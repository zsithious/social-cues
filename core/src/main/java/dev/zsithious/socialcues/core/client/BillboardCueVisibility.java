package dev.zsithious.socialcues.core.client;

import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 Katman 1 / P4b task note §3.2 — "Bir gösterge gösterilecek
 * mi": layer toggle, {@code showOnSelf}, a locally muted player,
 * {@link Activity#NORMAL} (nothing to show), {@link CueFlags#MUTED_SELF} /
 * {@link CueFlags#REDUCED_DETAIL}, and {@code distance > maxDistance}. Pure
 * Java so every rule is independently unit tested; the adapter's only job is
 * gathering the inputs (an entity's distance to camera, whether the local
 * camera is currently third-person, the target's current Mojang username)
 * and calling {@link #shouldRender}.
 *
 * <p><b>Where {@code cue} comes from for the local player</b> (DESIGN.md §7's
 * "Kendi oyuncusu" / P4b uygulama notu): {@code core.client.RemoteCueStore}
 * structurally never contains the local player's own id — DESIGN.md §5's
 * relay skips the viewer itself when building both the near and global tier
 * ({@code core.relay.CueRelay}'s {@code target.equals(viewer)} guard in both
 * {@code eligibleNearTargets} and {@code eligibleGlobalTargets}), so there is
 * nothing to look up there for {@code isSelf == true}. The adapter is
 * expected to source that case from a locally-observed cue instead (see
 * {@code mcshared.client.LocalCueState}) — this class only decides visibility
 * once given whichever {@link PlayerCue} the caller resolved.
 */
public final class BillboardCueVisibility {

    private BillboardCueVisibility() {
    }

    /**
     * @param cue               the cue to evaluate (remote store entry, or
     *                          the local player's own locally-observed cue
     *                          when {@code isSelf} is true)
     * @param isSelf            whether {@code cue} belongs to the local player
     * @param thirdPersonCamera whether the local camera is currently
     *                          third-person; irrelevant unless {@code isSelf}
     * @param distance          distance in blocks from the camera to the
     *                          target entity
     * @param config            the loaded client config
     * @param playerName        the target's current Mojang username, for
     *                          {@link ClientConfigData#isMuted}
     */
    public static boolean shouldRender(PlayerCue cue, boolean isSelf, boolean thirdPersonCamera,
            double distance, ClientConfigData config, String playerName) {
        Objects.requireNonNull(cue, "cue");
        Objects.requireNonNull(config, "config");

        if (!config.layer1Enabled()) {
            return false;
        }
        if (cue.activity() == Activity.NORMAL) {
            return false; // DESIGN.md §4: nothing unusual to indicate.
        }
        if (cue.hasFlag(CueFlags.MUTED_SELF)) {
            // Defense in depth: the relay is already supposed to have neutralized a
            // MUTED_SELF sender's cue to NORMAL before it ever reaches the wire
            // (core.policy.EffectivePolicy#applyNear), so this should be unreachable
            // in practice — checked anyway rather than trusted, matching this
            // project's "the relay/store never fully trusts an upstream layer to have
            // enforced a rule" pattern elsewhere (e.g. RemoteCueStore's own Javadoc).
            return false;
        }
        if (config.isMuted(playerName)) {
            return false;
        }
        if (isSelf) {
            if (!config.showOnSelf()) {
                return false; // DESIGN.md §7 "Kendi oyuncusu": default off.
            }
            if (!thirdPersonCamera) {
                return false; // DESIGN.md §7: even when enabled, only in 3rd person.
            }
        }
        // CueFlags.REDUCED_DETAIL ("gönderen sadece kaba durum izni verdi", DESIGN.md
        // §4) is deliberately NOT checked here. Layer 1 only ever displays
        // Activity-level detail — CueIconAtlas has no per-ScreenKind cells, and
        // CueDisplaySelector never reads ScreenKind — so a reduced-detail cue renders
        // identically to a full-detail one carrying the same Activity: the flag has
        // no observable effect on this layer. See BillboardCueVisibilityTest for the
        // explicit case documenting this is intentional, not an oversight.
        return !(distance > config.maxDistance());
    }
}
