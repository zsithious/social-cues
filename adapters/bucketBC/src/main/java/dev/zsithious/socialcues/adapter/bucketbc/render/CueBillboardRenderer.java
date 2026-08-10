package dev.zsithious.socialcues.adapter.bucketbc.render;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.adapter.compat.CueRenderLayers;
import dev.zsithious.socialcues.core.client.BillboardCueVisibility;
import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.CueDisplaySelector;
import dev.zsithious.socialcues.core.client.CueIconAtlas;
import dev.zsithious.socialcues.core.client.CueIconMotion;
import dev.zsithious.socialcues.core.client.DistanceFade;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.mcshared.client.LocalCueState;
import dev.zsithious.socialcues.mcshared.client.RemoteCueStoreHolder;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

import org.joml.Quaternionf;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * DESIGN.md §7 Katman 1 — draws the billboard icon above a player's name tag.
 *
 * <p><b>Hook (P4b düzeltmesi — see DESIGN.md §7's "Katman 1 hangi uzayda
 * çizilir" note):</b> this is called from the tail of
 * {@code PlayerEntityRenderer#renderLabelIfPresent}
 * ({@code adapter.bucketbc.mixin.PlayerEntityRendererMixin}), <em>not</em> from
 * a {@code FeatureRenderer}. P4b's first attempt used a feature renderer
 * registered through Fabric API — the hook DESIGN.md §7 prefers — but a
 * feature renderer is handed the <em>model</em> matrix stack, and
 * {@code javap -c} on 1.21.11's {@code LivingEntityRenderer#render} shows what
 * that means: {@code setupTransforms(...)} (body yaw, plus the death/sleeping
 * rotations), then {@code scale(-1, -1, 1)}, then the renderer's own
 * {@code scale(...)} (0.9375 for players), then {@code translate(0, -1.501,
 * 0)}. In that space Y points <em>down</em>, X is mirrored, everything is
 * yaw-rotated, and {@code EntityRenderState.nameLabelPos} — which is an
 * entity-local offset meant for the unflipped stack — lands roughly a block
 * <em>below the player's feet</em>. That is exactly what the P4 hand test on
 * the live server showed: an upside-down monitor icon the size of the screen.
 * None of it is undoable from inside a feature renderer without re-deriving
 * pose-dependent vanilla internals.
 *
 * <p>{@code renderLabelIfPresent} is the one hook that already runs in the
 * space {@code nameLabelPos} is expressed in — {@code javap -c}-verified:
 * {@code LivingEntityRenderer#render} pops all of the above before calling
 * {@code super.render}, which calls {@code renderLabelIfPresent} with the
 * clean, world-axis-aligned, camera-translated stack; and
 * {@code PlayerEntityRenderer}'s own override is balanced
 * ({@code push()} … {@code pop()}, re-verified on the 1.21.8 jar), so
 * {@code @At("TAIL")} sees that same clean stack.
 *
 * <p><b>Bucket BC's camera:</b> 1.21.9's {@code CameraRenderState} — which
 * bucket D receives as a hook parameter — does not exist in this range, so the
 * billboard orientation is fetched instead of received. Vanilla's own label
 * rendering in this range does exactly that ({@code javap -c},
 * {@code EntityRenderer#renderLabelIfPresent}: {@code
 * matrices.multiply(this.dispatcher.getRotation())} between the translate and
 * the {@code scale(0.025f, -0.025f, 0.025f)}), so this class asks the same
 * {@code EntityRenderDispatcher} for the same quaternion rather than
 * inventing a camera query of its own.
 *
 * <p><b>Name-tag rule reuse (DESIGN.md §7: "vanilla isim etiketini çizecek
 * miydi diye soran predicate'e bağlan, kendi kopyanı çıkarma"):</b> in this
 * range {@code EntityRenderer#render} only calls {@code renderLabelIfPresent}
 * when {@code displayName != null} ({@code javap -c}-verified; bucket D calls
 * it unconditionally), but this class keeps its own {@code displayName}/{@code
 * nameLabelPos} null checks anyway — they are simply redundant here rather
 * than load-bearing, and keeping them means the two buckets' visibility rules
 * cannot drift apart. Those two fields are set together — {@code javap
 * -c}-verified on {@code EntityRenderer#updateRenderState} — from exactly
 * {@code Entity.shouldRenderName()} (team visibility included),
 * {@code hasCustomName()}/targeted-entity, and a
 * {@code squaredDistanceToCamera < 4096} (64 block) cutoff. One rule vanilla's
 * {@code hasLabel} does <em>not</em> apply — {@code F1}/{@code hudHidden} — is
 * checked here separately: the billboard is new HUD-like chrome, not a literal
 * vanilla name tag, and DESIGN.md §7 asks for the F1 rule too.
 *
 * <p><b>Drawing (DESIGN.md §7 / §11: vanilla buffer only, no custom GL
 * state):</b> this is the {@link VertexConsumerProvider} generation, so the
 * buffer is taken directly — {@link VertexConsumerProvider#getBuffer} for a
 * standard {@link net.minecraft.client.render.RenderLayer}, into which this
 * class emits one ordinary textured quad — no shader, no raw GL call. Bucket
 * D reaches the same {@link VertexConsumer} through 1.21.9+'s {@code
 * OrderedRenderCommandQueue#submitCustom} callback; everything downstream of
 * obtaining it, {@link #emitQuad} and {@link #vertex} included, is identical
 * in both buckets ({@code javap}-verified: {@code VertexConsumer}'s {@code
 * vertex}/{@code color}/{@code texture}/{@code overlay}/{@code light}/{@code
 * normal} surface is unchanged across the whole range).
 *
 * <p><b>P5b task 3 — {@link CueIconMotion}:</b> the icon's own small idle bob
 * (added to {@code rise}, so it moves along the same vertical axis the icon
 * is already placed on) and tilt (a roll about local {@code +Z} — the view
 * axis once the camera orientation has been multiplied in, see {@link
 * #drawBillboard}) are applied unconditionally here, never gated on {@code
 * layer3Enabled}: this is Layer 1's own motion and DESIGN.md requires it to
 * keep working with the pose layer switched off (see {@link CueIconMotion}'s
 * own class Javadoc). {@link CueScreenPanelRenderer} — the P5b held panel,
 * called from the same mixin hook right after this class — is unrelated: it
 * genuinely is Layer 3 content, only hosted here for the matrix-space reasons
 * this class's own Javadoc explains, and so it does gate on {@code
 * layer3Enabled}.
 */
public final class CueBillboardRenderer {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    private static final Identifier CUES_TEXTURE = Identifier.of("socialcues", CueIconAtlas.TEXTURE_PATH);

    /** World-size (blocks) of the icon at {@code scale == 1.0} — an independent design choice, DESIGN.md doesn't pin one down. */
    private static final float BASE_ICON_SIZE = 0.3f;

    /**
     * P6 §4.2 {@code textOnly} mode's text size at {@code scale == 1.0} —
     * vanilla's own nameplate text scale, reused rather than invented ({@code
     * javap -c}, {@code LabelCommandRenderer$Commands#add}: {@code
     * matrices.scale(0.025f, -0.025f, 0.025f)}, applied right after the same
     * camera-orientation multiply {@link #drawBillboard} already does — see
     * {@link #drawBillboardText}). Multiplied by {@code config.scale()} the
     * same way {@link #BASE_ICON_SIZE} already is, so the icon-vs-text
     * footprint stays comparable at any scale setting.
     */
    private static final float BASE_TEXT_SCALE = 0.025f;

    /**
     * The rise vanilla itself adds on top of {@code nameLabelPos} before
     * drawing a label ({@code javap -c}: {@code LabelCommandRenderer.Commands
     * #add} translates by {@code pos.y + 0.5}). Mirrored here so the icon is
     * placed relative to where the name tag actually ends up, not to the raw
     * attachment point.
     */
    private static final float VANILLA_LABEL_RISE = 0.5f;

    /**
     * Vertical step {@code PlayerEntityRenderer#renderLabelIfPresent} puts
     * between its two labels ({@code javap -c}: {@code 9.0f * 1.15f * 0.025f})
     * — applied only when {@code playerName} is present, i.e. when the display
     * name is the <em>upper</em> of two lines rather than the only one.
     */
    private static final float SECOND_LABEL_STEP = 9.0f * 1.15f * 0.025f;

    /** Half the world-height of one vanilla label line: 9px of text at the label's own {@code 0.025} scale. */
    private static final float LABEL_HALF_HEIGHT = 9.0f * 0.025f / 2.0f;

    /**
     * Gap in blocks between the top of the highest name label and the bottom
     * of the icon. DESIGN.md §7 offers "başının üstünde / isim etiketinin
     * yanında"; stacking above the label is the placement that needs no
     * per-frame text-width query.
     */
    private static final float LABEL_CLEARANCE = 0.1f;

    /**
     * Set once when {@link #renderGuarded} catches a throwable. Same stance as
     * {@code ClientCueCapture.captureDisabledByError}: one bad frame must not
     * become a per-frame log flood or a crash, and the failure is a property of
     * this build, so it is never cleared (DESIGN.md §11).
     */
    private static boolean disabledByError;

    private CueBillboardRenderer() {
    }

    /**
     * Backstop around {@link #render}: a rendering bug here must degrade to
     * "no billboards this session", never to a crashed client. Unlike P4b's
     * original swallow-and-log-at-FINE, the one line this does emit is
     * {@code SEVERE}, so a hand test surfaces the bug instead of silently
     * showing nothing.
     */
    public static void renderGuarded(PlayerEntityRenderState state, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light) {
        if (disabledByError) {
            return;
        }
        try {
            render(state, matrices, vertexConsumers, light);
        } catch (Throwable t) {
            disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 1 (billboard) rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }

    private static void render(PlayerEntityRenderState state, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light) {
        if (state.displayName == null || state.nameLabelPos == null) {
            return; // Vanilla itself would not show a name label here right now; neither do we.
        }

        UUID id = ((CueUuidHolder) (Object) state).socialcues$getUuid();
        if (id == null) {
            return; // Not captured for this state (e.g. a mannequin our updateRenderState hook never saw).
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return; // DESIGN.md §7: F1 hides this layer too — see class Javadoc for why it is checked here.
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

        float half = (BASE_ICON_SIZE * (float) config.scale()) / 2.0f;
        // P5b task 3: CueIconMotion is Layer 1's own idle motion, deliberately
        // independent of layer3Enabled/PoseAnimator (see that class's Javadoc) --
        // it is only ever zero for every cue but AFK, so this costs nothing for
        // the common case and needs no extra gate here. Shared by both the icon
        // and the P6 §4.2 textOnly label below -- the "chrome" (anchor, rise,
        // tilt, fade) is identical either way, only the drawn content differs.
        // P6 §4.1: reducedMotion is read from config here (the viewer's own
        // setting, mcshared.config.ClientConfigState) and passed in, never read
        // inside core -- see CueIconMotion's own Javadoc.
        float bob = CueIconMotion.bobBlocks(cue, state.age, config.reducedMotion());
        float tilt = CueIconMotion.tiltRadians(cue, state.age, config.reducedMotion());
        float rise = VANILLA_LABEL_RISE
                + (state.playerName != null ? SECOND_LABEL_STEP : 0.0f)
                + LABEL_HALF_HEIGHT + LABEL_CLEARANCE + half
                + bob;

        // P6 §4.2: textOnly replaces the atlas quad with a translated label at
        // the exact same anchor/rise/tilt/fade/scale -- see drawBillboardText's
        // own Javadoc for the drawing itself. Activity.NORMAL's lang key is the
        // empty string (DESIGN.md §4), but passesSharedRules already rejected
        // NORMAL cues above, so CueDisplaySelector.langKeyFor(cue) here is never
        // asked to resolve that case.
        if (config.textOnly()) {
            drawBillboardText(matrices, vertexConsumers, state.nameLabelPos, rise, CueDisplaySelector.langKeyFor(cue),
                    light, (float) alpha, tilt, (float) config.scale());
        } else {
            int cell = CueDisplaySelector.atlasCellFor(cue);
            drawBillboard(matrices, vertexConsumers, state.nameLabelPos, rise, cell, half, light, (float) alpha, tilt);
        }
    }

    private static String resolvePlayerName(MinecraftClient client, UUID id) {
        if (client.getNetworkHandler() == null) {
            return "";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
        if (entry == null || entry.getProfile() == null) {
            return ""; // Not (yet) in the tab list; ClientConfigData.isMuted("") is simply never true.
        }
        // getName(), not name(): this bucket's rows ship authlib 6.x — see
        // adapter.bucketbc.mixin.PlayerListHudMixin for the measurement.
        return entry.getProfile().getName();
    }

    /**
     * The camera orientation to billboard against. Bucket D is handed a {@code
     * CameraRenderState} by the hook; in this range vanilla's own label
     * rendering reads it off the {@code EntityRenderDispatcher} instead
     * ({@code javap -c}, {@code EntityRenderer#renderLabelIfPresent}), and this
     * is the same dispatcher instance — {@code MinecraftClient} holds exactly
     * one and hands it to every renderer it constructs — so the quaternion is
     * the one vanilla is billboarding its own name tags with this frame, not a
     * separately-derived approximation of it.
     */
    private static Quaternionf cameraOrientation() {
        return MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation();
    }

    private static void drawBillboard(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            Vec3d anchor, float rise, int cell, float half, int light, float alpha,
            float tilt) {
        matrices.push();
        matrices.translate(anchor.x, anchor.y + rise, anchor.z);
        // Exactly the billboard idiom vanilla's own label rendering uses (javap -c,
        // EntityRenderer#renderLabelIfPresent): multiplying in the camera's orientation
        // leaves local +X pointing screen-right and local +Y screen-up — which is why
        // vanilla then has to flip *only* Y (scale(0.025f, -0.025f, 0.025f)) to draw
        // its y-down text. Our quad geometry is already y-up, so it needs no flip.
        matrices.multiply(cameraOrientation());
        if (tilt != 0f) {
            // CueIconMotion.tiltRadians: "roll about the view axis" -- after the
            // camera-orientation multiply above, local +Z already points straight at
            // the camera (the view axis), so rolling about local Z is exactly that.
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(tilt));
        }

        float minU = CueIconAtlas.minU(cell);
        float maxU = CueIconAtlas.maxU(cell);
        float minV = CueIconAtlas.minV(cell);
        float maxV = CueIconAtlas.maxV(cell);
        int alphaByte = Math.round(clamp01(alpha) * 255.0f);

        // Bucket D's OrderedRenderCommandQueue#submitCustom hands its callback the
        // MatrixStack.Entry and VertexConsumer; here both are simply taken directly.
        VertexConsumer vertices = vertexConsumers.getBuffer(CueRenderLayers.entityTranslucent(CUES_TEXTURE));
        emitQuad(matrices.peek(), vertices, half, minU, maxU, minV, maxV, light, alphaByte);
        matrices.pop();
    }

    /**
     * P6 §4.2 {@code textOnly} — the translated-label counterpart of {@link
     * #drawBillboard}. The first three statements below are byte-for-byte the
     * same billboard transform {@link #drawBillboard} uses (translate to
     * {@code anchor + rise}, multiply in the camera orientation, optionally
     * roll about the now-view-aligned local {@code +Z} for {@link
     * CueIconMotion#tiltRadians}) — same anchor, same rise, same tilt, on
     * purpose, so the label sits exactly where the icon would have.
     *
     * <p>Where it diverges: {@link #drawBillboard}'s quad geometry is already
     * y-up, so (per that method's own comment) it draws with no further flip.
     * Text cannot skip that flip — font glyphs are authored in a y-down pixel
     * space — so this method applies vanilla's own remedy for the same
     * problem: {@link #BASE_TEXT_SCALE} is vanilla's own {@code
     * scale(0.025f, -0.025f, 0.025f)} ({@code javap -c}-verified, see that
     * constant's Javadoc), scaled further by {@code configScale} the same way
     * {@link #BASE_ICON_SIZE} already is.
     *
     * <p>Drawing itself follows vanilla's nameplate text path, not an invented
     * one. In this range vanilla draws its own name label with {@code
     * TextRenderer#draw(Text, float, float, int, boolean, Matrix4f,
     * VertexConsumerProvider, TextLayerType, int, int)} ({@code javap
     * -c}-verified, {@code EntityRenderer#renderLabelIfPresent}) after doing
     * its own translate/orient/{@code scale(0.025f, -0.025f, 0.025f)} — and
     * that scale is hardcoded, with no hook for {@code config.scale()}, which
     * is exactly the one thing this method must be able to override. So the
     * camera-billboard transform is reproduced by hand instead (identical to
     * {@link #drawBillboard}'s own, see above) and the text is drawn into the
     * already-positioned result. Horizontal centering ({@code -textWidth / 2f})
     * mirrors vanilla's own trick for the identical purpose. Bucket D reaches
     * the same place through {@code OrderedRenderCommandQueue#submitText}; the
     * arguments correspond one-for-one, only re-ordered by the newer API.
     */
    private static void drawBillboardText(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            Vec3d anchor, float rise, String langKey, int light, float alpha,
            float tilt, float configScale) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        if (textRenderer == null) {
            return; // Not yet available this early in startup; drawBillboard's quad path has no equivalent dependency.
        }
        Text label = Text.translatable(langKey);
        float textWidth = textRenderer.getWidth(label);

        matrices.push();
        matrices.translate(anchor.x, anchor.y + rise, anchor.z);
        matrices.multiply(cameraOrientation());
        if (tilt != 0f) {
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(tilt));
        }
        float textScale = BASE_TEXT_SCALE * configScale;
        matrices.scale(textScale, -textScale, textScale);

        // Opaque white, alpha-only fade -- the same white drawBillboard's own
        // vertex() tints its quad with (255, 255, 255, alphaByte), so an icon and
        // its text-mode replacement read as the same "brightness" at any distance.
        int alphaByte = Math.round(clamp01(alpha) * 255.0f);
        int color = (alphaByte << 24) | 0xFFFFFF;

        // DESIGN.md §7 P5b precedent (CueScreenPanelRenderer.drawChatText's own
        // note): whether the colour's alpha byte is actually honoured all the way
        // into the vertex consumer was not javap-traced to the end in bucket D
        // either, and is not here -- a reasonable assumption given vanilla's own
        // chat/actionbar fade relies on the same path, not a proven one.
        //
        // Background colour 0 (fully transparent) and shadow=false match bucket
        // D's submitText arguments exactly; the trailing int is the light value,
        // which the newer API takes earlier in its parameter list.
        textRenderer.draw(label.asOrderedText(), -textWidth / 2f, 0f, color, false,
                matrices.peek().getPositionMatrix(), vertexConsumers,
                TextRenderer.TextLayerType.NORMAL, 0, light);
        matrices.pop();
    }

    /**
     * Counter-clockwise as seen from {@code +Z} (i.e. from the camera, after
     * {@link #drawBillboard}'s orientation multiply) — matches Minecraft's
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
