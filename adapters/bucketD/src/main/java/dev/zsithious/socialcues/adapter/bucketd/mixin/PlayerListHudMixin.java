package dev.zsithious.socialcues.adapter.bucketd.mixin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.CueDisplaySelector;
import dev.zsithious.socialcues.core.client.CueIconAtlas;
import dev.zsithious.socialcues.core.client.TabListCueVisibility;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.mcshared.client.RemoteCueStoreHolder;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;

/**
 * DESIGN.md §7 Katman 2 — draws the 8×8 status icon next to a tab list row.
 *
 * <p><b>Hook point:</b> {@code javap -c}-verified on the 1.21.11 mapped jar,
 * {@code PlayerListHud#renderLatencyIcon(DrawContext context, int width, int
 * x, int y, PlayerListEntry entry)} is called once per visible row from
 * {@code render(...)}'s own per-entry loop, already carrying real, *named*
 * parameters — {@code x}/{@code width} are the row's reserved-column left
 * edge and width (the ping icon itself draws at
 * {@code x + width - 11}, an 8px-wide, 1px-margined icon, per that method's
 * own body), {@code y} matches the row's name baseline exactly, and
 * {@code entry} is the row's own {@code PlayerListEntry}. This is a
 * deliberately different, *safer* choice than reading the raw local
 * variables inside {@code render()} itself (which also computes a row
 * {@code x}/{@code y} but only as anonymous {@code int} locals at slot
 * indices, not through any named, Mixin-annotation-processor-checked method
 * signature) — see DESIGN.md §7's "P4b uygulama notu" for why, and for where
 * this ends up placing the icon (immediately left of the ping icon, not
 * literally adjacent to the name text).
 *
 * <p><b>{@code @Inject} over {@code ModifyArg}/{@code ModifyVariable}:</b>
 * DESIGN.md §7 asks for the latter where possible to shrink the conflict
 * surface with other tab-list mods. That preference is about not fighting
 * over a value another mixin also wants to change; this mixin never reads or
 * changes anything {@code renderLatencyIcon} itself computes — it only draws
 * an unrelated, additional icon through the same already-available
 * {@code DrawContext} — so a plain additive {@code @Inject(at = "HEAD")} is
 * both correct and the actual minimal-conflict choice here (nothing to fight
 * over: two mods each doing their own additive {@code HEAD} inject coexist
 * fine). High {@link Mixin#priority} (applied late, DESIGN.md §7) still
 * applies in case another mod's mixin on this exact method wants to cancel
 * or fully replace it.
 *
 * <p><b>Never throws:</b> any lookup/render failure here is caught rather
 * than propagated, so a conflict with another tab-list mod (or a genuine bug)
 * degrades to "no tab list icons", never a crashed or blanked tab list
 * (DESIGN.md §11). It is caught <em>loudly</em> and once, though — the same
 * stance as {@code ClientCueCapture.tickGuarded} and
 * {@code CueBillboardRenderer.renderGuarded}. P4b's original
 * {@code Level.FINE} log was invisible under the default logging config,
 * which is exactly the wrong behaviour for a layer whose only failure mode is
 * "nothing appears": a hand test could not tell a swallowed bug apart from a
 * player who genuinely had no cue.
 */
@Mixin(value = PlayerListHud.class, priority = 2000)
public class PlayerListHudMixin {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** DESIGN.md §7 P4a note: the atlas texture path, namespaced for {@code Identifier}. */
    private static final Identifier CUES_TEXTURE = Identifier.of("socialcues", CueIconAtlas.TEXTURE_PATH);

    /** Screen-space size of the tab list icon (DESIGN.md §7: "8×8 ikon"). */
    private static final int ICON_SIZE = 8;

    /** Gap, in pixels, between our icon and the ping/latency icon it sits to the left of. */
    private static final int GAP_BEFORE_PING_ICON = 2;

    /** See the class Javadoc: one loud line, then quiet, never a per-row log flood. */
    private static boolean socialcues$disabledByError;

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"))
    private void socialcues$drawCueIcon(DrawContext context, int width, int x, int y, PlayerListEntry entry,
            CallbackInfo ci) {
        if (socialcues$disabledByError) {
            return;
        }
        try {
            if (entry == null || entry.getProfile() == null) {
                return;
            }
            // javap -c-verified (1.21.11): com.mojang.authlib.GameProfile exposes
            // id()/name(), not the older getId()/getName() (authlib went record-style).
            UUID id = entry.getProfile().id();
            // Katman 2 (tab list): near tier if we have it, else the coarse
            // global tier — see RemoteCueStore.tabCueOf's own Javadoc for why
            // this is deliberately not cueOf (that one is world-render-only,
            // near tier exclusively, DESIGN.md §5's P5 hand-test bugfix).
            Optional<PlayerCue> cueOpt = RemoteCueStoreHolder.get().tabCueOf(id);
            if (cueOpt.isEmpty()) {
                return;
            }
            PlayerCue cue = cueOpt.get();
            ClientConfigData config = ClientConfigState.get();
            if (!TabListCueVisibility.shouldRenderIcon(cue, config, entry.getProfile().name())) {
                return;
            }

            int cell = CueDisplaySelector.atlasCellFor(cue);
            // Right-aligned within the row's reserved column, immediately left of the
            // 10px-wide ping icon (renderLatencyIcon draws it at x + width - 11).
            int iconX = x + width - 11 - GAP_BEFORE_PING_ICON - ICON_SIZE;
            // Argument order is (x1, y1, x2, y2), *not* (x1, x2, y1, y2) — javap -c
            // traced through both private overloads: the public entry point forwards
            // (p2, p4, p3, p5) into a private (x1, x2, y1, y2) one, which in turn feeds
            // TexturedQuadGuiElementRenderState's (x1, y1, x2, y2). P4b assumed the
            // inner order and so passed a corner-swapped, off-screen rectangle: the
            // icon never appeared during the P4 hand test, silently and with no error.
            context.drawTexturedQuad(CUES_TEXTURE, iconX, y, iconX + ICON_SIZE, y + ICON_SIZE,
                    CueIconAtlas.minU(cell), CueIconAtlas.maxU(cell), CueIconAtlas.minV(cell), CueIconAtlas.maxV(cell));
        } catch (Throwable t) {
            socialcues$disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 2 (tab list) rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }
}
