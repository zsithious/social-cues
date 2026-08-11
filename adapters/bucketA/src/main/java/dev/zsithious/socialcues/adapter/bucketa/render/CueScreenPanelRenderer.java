package dev.zsithious.socialcues.adapter.bucketa.render;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.adapter.compat.CueRenderLayers;
import dev.zsithious.socialcues.core.client.BillboardCueVisibility;
import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.FakeChatStream;
import dev.zsithious.socialcues.core.client.PoseAnimator;
import dev.zsithious.socialcues.core.client.PoseBlend;
import dev.zsithious.socialcues.core.client.PoseFrame;
import dev.zsithious.socialcues.core.client.ScreenPanelTextures;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.mcshared.client.PoseBlendDriver;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * DESIGN.md §7 Katman 3, P5b — the held panel (a container GUI for {@code
 * IN_SCREEN}, a chat window for the four {@code TYPING_*} activities), the
 * 1.21–1.21.1 spelling.
 *
 * <p>{@code adapter.bucketbc.render.CueScreenPanelRenderer} is the canonical
 * account of everything this class does and why — the hook it shares with
 * Layer 1, why the panel is body-relative rather than camera-facing, the full
 * derivation of the yaw/forward/tilt transform, why {@code screenRise} is a
 * position rather than an intensity, why the chat text is drawn in the panel's
 * own plane, and why the visibility rules are {@link
 * BillboardCueVisibility#passesSharedRules} rather than {@code shouldRender}.
 * None of that reasoning changes here, and none of it is repeated.
 *
 * <p><b>What does change: the two inputs bucket BC reads off a render state.</b>
 * The {@code render.entity.state} package does not exist before 1.21.2 (see
 * {@code CueBillboardRenderer}'s Javadoc in this package for the full table),
 * so both are derived from the entity, each by the expression vanilla itself
 * uses on this row:
 *
 * <ul>
 *   <li>{@code state.age} → {@code entity.age + tickDelta}, which is both what
 *       {@code EntityRenderer#updateRenderState} stores in that field on 1.21.8
 *       and what {@code LivingEntityRenderer#getAnimationProgress} returns
 *       here.</li>
 *   <li>{@code state.bodyYaw} → {@code MathHelper.lerpAngleDegrees(tickDelta,
 *       entity.prevBodyYaw, entity.bodyYaw)}, which is the first thing 1.21's
 *       {@code LivingEntityRenderer#render} computes and hands to {@code
 *       setupTransforms} — i.e. the yaw the body model is actually drawn at,
 *       which is the whole point of orienting the panel to it.</li>
 * </ul>
 *
 * <p><b>One measured, deliberately accepted divergence.</b> After that lerp,
 * 1.21's {@code LivingEntityRenderer#render} applies a correction for a player
 * <em>riding a living entity</em> (re-deriving body yaw from the vehicle's and
 * clamping it to ±85° of the head yaw, then easing past 2500 squared), and
 * 1.21.2+ folds the same logic into the {@code clampBodyYaw} helper that fills
 * {@code state.bodyYaw}. This class does not reproduce it: it is twenty lines
 * of vanilla internals that only ever differ for a player who is typing while
 * mounted, and the visible consequence in that one case is a decorative panel
 * rotated with the player's own body instead of the horse's. Recorded here
 * rather than silently skipped, because the two buckets' output is otherwise
 * identical and a future reader deserves to know which line to add if that
 * case ever matters.
 */
public final class CueScreenPanelRenderer {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** Ticks per second — matches {@code PoseAnimator}'s own private constant; {@link FakeChatStream} takes seconds, not ticks. */
    private static final float TICKS_PER_SECOND = 20f;

    /** Social Cues' own flat-white fill (see {@code tools/gen_panel_fill.py}) — the chat panel's dark background is this, tinted. */
    private static final Identifier PANEL_FILL_TEXTURE = Identifier.of("socialcues", "textures/gui/panel_fill.png");

    // ------------------------------------------------------- placement
    // Every number below is bucket BC's, unchanged: they were tuned by eye
    // in-game during P5's hand tests and they describe the panel, not the
    // Minecraft version. See bucket BC's constants for the reasoning behind
    // each one.

    private static final float PANEL_FORWARD_OFFSET = 0.58f;
    private static final float CONTAINER_WIDTH_BLOCKS = 0.58f;
    private static final float NEUTRAL_PANEL_HEIGHT_BLOCKS = CONTAINER_WIDTH_BLOCKS;
    private static final float CHAT_WIDTH_BLOCKS = 0.62f;
    private static final float CHAT_HEIGHT_BLOCKS = 0.34f;
    private static final float CHAT_TEXT_MARGIN_BLOCKS = 0.045f;

    /** Nudges the text just off the background quad's own plane so the two never z-fight. */
    private static final float TEXT_FORWARD_EPSILON = 0.004f;

    private static final int CHAT_BG_R = 12;
    private static final int CHAT_BG_G = 12;
    private static final int CHAT_BG_B = 16;
    private static final float CHAT_BG_MAX_ALPHA = 0.65f;

    private static final int CHAT_TEXT_RGB = 0xE0E0E0;
    private static final int LINE_GAP_PX = 1;
    private static final float CARET_BLINK_HZ = 1.5f;
    private static final String CARET_GLYPH = "_";

    /** See the class Javadoc: one loud line, then quiet, never a per-frame log flood. */
    private static boolean disabledByError;

    private CueScreenPanelRenderer() {
    }

    /** Backstop around {@link #render}, same stance as {@code CueBillboardRenderer.renderGuarded}. */
    public static void renderGuarded(AbstractClientPlayerEntity entity, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta) {
        if (disabledByError) {
            return;
        }
        try {
            render(entity, matrices, vertexConsumers, light, tickDelta);
        } catch (Throwable t) {
            disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 3 held-panel rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }

    private static void render(AbstractClientPlayerEntity entity, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return; // DESIGN.md §7: F1 hides this layer too — see bucket BC's Javadoc for why it is checked here.
        }
        ClientConfigData config = ClientConfigState.get();
        if (!config.layer3Enabled()) {
            return; // This is Layer 3 content, only hosted at Layer 1's hook.
        }

        UUID id = entity.getUuid();
        Optional<PoseBlend.Blend> blendOpt = PoseBlendDriver.blendFor(id);
        if (blendOpt.isEmpty()) {
            return; // Nothing tracked for this player right now.
        }
        PoseBlend.Blend blend = blendOpt.get();

        // P6 §4.1: reducedMotion is the viewer's own setting, passed in rather
        // than read inside core -- see PoseAnimator's own Javadoc.
        float age = entity.age + tickDelta;
        PoseFrame frame = PoseAnimator.frameFor(blend.cue(), age, blend.weight(), config.reducedMotion());
        if (!frame.hasScreen()) {
            return; // AFK/NORMAL/SPEAKING never have a panel.
        }
        float alpha = clamp01(frame.screenWeight());
        if (alpha <= 0f) {
            return; // Weight rounded to nothing worth drawing.
        }

        boolean isSelf = client.player != null && id.equals(client.player.getUuid());
        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();
        double distance = Math.sqrt(client.getEntityRenderDispatcher().getSquaredDistanceToCamera(entity));
        String playerName = resolvePlayerName(client, id);

        // P6 §4.4: passesSharedRules, not shouldRender -- see bucket BC's Javadoc
        // for why (shouldRender would fold Layer 1's own layer1Enabled gate in here).
        if (!BillboardCueVisibility.passesSharedRules(blend.cue(), isSelf, thirdPerson, distance, config, playerName)) {
            return;
        }

        // See the class Javadoc for why this is the lerp and not a clamped one.
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
        drawPanel(matrices, vertexConsumers, light, blend.cue(), frame, alpha, age, bodyYaw);
    }

    private static String resolvePlayerName(MinecraftClient client, UUID id) {
        if (client.getNetworkHandler() == null) {
            return "";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
        if (entry == null || entry.getProfile() == null) {
            return "";
        }
        // getName(), not name(): these rows ship authlib 6.x — see DESIGN.md §7's P7 note.
        return entry.getProfile().getName();
    }

    /** See bucket BC's {@code drawPanel} Javadoc for the full derivation of this transform. */
    private static void drawPanel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            PlayerCue cue, PoseFrame frame, float alpha, float age, float bodyYaw) {
        matrices.push();
        matrices.translate(0f, frame.screenRise(), 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
        matrices.translate(0f, 0f, PANEL_FORWARD_OFFSET);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(frame.screenTilt()));

        switch (cue.activity()) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK -> drawChatPanel(matrices, vertexConsumers, light, cue, alpha, age);
            case IN_SCREEN -> drawContainerPanel(matrices, vertexConsumers, light, cue, alpha);
            // Unreachable: frame.hasScreen() already filtered out NORMAL/AFK/SPEAKING.
            // Kept exhaustive anyway, CueIconAtlas.cellFor's precedent: a future
            // Activity added to PoseAnimator's screen handling without a case here
            // fails to compile.
            case NORMAL, AFK, SPEAKING -> { }
        }

        matrices.pop();
    }

    /**
     * One quad per {@link ScreenPanelTextures.Band}, stacked vertically from the
     * panel's top edge down — see bucket BC's own Javadoc for why a container
     * texture can need more than one band (vanilla's real two-blit single-chest
     * composite) and why {@link ScreenPanelTextures#forScreenKind} returning
     * empty is a real answer rather than an error.
     *
     * <p>Bucket BC passes its render state down into this method and its
     * neighbours and never reads it; those parameters are simply dropped here.
     */
    private static void drawContainerPanel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            PlayerCue cue, float alpha) {
        Optional<ScreenPanelTextures.Texture> textureOpt = ScreenPanelTextures.forScreenKind(cue.screen());
        if (textureOpt.isEmpty()) {
            drawNeutralPanel(matrices, vertexConsumers, light, alpha);
            return;
        }
        ScreenPanelTextures.Texture texture = textureOpt.get();
        Identifier textureId = Identifier.of("minecraft", texture.path());

        int regionWidth = texture.regionWidth();
        int regionHeight = texture.regionHeight();
        float halfWidth = CONTAINER_WIDTH_BLOCKS / 2f;
        float panelHeight = CONTAINER_WIDTH_BLOCKS * regionHeight / regionWidth;
        float halfHeight = panelHeight / 2f;
        int alphaByte = Math.round(alpha * 255f);

        // Every edge y-coordinate is derived from one running pixel offset
        // (cumulativePx), not by repeatedly adding each band's own
        // already-rounded-to-blocks height -- that is what keeps adjacent
        // bands meeting exactly, with no visible seam or overlap between them.
        int cumulativePx = 0;
        for (ScreenPanelTextures.Band band : texture.bands()) {
            float top = halfHeight - panelHeight * cumulativePx / regionHeight;
            cumulativePx += band.height();
            float bottom = halfHeight - panelHeight * cumulativePx / regionHeight;

            // A band narrower than the texture's widest is centred under it.
            float bandHalfWidth = halfWidth * band.width() / regionWidth;
            float left = -bandHalfWidth;
            float right = bandHalfWidth;

            float minU = (float) band.u() / texture.canvasWidth();
            float maxU = (float) (band.u() + band.width()) / texture.canvasWidth();
            float minV = (float) band.v() / texture.canvasHeight();
            float maxV = (float) (band.v() + band.height()) / texture.canvasHeight();

            VertexConsumer vertices = vertexConsumers.getBuffer(CueRenderLayers.entityTranslucent(textureId));
            emitQuad(matrices.peek(), vertices, left, right, top, bottom, minU, maxU, minV, maxV,
                    light, 255, 255, 255, alphaByte);
        }
    }

    /**
     * The "a screen is open, but we are not saying which" panel — see bucket
     * BC's own Javadoc for why it deliberately shows something rather than
     * nothing.
     */
    private static void drawNeutralPanel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            float alpha) {
        float halfWidth = CONTAINER_WIDTH_BLOCKS / 2f;
        float halfHeight = NEUTRAL_PANEL_HEIGHT_BLOCKS / 2f;
        int bgAlphaByte = Math.round(alpha * CHAT_BG_MAX_ALPHA * 255f);

        VertexConsumer vertices = vertexConsumers.getBuffer(CueRenderLayers.entityTranslucent(PANEL_FILL_TEXTURE));
        emitQuad(matrices.peek(), vertices, -halfWidth, halfWidth, halfHeight, -halfHeight, 0f, 1f, 0f, 1f, light,
                CHAT_BG_R, CHAT_BG_G, CHAT_BG_B, bgAlphaByte);
    }

    private static void drawChatPanel(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            PlayerCue cue, float alpha, float age) {
        float halfWidth = CHAT_WIDTH_BLOCKS / 2f;
        float halfHeight = CHAT_HEIGHT_BLOCKS / 2f;
        int bgAlphaByte = Math.round(alpha * CHAT_BG_MAX_ALPHA * 255f);

        // No container texture (task requirement) -- a flat, semi-transparent
        // quad sampling our own solid-white fill, tinted dark by vertex colour.
        VertexConsumer vertices = vertexConsumers.getBuffer(CueRenderLayers.entityTranslucent(PANEL_FILL_TEXTURE));
        emitQuad(matrices.peek(), vertices, -halfWidth, halfWidth, halfHeight, -halfHeight, 0f, 1f, 0f, 1f, light,
                CHAT_BG_R, CHAT_BG_G, CHAT_BG_B, bgAlphaByte);

        drawChatText(matrices, vertexConsumers, light, cue, alpha, age);
    }

    private static void drawChatText(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light,
            PlayerCue cue, float alpha, float age) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) {
            return;
        }

        float seconds = age / TICKS_PER_SECOND;
        // P6 §4.1: same reducedMotion source as the pose frame above; read fresh
        // here rather than threaded down from render().
        boolean reducedMotion = ClientConfigState.get().reducedMotion();
        List<String> lines = FakeChatStream.lines(cue, seconds, reducedMotion);
        int caretColumn = FakeChatStream.caretColumn(cue, seconds, reducedMotion);

        // The blinking caret is this renderer's own UI chrome, not part of
        // FakeChatStream's simulated content -- appended to the in-progress line
        // ourselves, at the position FakeChatStream reports. Built once into
        // displayLines so both the width measurement below and the actual draw
        // loop see the exact same (caret-included) strings.
        int lastIndex = lines.size() - 1;
        String[] displayLines = lines.toArray(new String[0]);
        if (blinkOn(seconds, reducedMotion)) {
            String lastLine = displayLines[lastIndex];
            int column = Math.min(caretColumn, lastLine.length());
            displayLines[lastIndex] = lastLine.substring(0, column) + CARET_GLYPH;
        }

        int lineHeightPx = textRenderer.fontHeight + LINE_GAP_PX;
        float usableHeight = CHAT_HEIGHT_BLOCKS - 2f * CHAT_TEXT_MARGIN_BLOCKS;
        float heightScale = usableHeight / (FakeChatStream.VISIBLE_LINES * lineHeightPx);

        // DESIGN.md §7 P5 hand-test fix: also constrain by width, so a wide line's
        // pixels cannot run past the panel's edge -- the smaller of the two
        // candidate scales wins. See bucket BC's own comment for why this is
        // measured per frame rather than budgeted from a worst-case glyph width.
        float maxLineWidthPx = 0f;
        for (String line : displayLines) {
            if (!line.isEmpty()) {
                maxLineWidthPx = Math.max(maxLineWidthPx, textRenderer.getWidth(line));
            }
        }
        float usableWidth = CHAT_WIDTH_BLOCKS - 2f * CHAT_TEXT_MARGIN_BLOCKS;
        float widthScale = maxLineWidthPx <= 0f ? heightScale : usableWidth / maxLineWidthPx;
        float scale = Math.min(heightScale, widthScale);
        int colorWithAlpha = (Math.round(alpha * 255f) << 24) | CHAT_TEXT_RGB;

        matrices.push();
        // Top-left corner of the text area, in the already-tilted panel-local
        // frame drawPanel() established; then the same translate+scale(s,-s,s)
        // idiom AbstractSignBlockEntityRenderer#applyTextTransforms uses to
        // convert vanilla's y-down font-pixel space into this plane's y-up blocks.
        matrices.translate(-CHAT_WIDTH_BLOCKS / 2f + CHAT_TEXT_MARGIN_BLOCKS,
                CHAT_HEIGHT_BLOCKS / 2f - CHAT_TEXT_MARGIN_BLOCKS, TEXT_FORWARD_EPSILON);
        matrices.scale(scale, -scale, scale);

        for (int i = 0; i < displayLines.length; i++) {
            String line = displayLines[i];
            if (line.isEmpty()) {
                continue;
            }
            textRenderer.draw(Text.literal(line).asOrderedText(), 0f, i * lineHeightPx, colorWithAlpha, false,
                    matrices.peek().getPositionMatrix(), vertexConsumers,
                    TextRenderer.TextLayerType.NORMAL, 0, light);
        }
        matrices.pop();
    }

    /** P6 §4.1 / B5: under reducedMotion the caret is steadily on rather than absent — see bucket BC's Javadoc. */
    private static boolean blinkOn(float seconds, boolean reducedMotion) {
        if (reducedMotion) {
            return true;
        }
        return ((int) Math.floor(seconds * CARET_BLINK_HZ)) % 2 == 0;
    }

    /**
     * Front face counter-clockwise as seen from local {@code +Z} (the panel's
     * outward normal), plus a second, reversed-winding back face so the panel
     * does not shade as near-black when seen from the wearer's own side — see
     * bucket BC's own Javadoc for the hand-test round that found that.
     */
    private static void emitQuad(MatrixStack.Entry entry, VertexConsumer vertices,
            float left, float right, float top, float bottom,
            float minU, float maxU, float minV, float maxV, int light, int r, int g, int b, int alpha) {
        // Front face.
        vertex(entry, vertices, left, top, minU, minV, light, r, g, b, alpha, 0f, 0f, 1f); // top-left
        vertex(entry, vertices, left, bottom, minU, maxV, light, r, g, b, alpha, 0f, 0f, 1f); // bottom-left
        vertex(entry, vertices, right, bottom, maxU, maxV, light, r, g, b, alpha, 0f, 0f, 1f); // bottom-right
        vertex(entry, vertices, right, top, maxU, minV, light, r, g, b, alpha, 0f, 0f, 1f); // top-right

        // Back face: same corners, reversed winding, opposite normal.
        vertex(entry, vertices, right, top, maxU, minV, light, r, g, b, alpha, 0f, 0f, -1f); // top-right
        vertex(entry, vertices, right, bottom, maxU, maxV, light, r, g, b, alpha, 0f, 0f, -1f); // bottom-right
        vertex(entry, vertices, left, bottom, minU, maxV, light, r, g, b, alpha, 0f, 0f, -1f); // bottom-left
        vertex(entry, vertices, left, top, minU, minV, light, r, g, b, alpha, 0f, 0f, -1f); // top-left
    }

    private static void vertex(MatrixStack.Entry entry, VertexConsumer vertices, float x, float y,
            float u, float v, int light, int r, int g, int b, int alpha, float nx, float ny, float nz) {
        vertices.vertex(entry.getPositionMatrix(), x, y, 0f)
                .color(r, g, b, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(entry, nx, ny, nz);
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
