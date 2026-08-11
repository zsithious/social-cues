package dev.zsithious.socialcues.adapter.bucketa.render;

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
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityAttachmentType;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * DESIGN.md §7 Katman 1 — draws the billboard icon above a player's name tag,
 * the 1.21–1.21.1 spelling.
 *
 * <p><b>Why this class exists at all instead of reusing bucket BC's.</b>
 * {@code net.minecraft.client.render.entity.state} — the whole package — was
 * introduced in 1.21.2, so on these two rows there is no {@code
 * PlayerEntityRenderState} and no {@code EntityRenderState}: the renderer is
 * handed the live {@code Entity} instead (DESIGN.md §7's "Kova A için ölçülmüş
 * render farkları" table). Every value bucket BC reads off a pre-computed
 * render state has to be derived here from the entity, and each derivation
 * below is the one vanilla itself performs on these rows, read out of the
 * mapped jar with {@code javap -c} — never an invented equivalent:
 *
 * <table border="1">
 *   <caption>Bucket BC's render-state field → bucket A's source</caption>
 *   <tr><th>BC</th><th>A</th><th>Where the equivalence was measured</th></tr>
 *   <tr><td>{@code state.nameLabelPos}</td>
 *       <td>{@code entity.getAttachments().getPointNullable(NAME_TAG, 0,
 *           entity.getYaw(tickDelta))}</td>
 *       <td>1.21 {@code EntityRenderer#renderLabelIfPresent} computes exactly
 *           this, and 1.21.8's {@code EntityRenderer#updateRenderState} fills
 *           {@code nameLabelPos} from the same call — the field is a cache of
 *           this expression, not a different quantity.</td></tr>
 *   <tr><td>{@code state.squaredDistanceToCamera}</td>
 *       <td>{@code dispatcher.getSquaredDistanceToCamera(entity)}</td>
 *       <td>Same call, same dispatcher; the field is again just the cached
 *           result.</td></tr>
 *   <tr><td>{@code state.age}</td>
 *       <td>{@code entity.age + tickDelta}</td>
 *       <td>{@code EntityRenderer#updateRenderState}: {@code state.age =
 *           (float) entity.age + tickDelta}. Identical to what {@code
 *           LivingEntityRenderer#getAnimationProgress} returns on this row.</td></tr>
 *   <tr><td>{@code state.displayName != null}</td>
 *       <td>(no check needed)</td>
 *       <td>{@code EntityRenderer#render} calls {@code renderLabelIfPresent}
 *           only inside {@code if (this.hasLabel(entity))} — the hook cannot
 *           run for a player vanilla is not labelling. BC's null check is the
 *           same rule expressed as a field.</td></tr>
 *   <tr><td>{@code state.playerName != null}</td>
 *       <td>{@link #hasScoreLabel}</td>
 *       <td>Despite the yarn name, that field is the BELOW_NAME scoreboard
 *           line, and 1.21.8 sets it under exactly {@code
 *           squaredDistanceToCamera < 100 && scoreboard.getObjectiveForSlot(
 *           BELOW_NAME) != null} — the condition 1.21's {@code
 *           PlayerEntityRenderer#renderLabelIfPresent} evaluates inline before
 *           applying the same {@code 9 * 1.15 * 0.025} step.</td></tr>
 * </table>
 *
 * <p>Everything else in this class is bucket BC's, unchanged, and the reasoning
 * behind it is not repeated here — {@code adapter.bucketbc.render
 * .CueBillboardRenderer}'s Javadoc is the canonical account of why {@code
 * renderLabelIfPresent}'s tail is the correct hook and a {@code FeatureRenderer}
 * is not (P4's upside-down, ground-level icon), why the camera orientation is
 * fetched from the {@code EntityRenderDispatcher} rather than received, why the
 * quad needs no Y flip and the text does, and why {@link CueIconMotion} is never
 * gated on {@code layer3Enabled}. Those all hold verbatim on these two rows:
 * the drawing surface ({@link VertexConsumerProvider}, {@link VertexConsumer},
 * {@link TextRenderer#draw}) is byte-for-byte the same API here as on
 * 1.21.2–1.21.8, {@code javap}-verified.
 *
 * <p><b>Guard order differs from bucket BC's on purpose.</b> There, every input
 * was a field already computed for this frame, so the order was free and the
 * class read top-down as "vanilla's rules, then ours". Here two of those inputs
 * cost real work — {@code getPointNullable} allocates a {@link Vec3d} and
 * interpolates the entity's yaw — so the free checks ({@code hudHidden},
 * {@code layer1Enabled}, is there even a cue for this player) run first and the
 * anchor is computed last, once something is actually going to be drawn. The
 * set of rules is identical; only their order is not.
 */
public final class CueBillboardRenderer {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    private static final Identifier CUES_TEXTURE = Identifier.of("socialcues", CueIconAtlas.TEXTURE_PATH);

    /** World-size (blocks) of the icon at {@code scale == 1.0} — same constant as bucket BC's; DESIGN.md doesn't pin one down. */
    private static final float BASE_ICON_SIZE = 0.3f;

    /** P6 §4.2 {@code textOnly} text size at {@code scale == 1.0} — vanilla's own nameplate scale, see bucket BC's Javadoc. */
    private static final float BASE_TEXT_SCALE = 0.025f;

    /**
     * The rise vanilla adds on top of the name-tag attachment point before
     * drawing a label ({@code javap -c}, 1.21 {@code
     * EntityRenderer#renderLabelIfPresent}: {@code translate(pos.x, pos.y +
     * 0.5, pos.z)}). Same number, same place in the expression, as bucket BC.
     */
    private static final float VANILLA_LABEL_RISE = 0.5f;

    /**
     * Vertical step {@code PlayerEntityRenderer#renderLabelIfPresent} puts
     * between its two labels ({@code javap -c}: {@code 9.0f * 1.15f * 0.025f}),
     * applied only when the BELOW_NAME scoreboard line is present — see
     * {@link #hasScoreLabel}.
     */
    private static final float SECOND_LABEL_STEP = 9.0f * 1.15f * 0.025f;

    /** Half the world-height of one vanilla label line: 9px of text at the label's own {@code 0.025} scale. */
    private static final float LABEL_HALF_HEIGHT = 9.0f * 0.025f / 2.0f;

    /** Gap in blocks between the top of the highest name label and the bottom of the icon. */
    private static final float LABEL_CLEARANCE = 0.1f;

    /**
     * Vanilla's own cutoff for drawing a name label at all, in squared blocks
     * ({@code javap -c}, 1.21 {@code EntityRenderer#renderLabelIfPresent}:
     * {@code if (d > 4096.0) return;}, i.e. 64 blocks).
     *
     * <p>Kept as belt-and-braces, exactly as vanilla keeps it, and not because
     * it is load-bearing: {@code LivingEntityRenderer#hasLabel} — which gates
     * the whole call — already returns false at {@code d >= 64²} (or {@code
     * 32²} while sneaking), so on the normal path this check can no more fire
     * here than it can inside vanilla's own method. The reason to state the
     * rule anyway is DESIGN.md §7's "bind to the predicate that decides
     * whether vanilla would draw a name tag, rather than inventing a copy of
     * it": this hook is on {@code PlayerEntityRenderer}'s override, one frame
     * further out than the method that performs the check, so a reader (or a
     * future mod that calls the override directly) should be able to see the
     * bound rather than have to re-derive it from a superclass.
     */
    private static final double MAX_LABEL_DISTANCE_SQUARED = 4096.0;

    /**
     * Vanilla's cutoff for the BELOW_NAME scoreboard line ({@code javap -c},
     * 1.21 {@code PlayerEntityRenderer#renderLabelIfPresent}: {@code if (d <
     * 100.0)}, i.e. 10 blocks) — see {@link #hasScoreLabel}.
     */
    private static final double SCORE_LABEL_DISTANCE_SQUARED = 100.0;

    /** Set once when {@link #renderGuarded} catches a throwable; never cleared (DESIGN.md §11). */
    private static boolean disabledByError;

    private CueBillboardRenderer() {
    }

    /**
     * Backstop around {@link #render}, same stance as every other layer in this
     * codebase: one bad frame degrades to "no billboards this session", and the
     * single line it logs is {@code SEVERE} so a hand test surfaces the bug
     * instead of silently showing nothing.
     */
    public static void renderGuarded(AbstractClientPlayerEntity entity, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta) {
        if (disabledByError) {
            return;
        }
        try {
            render(entity, matrices, vertexConsumers, light, tickDelta);
        } catch (Throwable t) {
            disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 1 (billboard) rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }

    private static void render(AbstractClientPlayerEntity entity, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return; // DESIGN.md §7: F1 hides this layer too (vanilla's own hasLabel does not apply this rule).
        }
        ClientConfigData config = ClientConfigState.get();
        if (!config.layer1Enabled()) {
            return; // Cheapest possible bail before any lookup (DESIGN.md §3.5: a disabled layer costs nothing).
        }

        UUID id = entity.getUuid();
        boolean isSelf = client.player != null && id.equals(client.player.getUuid());
        // DESIGN.md §7 P4b uygulama notu: RemoteCueStore never has the local player's own
        // id (the relay never echoes a viewer's own cue back), so the self case is sourced
        // from LocalCueState instead — see that class's Javadoc.
        Optional<PlayerCue> cueOpt = isSelf ? LocalCueState.get() : RemoteCueStoreHolder.get().cueOf(id);
        if (cueOpt.isEmpty()) {
            return;
        }
        PlayerCue cue = cueOpt.get();

        double squaredDistance = client.getEntityRenderDispatcher().getSquaredDistanceToCamera(entity);
        if (squaredDistance > MAX_LABEL_DISTANCE_SQUARED) {
            return; // Vanilla itself would not show a name label this far out; neither do we.
        }

        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();
        double distance = Math.sqrt(squaredDistance);
        String playerName = resolvePlayerName(client, id);

        if (!BillboardCueVisibility.shouldRender(cue, isSelf, thirdPerson, distance, config, playerName)) {
            return;
        }

        double alpha = DistanceFade.combinedAlpha(distance, config.maxDistance(), config.opacity());
        if (alpha <= 0.0) {
            return;
        }

        // The last input, and the only one that costs an allocation — see the
        // class Javadoc's note on guard order. Null is a real answer ("this
        // entity has no name-tag attachment point right now"), the same one
        // bucket BC reads as a null nameLabelPos.
        Vec3d anchor = entity.getAttachments()
                .getPointNullable(EntityAttachmentType.NAME_TAG, 0, entity.getYaw(tickDelta));
        if (anchor == null) {
            return;
        }

        float age = entity.age + tickDelta;
        float half = (BASE_ICON_SIZE * (float) config.scale()) / 2.0f;
        // P5b task 3: CueIconMotion is Layer 1's own idle motion, deliberately
        // independent of layer3Enabled/PoseAnimator (see that class's Javadoc).
        // P6 §4.1: reducedMotion is read from config here (the viewer's own
        // setting) and passed in, never read inside core.
        float bob = CueIconMotion.bobBlocks(cue, age, config.reducedMotion());
        float tilt = CueIconMotion.tiltRadians(cue, age, config.reducedMotion());
        float rise = VANILLA_LABEL_RISE
                + (hasScoreLabel(entity, squaredDistance) ? SECOND_LABEL_STEP : 0.0f)
                + LABEL_HALF_HEIGHT + LABEL_CLEARANCE + half
                + bob;

        // P6 §4.2: textOnly replaces the atlas quad with a translated label at
        // the exact same anchor/rise/tilt/fade/scale.
        if (config.textOnly()) {
            drawBillboardText(matrices, vertexConsumers, anchor, rise, CueDisplaySelector.langKeyFor(cue),
                    light, (float) alpha, tilt, (float) config.scale());
        } else {
            int cell = CueDisplaySelector.atlasCellFor(cue);
            drawBillboard(matrices, vertexConsumers, anchor, rise, cell, half, light, (float) alpha, tilt);
        }
    }

    /**
     * Is vanilla drawing a <em>second</em> label — the BELOW_NAME scoreboard
     * line — under this player's name right now? If so the display name sits
     * one {@link #SECOND_LABEL_STEP} higher and the icon has to follow it.
     *
     * <p>This is the condition bucket BC gets for free as {@code
     * state.playerName != null}: measured on both sides ({@code javap -c}),
     * 1.21.8's {@code PlayerEntityRenderer#updateRenderState} fills that field
     * exactly when {@code squaredDistanceToCamera < 100} and the scoreboard has
     * a BELOW_NAME objective, and 1.21's {@code
     * PlayerEntityRenderer#renderLabelIfPresent} evaluates the same two
     * conditions inline before translating by {@code 9 * 1.15 * 0.025}. Note
     * that vanilla's own check is {@code < 100}, not {@code <= 100}, and that
     * this cutoff is a different (much nearer) one than {@link
     * #MAX_LABEL_DISTANCE_SQUARED}.
     *
     * <p>The score's own <em>value</em> is never read — only whether an
     * objective is displayed in that slot, which is the only part that changes
     * the geometry.
     */
    private static boolean hasScoreLabel(AbstractClientPlayerEntity entity, double squaredDistance) {
        if (squaredDistance >= SCORE_LABEL_DISTANCE_SQUARED) {
            return false;
        }
        return entity.getScoreboard().getObjectiveForSlot(ScoreboardDisplaySlot.BELOW_NAME) != null;
    }

    /**
     * The tab list, not {@code entity.getGameProfile()}, deliberately: this is
     * the name {@code ClientConfigData.isMuted} is matched against, and the
     * mute list has to mean the same thing on all twelve rows — so the lookup
     * is the same one {@code adapter.bucketbc}/{@code adapter.bucketd} perform,
     * not a shortcut this bucket happens to have available.
     */
    private static String resolvePlayerName(MinecraftClient client, UUID id) {
        if (client.getNetworkHandler() == null) {
            return "";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
        if (entry == null || entry.getProfile() == null) {
            return ""; // Not (yet) in the tab list; ClientConfigData.isMuted("") is simply never true.
        }
        // getName(), not name(): these rows ship authlib 6.x — see DESIGN.md §7's
        // P7 note for the measurement across all twelve rows.
        return entry.getProfile().getName();
    }

    /**
     * The camera orientation to billboard against — the same {@code
     * EntityRenderDispatcher#getRotation()} vanilla's own label rendering uses
     * on this row ({@code javap -c}, {@code
     * EntityRenderer#renderLabelIfPresent}), so the quaternion is the one
     * vanilla is billboarding its own name tags with this frame.
     */
    private static Quaternionf cameraOrientation() {
        return MinecraftClient.getInstance().getEntityRenderDispatcher().getRotation();
    }

    private static void drawBillboard(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
            Vec3d anchor, float rise, int cell, float half, int light, float alpha,
            float tilt) {
        matrices.push();
        matrices.translate(anchor.x, anchor.y + rise, anchor.z);
        // Exactly the billboard idiom vanilla's own label rendering uses on this row
        // (javap -c, EntityRenderer#renderLabelIfPresent): multiplying in the camera's
        // orientation leaves local +X pointing screen-right and local +Y screen-up —
        // which is why vanilla then has to flip *only* Y (scale(0.025f, -0.025f,
        // 0.025f)) to draw its y-down text. Our quad geometry is already y-up.
        matrices.multiply(cameraOrientation());
        if (tilt != 0f) {
            // CueIconMotion.tiltRadians: "roll about the view axis" -- after the
            // camera-orientation multiply above, local +Z already points straight at
            // the camera, so rolling about local Z is exactly that.
            matrices.multiply(RotationAxis.POSITIVE_Z.rotation(tilt));
        }

        float minU = CueIconAtlas.minU(cell);
        float maxU = CueIconAtlas.maxU(cell);
        float minV = CueIconAtlas.minV(cell);
        float maxV = CueIconAtlas.maxV(cell);
        int alphaByte = Math.round(clamp01(alpha) * 255.0f);

        VertexConsumer vertices = vertexConsumers.getBuffer(CueRenderLayers.entityTranslucent(CUES_TEXTURE));
        emitQuad(matrices.peek(), vertices, half, minU, maxU, minV, maxV, light, alphaByte);
        matrices.pop();
    }

    /**
     * P6 §4.2 {@code textOnly} — the translated-label counterpart of {@link
     * #drawBillboard}, identical to bucket BC's down to the argument order:
     * {@code TextRenderer#draw(OrderedText, float, float, int, boolean,
     * Matrix4f, VertexConsumerProvider, TextLayerType, int, int)} exists with
     * this exact signature on 1.21 as well ({@code javap}-verified), so the
     * only thing that changed is where the anchor came from. See bucket BC's
     * own Javadoc for why the billboard transform is reproduced by hand rather
     * than reusing vanilla's label path (its {@code 0.025} scale is hardcoded,
     * with no hook for {@code config.scale()}).
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
        // vertex() tints its quad with, so an icon and its text-mode replacement
        // read as the same "brightness" at any distance.
        int alphaByte = Math.round(clamp01(alpha) * 255.0f);
        int color = (alphaByte << 24) | 0xFFFFFF;

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
