package dev.zsithious.socialcues.adapter.bucketd.render;

import java.util.Optional;
import java.util.UUID;

import dev.zsithious.socialcues.core.client.BillboardCueVisibility;
import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.CueDisplaySelector;
import dev.zsithious.socialcues.core.client.CueIconAtlas;
import dev.zsithious.socialcues.core.client.DistanceFade;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.mcshared.client.LocalCueState;
import dev.zsithious.socialcues.mcshared.client.RemoteCueStoreHolder;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * DESIGN.md §7 Katman 1 — draws the billboard icon above a player's head.
 *
 * <p><b>Hook:</b> a vanilla {@code FeatureRenderer}, registered through
 * Fabric API's {@code LivingEntityFeatureRendererRegistrationCallback} (see
 * {@link BucketDFeatureRendererBootstrap}) rather than a mixin — DESIGN.md
 * §7's stated preference when a Fabric API registration point exists.
 * {@code javap -c}-verified (1.21.11, fabric-rendering-v1 16.2.10): the
 * callback fires once per {@code PlayerEntityRenderer} construction — twice
 * total, once per skin variant ("default"/"slim") — via a dedicated
 * {@code createPlayerEntityRenderer} wrap-operation that explicitly passes
 * {@code EntityType.PLAYER}, not only through the generic
 * {@code EntityRenderers.register(EntityType, factory)} path other living
 * entities use; Fabric API's own class Javadoc example (registering a
 * feature renderer "for a player model") confirms this is supported, not an
 * implementation accident.
 *
 * <p><b>No mixin needed to find the player's id</b> (unlike vanilla feature
 * renderers, which are constructed alongside — and know the concrete type of
 * — their owning {@code LivingEntityRenderer}): see {@link CueUuidHolder} and
 * {@code adapter.bucketd.mixin.PlayerEntityRendererMixin} for why one still
 * is needed to get a {@code UUID} out of a render state at all.
 *
 * <p><b>Name-tag rule reuse (DESIGN.md §7: "vanilla isim etiketini çizecek
 * miydi diye soran predicate'e bağlan, kendi kopyanı çıkarma"):</b>
 * {@code javap -c}-verified on {@code EntityRenderer#updateRenderState} —
 * {@code EntityRenderState.nameLabelPos}/{@code displayName} are computed
 * there from exactly {@code Entity.shouldRenderName()} (which itself defers
 * to team visibility rules), {@code hasCustomName()}/targeted-entity, and a
 * {@code squaredDistanceToCamera < 4096} (64 block) cutoff — the same
 * F1-independent set of rules a real name tag uses. Both fields are only
 * ever non-null together when vanilla would show a label; this renderer
 * gates on exactly that (see {@link #render}) instead of re-deriving
 * distance/team/targeted-entity logic itself. One rule vanilla's own
 * {@code hasLabel} does <em>not</em> apply — {@code F1}/{@code hudHidden} —
 * so this renderer checks {@link net.minecraft.client.option.GameOptions#hudHidden}
 * itself, independently: our billboard is new HUD-like chrome, not a literal
 * vanilla name tag, and DESIGN.md §7 explicitly asks for the F1 rule too. See
 * DESIGN.md §7's "P4b uygulama notu" for the full reasoning and what to
 * verify in-game.
 *
 * <p><b>Drawing (DESIGN.md §7 / §11: vanilla buffer only, no custom GL
 * state):</b> 1.21.9+ replaced the {@code VertexConsumerProvider} feature
 * renderers used to receive with {@link OrderedRenderCommandQueue} — there is
 * no longer a buffer handed directly to {@code render}. The still-vanilla,
 * still-Sodium/Iris-safe equivalent is {@link OrderedRenderCommandQueue#submitCustom}:
 * it hands back a plain {@link VertexConsumer} for a standard
 * {@link net.minecraft.client.render.RenderLayer}, into which this class
 * emits one ordinary textured quad — no shader, no raw GL call, exactly the
 * same primitive vanilla's own model/armor feature renderers use.
 */
public final class CueBillboardFeatureRenderer extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {

    private static final Identifier CUES_TEXTURE = Identifier.of("socialcues", CueIconAtlas.TEXTURE_PATH);

    /** World-size (blocks) of the icon at {@code scale == 1.0} — an independent design choice, DESIGN.md doesn't pin one down. */
    private static final float BASE_ICON_SIZE = 0.3f;

    /**
     * How far above {@code nameLabelPos} the icon sits, in blocks. Anchoring
     * icon and name tag to the exact same point would draw them on top of
     * each other (both use {@code EntityRenderState.nameLabelPos}) — DESIGN.md
     * §7 offers "above the head" as one of two acceptable placements
     * ("başının üstünde / isim etiketinin yanında"); this renderer picks that
     * one specifically because it needs no per-frame text-width query.
     */
    private static final float LABEL_CLEARANCE = 0.28f;

    public CueBillboardFeatureRenderer(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context) {
        super(context);
    }

    @Override
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
            PlayerEntityRenderState state, float yaw, float headPitch) {
        UUID id = ((CueUuidHolder) (Object) state).socialcues$getUuid();
        if (id == null) {
            return; // Not yet captured this frame (or not our mixin's doing at all, e.g. a mannequin).
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return; // DESIGN.md §7: F1 hides this layer too — see class Javadoc for why it's checked here, not upstream.
        }
        ClientConfigData config = ClientConfigState.get();
        if (!config.layer1Enabled()) {
            return; // Cheapest possible bail before any lookup (DESIGN.md §3.5: a disabled layer costs nothing).
        }

        boolean isSelf = client.player != null && id.equals(client.player.getUuid());
        // DESIGN.md §7 P4b uygulama notu: RemoteCueStore never has the local player's own
        // id (the relay never echoes a viewer's own cue back), so the self case is sourced
        // from LocalCueState instead — see that class's Javadoc.
        Optional<PlayerCue> cueOpt = isSelf ? LocalCueState.get() : RemoteCueStoreHolder.get().cueOf(id);
        if (cueOpt.isEmpty()) {
            return;
        }
        PlayerCue cue = cueOpt.get();

        if (state.displayName == null || state.nameLabelPos == null) {
            return; // Vanilla itself would not show a name label here right now; neither do we.
        }

        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();
        double distance = Math.sqrt(state.squaredDistanceToCamera);
        String playerName = resolvePlayerName(client, id);

        if (!BillboardCueVisibility.shouldRender(cue, isSelf, thirdPerson, distance, config, playerName)) {
            return;
        }

        double alpha = DistanceFade.combinedAlpha(distance, config.maxDistance(), config.opacity());
        if (alpha <= 0.0) {
            return;
        }

        int cell = CueDisplaySelector.atlasCellFor(cue);
        drawBillboard(matrices, queue, light, state.nameLabelPos, cell, (float) config.scale(), (float) alpha);
    }

    private static String resolvePlayerName(MinecraftClient client, UUID id) {
        if (client.getNetworkHandler() == null) {
            return "";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
        if (entry == null || entry.getProfile() == null) {
            return ""; // Not (yet) in the tab list; ClientConfigData.isMuted("") is simply never true.
        }
        return entry.getProfile().name(); // javap -verified: GameProfile.name(), not getName() (see PlayerListHudMixin).
    }

    private static void drawBillboard(MatrixStack matrices, OrderedRenderCommandQueue queue, int light,
            Vec3d anchor, int cell, float scale, float alpha) {
        matrices.push();
        matrices.translate(anchor.x, anchor.y + LABEL_CLEARANCE, anchor.z);
        // Same billboard idiom vanilla's own particle/label rendering uses: rotating by
        // the camera's own orientation makes the local XY plane always face the viewer.
        Camera camera = MinecraftClient.getInstance().gameRenderer.getCamera();
        matrices.multiply(camera.getRotation());

        float half = (BASE_ICON_SIZE * scale) / 2.0f;
        float minU = CueIconAtlas.minU(cell);
        float maxU = CueIconAtlas.maxU(cell);
        float minV = CueIconAtlas.minV(cell);
        float maxV = CueIconAtlas.maxV(cell);
        int alphaByte = Math.round(clamp01(alpha) * 255.0f);

        queue.submitCustom(matrices, RenderLayers.entityTranslucent(CUES_TEXTURE), (entry, vertices) ->
                emitQuad(entry, vertices, half, minU, maxU, minV, maxV, light, alphaByte));
        matrices.pop();
    }

    /**
     * Counter-clockwise as seen from {@code +Z} (i.e. facing the camera,
     * after {@link #drawBillboard}'s rotation) — matches Minecraft's
     * front-face winding convention, so the quad stays visible under normal
     * backface culling instead of needing a "no cull" render layer.
     */
    private static void emitQuad(MatrixStack.Entry entry, VertexConsumer vertices, float half,
            float minU, float maxU, float minV, float maxV, int light, int alpha) {
        vertex(entry, vertices, -half, half, minU, minV, light, alpha); // top-left
        vertex(entry, vertices, -half, -half, minU, maxV, light, alpha); // bottom-left
        vertex(entry, vertices, half, -half, maxU, maxV, light, alpha); // bottom-right
        vertex(entry, vertices, half, half, maxU, minV, light, alpha); // top-right
    }

    private static void vertex(MatrixStack.Entry entry, VertexConsumer vertices, float x, float y,
            float u, float v, int light, int alpha) {
        vertices.vertex(entry.getPositionMatrix(), x, y, 0f)
                .color(255, 255, 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, 0f, 0f, 1f);
    }

    private static float clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, value));
    }
}
