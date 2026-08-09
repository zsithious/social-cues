package dev.zsithious.socialcues.adapter.bucketd.render;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/**
 * DESIGN.md §7 Katman 3, P5b — the held panel: a container GUI for
 * {@code IN_SCREEN}, a chat window for the four {@code TYPING_*} activities.
 * {@link PoseFrame#screenWeight()}/{@link PoseFrame#screenTilt()}/
 * {@link PoseFrame#screenRise()} already carry every number this class
 * needs; the maths P5a's {@code PlayerEntityModelMixin} left out on purpose
 * (see its own Javadoc) lives here.
 *
 * <p><b>Hook — same space as Layer 1, not a {@code FeatureRenderer}, and not
 * a new mixin.</b> This is called from the tail of {@code
 * PlayerEntityRenderer#renderLabelIfPresent} — the exact same injection
 * point {@code adapter.bucketd.mixin.PlayerEntityRendererMixin}'s {@code
 * socialcues$drawBillboard} already uses to call {@link CueBillboardRenderer}.
 * {@link CueBillboardRenderer}'s own class Javadoc has the full {@code
 * javap}-verified account of why: a {@code FeatureRenderer} is handed the
 * pose-dependent, mirrored, yaw-rotated <em>model</em> matrix stack, and
 * {@code renderLabelIfPresent} is the one player-rendering hook that runs
 * with a clean, world-axis-aligned, camera-translated stack instead — P4b
 * learned this the hard way (an upside-down, ground-level icon) so this
 * class does not repeat that mistake for a much larger, more visible piece
 * of geometry. No new mixin is needed either: the existing {@code
 * socialcues$drawBillboard} injection just gains a second, independently
 * guarded call.
 *
 * <p><b>Why the panel is body-relative and not camera-facing.</b> Layer 1's
 * icon is a billboard (always faces the camera, {@code
 * matrices.multiply(camera.orientation)}) because it is a HUD-like status
 * indicator with no "wrong side". A held panel is not that — it is an object
 * in the world the player is holding up, and DESIGN.md's own brief is
 * explicit that it must NOT be camera-facing. So instead of orienting to the
 * camera, this orients to {@link PlayerEntityRenderState#bodyYaw} (a public
 * {@code float} on {@code LivingEntityRenderState}, {@code javap}-verified —
 * see below) the same way vanilla itself orients the whole body model in
 * {@code LivingEntityRenderer#setupTransforms} (also {@code javap}-verified:
 * {@code matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f -
 * bodyYaw))}, immediately before the mirroring {@code scale(-1,-1,1)} that
 * space does and this hook's clean stack does not). That means a viewer
 * standing in front of the player sees the panel's textured face — exactly
 * the "rotate it to face outward from the player" requirement — and turning
 * around to walk behind the player turns the panel's back to them instead,
 * matching how a physically held object would behave.
 *
 * <p><b>The placement maths, derived once and reused for both position and
 * orientation</b> (see {@link #drawPanel} — this paragraph is the proof it
 * is correct, not just a description): {@code Entity.getRotationVector(float,
 * float)}'s bytecode ({@code javap -c}) gives the ground-truth forward
 * vector for a yaw in degrees as {@code (-sin(yawRad), 0, cos(yawRad))}.
 * Separately, {@code javap -c} on {@code RotationAxis}'s enum constants shows
 * {@code POSITIVE_Y.rotation(angle)} is exactly {@code new
 * Quaternionf().rotationY(angle)} (JOML, no extra negation) — whose standard
 * rotation matrix sends local {@code (0,0,1)} to {@code (sin(angle), 0,
 * cos(angle))}. Solving {@code sin(angle) = -sin(yawRad)} and
 * {@code cos(angle) = cos(yawRad)} gives {@code angle = -yawRad}, i.e.
 * {@code matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state
 * .bodyYaw))} sends local {@code +Z} to the player's true forward direction
 * and (by the same rotation matrix) local {@code +X} to
 * {@code (cos(yawRad), 0, sin(yawRad))} — which is exactly {@code up × forward}
 * for {@code up = (0,1,0)}, i.e. a right-handed {@code (right, up, forward)}
 * frame. Once that rotation is on the {@link MatrixStack}, a plain
 * {@code translate(0, 0, PANEL_FORWARD_OFFSET)} steps forward in the
 * <em>local</em>, now-rotated frame — which is the world forward direction —
 * with no manual trigonometry needed at the call site; this is the identical
 * nested translate/rotate/translate idiom every {@code ModelPart} hierarchy
 * in this codebase already relies on (see {@code PlayerEntityModelMixin}'s
 * own Javadoc on why a child's transform composes inside its parent's).
 *
 * <p><b>The tilt composes the same way.</b> A second {@code multiply(
 * RotationAxis.POSITIVE_X.rotation(frame.screenTilt()))}, applied after the
 * yaw and the forward step, rotates about the <em>current</em> local X axis
 * — which the paragraph above already establishes equals world "right" —
 * pivoting the panel's own center. Working out where local {@code +Y}
 * (\"up\") and local {@code +Z} (\"forward/normal\") land under a rotation
 * about X by {@code screenTilt} gives {@code newUp = up·cosθ - forward·sinθ}
 * and {@code newNormal = forward·cosθ + up·sinθ}: as {@code θ} grows from 0,
 * the panel's top edge (whatever is in the {@code +newUp} direction) gains a
 * component in {@code -forward} — i.e. leans back toward the player, exactly
 * DESIGN.md's demo-material description — while the visible face still
 * points mostly outward. Because {@link MatrixStack} composes rotations by
 * always operating in the current local frame, none of this has to be
 * computed by hand here: the two {@code multiply} calls plus a plain
 * {@code (halfWidth, halfHeight, 0)}-cornered quad already sit in exactly
 * this tilted frame.
 *
 * <p><b>{@code screenRise} is a position, not an intensity</b> (see {@link
 * PoseFrame}'s own Javadoc and {@code PoseAnimator.scale()}, which
 * deliberately does not multiply it by weight) — it is applied once, as a
 * plain vertical {@code translate}, before the body-yaw rotation.
 *
 * <p><b>Container texture:</b> {@link ScreenPanelTextures} (pure {@code
 * core}, no {@code Identifier}) supplies the resource path and the region/
 * canvas pixel sizes measured off the real 1.21.11 client jar assets; this
 * class turns the path into a {@code minecraft}-namespaced {@code
 * Identifier} and preserves the region's aspect ratio when sizing the quad.
 *
 * <p><b>Chat text is drawn in the panel's own plane, not billboarded.</b>
 * {@link FakeChatStream#lines} is rendered with {@code
 * OrderedRenderCommandQueue#submitText} — the same queue method {@code
 * javap}-verified (via {@code AbstractSignBlockEntityRenderer#renderText},
 * the closest vanilla precedent for "flat text fixed to a plane in the
 * world", the same reasoning family as sign text) rather than {@code
 * submitLabel}, which is what vanilla nameplates use and which auto-orients
 * text to face the camera — exactly the camera-facing behaviour the panel
 * itself must NOT have. The text sub-block pushes its own nested transform
 * (translate to the text area's top-left corner, then {@code scale(s, -s,
 * s)} — the same negative-Y-scale idiom {@code
 * AbstractSignBlockEntityRenderer#applyTextTransforms} uses, since font
 * glyphs are authored in a y-down pixel space) so {@code submitText}'s
 * {@code x}/{@code y} arguments are plain font-pixel offsets inside the
 * already-tilted panel frame.
 *
 * <p><b>Visibility rules are reused, not re-implemented</b> — {@link
 * BillboardCueVisibility#shouldRender} covers mute list, {@code
 * showOnSelf}/third-person, {@code MUTED_SELF}, and max distance, exactly
 * the same as Layer 1. One consequence worth being explicit about: that
 * method also checks {@code config.layer1Enabled()} internally (it has no
 * separate parameter for "which layer is asking"), so turning Layer 1 off
 * hides this panel too, on top of the {@code layer3Enabled} gate this class
 * checks itself. That is a side effect of reuse, not a deliberate design
 * decision — DESIGN.md doesn't say whether the panel should survive Layer 1
 * being switched off, and writing a second copy of the mute/self/distance
 * rules to avoid this one coupling would violate the "don't duplicate the
 * rules" instruction this task was given. {@code F1} ({@code
 * client.options.hudHidden}) is checked directly here instead, mirroring
 * {@link CueBillboardRenderer#render} — it was never part of {@code
 * BillboardCueVisibility} to begin with (see that class's Javadoc), so this
 * is not a duplicate, just the same one-line check every entry point into
 * this hook already needs to make for itself.
 *
 * <p><b>Never throws</b> — same guarded stance as every other layer in this
 * codebase: the first {@code Throwable} disables the panel for the rest of
 * the session and logs exactly once, at {@code SEVERE}, never {@code FINE}
 * (DESIGN.md §7's P4 hand-test note on why a swallowed, invisible-by-default
 * log is the wrong failure mode for a "nothing appears" layer).
 */
public final class CueScreenPanelRenderer {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** Ticks per second — matches {@code PoseAnimator}'s own private constant; {@link FakeChatStream} takes seconds, not ticks. */
    private static final float TICKS_PER_SECOND = 20f;

    /** Social Cues' own flat-white fill (see {@code tools/gen_panel_fill.py}) — the chat panel's dark background is this, tinted. */
    private static final Identifier PANEL_FILL_TEXTURE = Identifier.of("socialcues", "textures/gui/panel_fill.png");

    // ------------------------------------------------------- placement (own design choices)
    // DESIGN.md doesn't pin any of these numbers down (same situation
    // CueBillboardRenderer.BASE_ICON_SIZE already documents), so they were
    // chosen to fit the task brief's "roughly 1.0-1.2 blocks wide" and
    // tuned by eye in-game, same as everywhere else in this codebase.

    /**
     * Blocks in front of the entity origin the panel's center sits, along body
     * yaw. DESIGN.md §7 P5 hand-test fix: the typing pose's hand sits roughly
     * 0.65 blocks forward at this pitch (see {@code
     * PoseAnimator.TYPING_ARM_PITCH}'s own Javadoc) — the panel used to sit
     * well short of that (0.34), which is why hands appeared to poke through
     * it; now it sits just behind the fingers instead.
     */
    private static final float PANEL_FORWARD_OFFSET = 0.58f;

    /**
     * Container-GUI panel width; height is derived from the texture's own
     * aspect ratio (see {@link #drawContainerPanel}). DESIGN.md §7 P5
     * hand-test fix: 1.1 blocks was wider than the player model itself (0.6
     * blocks) and, at generic_54's aspect ratio, 1.39 blocks tall — a slab
     * that cut across the whole body. Sized to read as a held tablet instead.
     */
    private static final float CONTAINER_WIDTH_BLOCKS = 0.58f;

    /** Chat-window panel size — independent of the container panel's aspect ratio, matches vanilla's own wide-short chat box shape. */
    private static final float CHAT_WIDTH_BLOCKS = 0.62f;
    private static final float CHAT_HEIGHT_BLOCKS = 0.34f;
    private static final float CHAT_TEXT_MARGIN_BLOCKS = 0.045f;

    /** Nudges the text just off the background quad's own plane so the two never z-fight. */
    private static final float TEXT_FORWARD_EPSILON = 0.004f;

    private static final int CHAT_BG_R = 12;
    private static final int CHAT_BG_G = 12;
    private static final int CHAT_BG_B = 16;
    /** Matches vanilla's own chat HUD background darkness in spirit (a translucent near-black box), scaled further by {@link PoseFrame#screenWeight()}. */
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
    public static void renderGuarded(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        if (disabledByError) {
            return;
        }
        try {
            render(state, matrices, queue);
        } catch (Throwable t) {
            disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 3 held-panel rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }

    private static void render(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue) {
        UUID id = ((CueUuidHolder) (Object) state).socialcues$getUuid();
        if (id == null) {
            return; // Not captured for this state (e.g. a render state our PlayerEntityRendererMixin hook never saw).
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return; // DESIGN.md §7: F1 hides this layer too — see class Javadoc for why it is checked here, not in BillboardCueVisibility.
        }
        ClientConfigData config = ClientConfigState.get();
        if (!config.layer3Enabled()) {
            return; // Task requirement: gated on layer3Enabled specifically -- this is Layer 3 content, only hosted at Layer 1's hook.
        }

        Optional<PoseBlend.Blend> blendOpt = PoseBlendDriver.blendFor(id);
        if (blendOpt.isEmpty()) {
            return; // Nothing tracked for this player right now.
        }
        PoseBlend.Blend blend = blendOpt.get();

        PoseFrame frame = PoseAnimator.frameFor(blend.cue(), state.age, blend.weight());
        if (!frame.hasScreen()) {
            return; // Task requirement: gated on frame.hasScreen() -- AFK/NORMAL/SPEAKING never have a panel.
        }
        float alpha = clamp01(frame.screenWeight());
        if (alpha <= 0f) {
            return; // Weight rounded to nothing worth drawing (mirrors CueBillboardRenderer's DistanceFade bail).
        }

        boolean isSelf = client.player != null && id.equals(client.player.getUuid());
        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();
        double distance = Math.sqrt(state.squaredDistanceToCamera);
        String playerName = resolvePlayerName(client, id);

        // Reused wholesale, not re-implemented -- see class Javadoc for the one
        // consequence worth knowing (this also enforces config.layer1Enabled()).
        if (!BillboardCueVisibility.shouldRender(blend.cue(), isSelf, thirdPerson, distance, config, playerName)) {
            return;
        }

        drawPanel(state, matrices, queue, blend.cue(), frame, alpha);
    }

    private static String resolvePlayerName(MinecraftClient client, UUID id) {
        if (client.getNetworkHandler() == null) {
            return "";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
        if (entry == null || entry.getProfile() == null) {
            return "";
        }
        return entry.getProfile().name(); // javap-verified (see CueBillboardRenderer/PlayerListHudMixin): name(), not getName().
    }

    /** See the class Javadoc for the full derivation of this transform. */
    private static void drawPanel(PlayerEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue,
            PlayerCue cue, PoseFrame frame, float alpha) {
        matrices.push();
        matrices.translate(0f, frame.screenRise(), 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-state.bodyYaw));
        matrices.translate(0f, 0f, PANEL_FORWARD_OFFSET);
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(frame.screenTilt()));

        switch (cue.activity()) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK -> drawChatPanel(matrices, queue, state, cue, alpha);
            case IN_SCREEN -> drawContainerPanel(matrices, queue, state, cue, alpha);
            // Unreachable: frame.hasScreen() already filtered out NORMAL/AFK/SPEAKING
            // (PoseAnimator never sets screenWeight > 0 for them). Kept exhaustive
            // anyway, CueIconAtlas.cellFor's precedent: a future Activity added to
            // PoseAnimator's screen handling without a case here fails to compile.
            case NORMAL, AFK, SPEAKING -> { }
        }

        matrices.pop();
    }

    /**
     * DESIGN.md §7 P5 hand-test follow-up: {@link ScreenPanelTextures.Texture}
     * can carry more than one {@link ScreenPanelTextures.Band} now (see that
     * class's own Javadoc on why: {@code CONTAINER_SMALL} reproduces
     * vanilla's real two-blit single-chest composite rather than a truncated
     * single crop). One quad is emitted per band, stacked vertically from the
     * panel's top edge down, each sized proportionally to its own share of
     * {@link ScreenPanelTextures.Texture#regionHeight()} — for the common
     * single-band case this is exactly the one quad this method always drew.
     */
    private static void drawContainerPanel(MatrixStack matrices, OrderedRenderCommandQueue queue,
            PlayerEntityRenderState state, PlayerCue cue, float alpha) {
        ScreenPanelTextures.Texture texture = ScreenPanelTextures.forScreenKind(cue.screen());
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

            // A band narrower than the texture's widest is centred under it
            // (no texture shipped by this table needs that yet, but the rule
            // stays consistent for whichever one eventually does).
            float bandHalfWidth = halfWidth * band.width() / regionWidth;
            float left = -bandHalfWidth;
            float right = bandHalfWidth;

            float minU = (float) band.u() / texture.canvasWidth();
            float maxU = (float) (band.u() + band.width()) / texture.canvasWidth();
            float minV = (float) band.v() / texture.canvasHeight();
            float maxV = (float) (band.v() + band.height()) / texture.canvasHeight();

            queue.submitCustom(matrices, RenderLayers.entityTranslucent(textureId), (entry, vertices) ->
                    emitQuad(entry, vertices, left, right, top, bottom, minU, maxU, minV, maxV,
                            state.light, 255, 255, 255, alphaByte));
        }
    }

    private static void drawChatPanel(MatrixStack matrices, OrderedRenderCommandQueue queue,
            PlayerEntityRenderState state, PlayerCue cue, float alpha) {
        float halfWidth = CHAT_WIDTH_BLOCKS / 2f;
        float halfHeight = CHAT_HEIGHT_BLOCKS / 2f;
        int bgAlphaByte = Math.round(alpha * CHAT_BG_MAX_ALPHA * 255f);

        // No container texture (task requirement) -- a flat, semi-transparent
        // quad sampling our own solid-white fill, tinted dark by vertex colour.
        queue.submitCustom(matrices, RenderLayers.entityTranslucent(PANEL_FILL_TEXTURE), (entry, vertices) ->
                emitQuad(entry, vertices, -halfWidth, halfWidth, halfHeight, -halfHeight, 0f, 1f, 0f, 1f, state.light,
                        CHAT_BG_R, CHAT_BG_G, CHAT_BG_B, bgAlphaByte));

        drawChatText(matrices, queue, state, cue, alpha);
    }

    private static void drawChatText(MatrixStack matrices, OrderedRenderCommandQueue queue,
            PlayerEntityRenderState state, PlayerCue cue, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer textRenderer = client.textRenderer;
        if (textRenderer == null) {
            return;
        }

        float seconds = state.age / TICKS_PER_SECOND;
        List<String> lines = FakeChatStream.lines(cue, seconds);
        int caretColumn = FakeChatStream.caretColumn(cue, seconds);

        // The blinking caret is this renderer's own UI chrome, not part of
        // FakeChatStream's simulated content (whose alphabet is deliberately
        // letters-and-spaces-only, see that class's Javadoc) -- appended to the
        // in-progress line ourselves, at the position FakeChatStream reports.
        // Built once into displayLines so both the width measurement below and
        // the actual draw loop see the exact same (caret-included) strings.
        int lastIndex = lines.size() - 1;
        String[] displayLines = lines.toArray(new String[0]);
        if (blinkOn(seconds)) {
            String lastLine = displayLines[lastIndex];
            int column = Math.min(caretColumn, lastLine.length());
            displayLines[lastIndex] = lastLine.substring(0, column) + CARET_GLYPH;
        }

        int lineHeightPx = textRenderer.fontHeight + LINE_GAP_PX;
        float usableHeight = CHAT_HEIGHT_BLOCKS - 2f * CHAT_TEXT_MARGIN_BLOCKS;
        float heightScale = usableHeight / (FakeChatStream.VISIBLE_LINES * lineHeightPx);

        // DESIGN.md §7 P5 hand-test fix: the scale used to be derived purely from
        // line count/font height, so nothing ever stopped a wide line's pixels
        // from running past the panel's edge. Also constrain by width: the widest
        // of the currently visible lines (caret included) must fit CHAT_WIDTH_BLOCKS
        // minus its own margins, so the smaller of the two candidate scales wins.
        //
        // Why measured-per-frame rather than a fixed character-count budget: a
        // fixed budget (e.g. textRenderer.getWidth of the alphabet's single
        // widest glyph, repeated MAX_LINE_LENGTH times) would never need to
        // change frame to frame, which is maximally stable, but it is a
        // worst-case bound -- ordinary gibberish (a mix of narrow and wide
        // lowercase letters) is usually well under it, so text would render
        // smaller than the panel actually allows almost all the time. This
        // renderer measures the real, currently-visible lines instead and pairs
        // it with FakeChatStream.MAX_LINE_LENGTH being cut down (see that
        // class's Javadoc) so a full-length line already fits comfortably under
        // heightScale in the common case -- widthScale only ever binds, and the
        // scale only ever visibly moves, on the rare line that leans unusually
        // wide, not on every keystroke.
        float maxLineWidthPx = 0f;
        for (String line : displayLines) {
            if (!line.isEmpty()) {
                maxLineWidthPx = Math.max(maxLineWidthPx, textRenderer.getWidth(line));
            }
        }
        float usableWidth = CHAT_WIDTH_BLOCKS - 2f * CHAT_TEXT_MARGIN_BLOCKS;
        float widthScale = maxLineWidthPx <= 0f ? heightScale : usableWidth / maxLineWidthPx;
        float scale = Math.min(heightScale, widthScale);
        // Text alpha follows the same panel fade as the background quad; submitText's
        // colour int is assumed to honour its alpha byte the way vanilla's own chat
        // HUD/actionbar fade does -- not independently javap-traced all the way into
        // the vertex consumer for this build (see this task's final report).
        int colorWithAlpha = (Math.round(alpha * 255f) << 24) | CHAT_TEXT_RGB;

        matrices.push();
        // Top-left corner of the text area, in the already-tilted panel-local
        // frame drawPanel() established; then the same translate+scale(s,-s,s)
        // idiom AbstractSignBlockEntityRenderer#applyTextTransforms uses (javap
        // -c-verified) to convert vanilla's y-down font-pixel space into this
        // plane's y-up blocks.
        matrices.translate(-CHAT_WIDTH_BLOCKS / 2f + CHAT_TEXT_MARGIN_BLOCKS,
                CHAT_HEIGHT_BLOCKS / 2f - CHAT_TEXT_MARGIN_BLOCKS, TEXT_FORWARD_EPSILON);
        matrices.scale(scale, -scale, scale);

        for (int i = 0; i < displayLines.length; i++) {
            String line = displayLines[i];
            if (line.isEmpty()) {
                continue;
            }
            queue.submitText(matrices, 0f, i * lineHeightPx, Text.literal(line).asOrderedText(), false,
                    TextRenderer.TextLayerType.NORMAL, state.light, colorWithAlpha, 0, 0);
        }
        matrices.pop();
    }

    private static boolean blinkOn(float seconds) {
        return ((int) Math.floor(seconds * CARET_BLINK_HZ)) % 2 == 0;
    }

    /**
     * Counter-clockwise as seen from local {@code +Z} — matches {@code
     * CueBillboardRenderer.emitQuad}'s exact winding convention, which is
     * what keeps this quad visible under normal backface culling. Here local
     * {@code +Z} is the panel's outward normal (see class Javadoc), so the
     * front face is visible from in front of the player.
     *
     * <p><b>DESIGN.md §7 P5 hand-test fix — a second, back-facing quad.</b>
     * The hand-test screenshot showed the panel reading as nearly solid black
     * when seen from the wearer's own side (e.g. third person, looking past
     * your own shoulder): a single-sided quad's {@code normal(entry, 0, 0,
     * 1)} only matches the diffuse lighting model from the front, so from
     * behind the same normal points *away* from the viewer and the surface
     * shades as if it were facing away from every light. Real held objects
     * (and vanilla's own two-sided GUI-container quads) do not go dark from
     * the back, so this emits a second quad with reversed winding (correct
     * for backface culling when viewed from {@code -Z}) and {@code normal(entry,
     * 0, 0, -1)}, sharing the same texture/colour/alpha. The texture is not
     * mirrored front-to-back (same as the text on it not being mirrored
     * either) — a minor, accepted imperfection for a decorative cue panel,
     * not a readable-from-behind requirement.
     */
    /**
     * DESIGN.md §7 P5 hand-test follow-up: takes explicit {@code left}/
     * {@code right}/{@code top}/{@code bottom} edges rather than a half-width/
     * half-height centred on the local origin, so {@link #drawContainerPanel}
     * can stack several of these (one per {@link ScreenPanelTextures.Band})
     * at different vertical offsets on the same panel; a quad centred at
     * {@code (0,0)} — every other caller — is just {@code left=-halfWidth,
     * right=halfWidth, top=halfHeight, bottom=-halfHeight}.
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
