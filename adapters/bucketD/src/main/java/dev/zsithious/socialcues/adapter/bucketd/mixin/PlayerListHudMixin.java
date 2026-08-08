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
 * <p><b>Never throws:</b> any lookup/render failure here is caught and
 * logged rather than propagated, so a conflict with another tab-list mod (or
 * a genuine bug) degrades to "no icon this row", never a crashed or blanked
 * tab list (DESIGN.md §11).
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

    @Inject(method = "renderLatencyIcon", at = @At("HEAD"))
    private void socialcues$drawCueIcon(DrawContext context, int width, int x, int y, PlayerListEntry entry,
            CallbackInfo ci) {
        try {
            if (entry == null || entry.getProfile() == null) {
                return;
            }
            // javap -c-verified (1.21.11): com.mojang.authlib.GameProfile exposes
            // id()/name(), not the older getId()/getName() (authlib went record-style).
            UUID id = entry.getProfile().id();
            Optional<PlayerCue> cueOpt = RemoteCueStoreHolder.get().cueOf(id);
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
            context.drawTexturedQuad(CUES_TEXTURE, iconX, iconX + ICON_SIZE, y, y + ICON_SIZE,
                    CueIconAtlas.minU(cell), CueIconAtlas.maxU(cell), CueIconAtlas.minV(cell), CueIconAtlas.maxV(cell));
        } catch (RuntimeException e) {
            LOGGER.log(Level.FINE, "socialcues: failed to draw a tab list cue icon", e);
        }
    }
}
