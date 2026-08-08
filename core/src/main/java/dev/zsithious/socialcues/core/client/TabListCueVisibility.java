package dev.zsithious.socialcues.core.client;

import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 Katman 2 / P4b task note §3.2 — "Sekme listesi için: bir
 * oyuncunun satırında ikon çizilecek mi." Deliberately simpler than
 * {@link BillboardCueVisibility}: no distance, camera, or "self row" case.
 *
 * <p>No self-row case because DESIGN.md §5's relay never sends a viewer its
 * own cue back (see {@link BillboardCueVisibility}'s Javadoc for the same
 * point) — {@code core.client.RemoteCueStore} structurally never has an entry
 * keyed by the local player's own id, so the tab list row the adapter
 * happens to be drawing for the local player will simply never have a
 * {@link PlayerCue} to look up in the first place; there is nothing to
 * special-case here.
 *
 * <p>No distance/camera factor because the tab list is a flat screen overlay,
 * not a world-space billboard — every visible row is equally "in view"
 * regardless of where the corresponding player actually is.
 */
public final class TabListCueVisibility {

    private TabListCueVisibility() {
    }

    /**
     * @param cue        the remote store's cue for the row's player
     * @param config     the loaded client config
     * @param playerName the row's player's current Mojang username, for
     *                   {@link ClientConfigData#isMuted}
     */
    public static boolean shouldRenderIcon(PlayerCue cue, ClientConfigData config, String playerName) {
        Objects.requireNonNull(cue, "cue");
        Objects.requireNonNull(config, "config");

        if (!config.layer2Enabled()) {
            return false;
        }
        if (cue.activity() == Activity.NORMAL) {
            return false;
        }
        if (cue.hasFlag(CueFlags.MUTED_SELF)) {
            return false; // see BillboardCueVisibility's Javadoc: defense in depth, normally unreachable.
        }
        return !config.isMuted(playerName);
    }
}
