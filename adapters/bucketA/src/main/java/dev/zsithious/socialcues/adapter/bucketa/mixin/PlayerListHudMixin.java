package dev.zsithious.socialcues.adapter.bucketa.mixin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.adapter.compat.CueGuiIcons;
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
 * DESIGN.md §7 Katman 2 — draws the 8×8 status icon next to a tab list row,
 * bucket A's copy.
 *
 * <p><b>This file is bucket BC's, unchanged apart from its package.</b> That is
 * a measurement, not an assumption: {@code javap} on the 1.21 mapped jar shows
 * {@code PlayerListHud#renderLatencyIcon(DrawContext context, int width, int x,
 * int y, PlayerListEntry entry)} with exactly the signature the other two
 * buckets bind to, and {@code GameProfile#getId()}/{@code getName()} are the
 * authlib 6.x spellings these rows ship (the 7.x rename is at 1.21.9, i.e.
 * bucket D's problem). The one call that <em>does</em> differ across the twelve
 * rows — drawing a scaled sub-rectangle of an atlas — was moved out to {@code
 * adapter.compat.CueGuiIcons} during P7 precisely so this file would not have
 * to fork again; {@code adapters/compat/from-1.21/}'s body is the one written
 * against these rows' {@code drawTexture(Identifier, x, y, width, height, u, v,
 * regionWidth, regionHeight, textureWidth, textureHeight)} form, and it was
 * written and compiling before this bucket had any render at all.
 *
 * <p>See {@code adapter.bucketbc.mixin.PlayerListHudMixin} for the full
 * reasoning: why {@code renderLatencyIcon}'s named parameters are a safer hook
 * than {@code render()}'s anonymous locals, why a plain additive {@code
 * @Inject} at {@code HEAD} is the minimal-conflict choice here rather than
 * {@code ModifyArg}/{@code ModifyVariable}, why the icon lands immediately left
 * of the ping icon, why {@code tabCueOf} and not {@code cueOf}, and why P6's
 * {@code textOnly} deliberately does not apply to an 8×8 column.
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
            // getId(), not id(): these rows ship authlib 6.x — see the class Javadoc.
            UUID id = entry.getProfile().getId();
            // Katman 2 (tab list): near tier if we have it, else the coarse
            // global tier — see RemoteCueStore.tabCueOf's own Javadoc for why
            // this is deliberately not cueOf.
            Optional<PlayerCue> cueOpt = RemoteCueStoreHolder.get().tabCueOf(id);
            if (cueOpt.isEmpty()) {
                return;
            }
            PlayerCue cue = cueOpt.get();
            ClientConfigData config = ClientConfigState.get();
            if (!TabListCueVisibility.shouldRenderIcon(cue, config, entry.getProfile().getName())) {
                return;
            }

            int cell = CueDisplaySelector.atlasCellFor(cue);
            // Right-aligned within the row's reserved column, immediately left of the
            // 10px-wide ping icon (renderLatencyIcon draws it at x + width - 11).
            int iconX = x + width - 11 - GAP_BEFORE_PING_ICON - ICON_SIZE;
            // The compat hop — see CueGuiIcons' Javadoc for the measured three-way
            // split (1.21 / 1.21.2 / 1.21.6) this bucket sits at the oldest end of.
            CueGuiIcons.drawAtlasCell(context, CUES_TEXTURE, cell, iconX, y, ICON_SIZE);
        } catch (Throwable t) {
            socialcues$disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 2 (tab list) rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }
}
