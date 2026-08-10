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
 *
 * <p><b>P6 §4.4 — split into a layer-agnostic core and Layer 1's own gate.</b>
 * Every rule above except the {@code layer1Enabled} check itself is not
 * actually specific to Layer 1: mute list, {@code MUTED_SELF}, self/
 * third-person, and max distance all apply just as much to Layer 3's held
 * panel and pose ({@code adapter.bucketd.render.CueScreenPanelRenderer},
 * {@code adapter.bucketd.mixin.PlayerEntityModelMixin}). Before this split,
 * {@link #shouldRender} folded {@code layer1Enabled} into the same method
 * every layer had to call to get the shared rules, so turning Layer 1 off
 * silently killed Layer 3's panel too — a coupling nobody asked for, just a
 * side effect of there being only one method to call. {@link
 * #passesSharedRules} is now that reusable core; {@link #shouldRender} is
 * {@code layer1Enabled() && passesSharedRules(...)} and nothing else, so
 * Layer 1's own observable behaviour is unchanged byte-for-byte. WP-B is the
 * one that actually moves Layer 3's callers over to {@link
 * #passesSharedRules} — this class only has to stop making that impossible.
 */
public final class BillboardCueVisibility {

    private BillboardCueVisibility() {
    }

    /**
     * Layer 1's own gate: {@code config.layer1Enabled()} first (cheapest
     * possible bail, DESIGN.md §3.5), then every rule {@link
     * #passesSharedRules} already covers. Behaviourally identical to this
     * method before the P6 §4.4 split — nothing about what Layer 1 shows or
     * hides has changed, only where the non-layer-specific rules now live.
     *
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
            return false; // Cheapest possible bail before any of the shared rules (DESIGN.md §3.5).
        }
        return passesSharedRules(cue, isSelf, thirdPersonCamera, distance, config, playerName);
    }

    /**
     * Every rule that is not about which layer is asking: {@link
     * Activity#NORMAL} (nothing to show), {@link CueFlags#MUTED_SELF}, the
     * local mute list, self/third-person, and max distance. P6 §4.4: this is
     * what {@link #shouldRender} delegates to after its own {@code
     * layer1Enabled} check, and what Layer 3's panel/pose callers are meant
     * to call directly instead of {@link #shouldRender} — they apply their
     * own {@code layer3Enabled} gate the same way this class applies {@code
     * layer1Enabled}, rather than inheriting Layer 1's.
     *
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
    public static boolean passesSharedRules(PlayerCue cue, boolean isSelf, boolean thirdPersonCamera,
            double distance, ClientConfigData config, String playerName) {
        Objects.requireNonNull(cue, "cue");
        Objects.requireNonNull(config, "config");

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
