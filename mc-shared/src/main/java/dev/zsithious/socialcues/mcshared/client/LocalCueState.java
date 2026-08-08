package dev.zsithious.socialcues.mcshared.client;

import java.util.Optional;

import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 P4b — the local player's own, locally-observed cue, kept
 * separately from {@link RemoteCueStoreHolder}. This is what backs Layer 1's
 * {@code showOnSelf} (DESIGN.md §7 "Kendi oyuncusu"): DESIGN.md §5's relay
 * never echoes a player's own state back to them ({@code core.relay.CueRelay}'s
 * {@code eligibleNearTargets}/{@code eligibleGlobalTargets} both skip
 * {@code target.equals(viewer)}), so {@code core.client.RemoteCueStore}
 * structurally never has an entry keyed by the local player's own id — there
 * is nothing to look up there for the self case, at all, ever. Showing your
 * own current cue on your own in-world model therefore has to come from
 * <em>this</em> device's own locally-observed state, not from anything the
 * server sent back.
 *
 * <p>Updated every tick by {@link ClientCueCapture#onClientTick} with the
 * <em>pre-policy-mask</em> activity/screen/intensity/flags — deliberately
 * unmasked, unlike everything {@code CueSampler} sends over the wire: policy
 * masking exists to protect what <em>other players</em> learn about you
 * (DESIGN.md §5's {@code policyBits}/{@code prefBits}), not to hide your own
 * true state from yourself. A server that disables sharing typing status to
 * others has no reason to also make your own client lie to you about whether
 * you are, in fact, typing.
 *
 * <p>Same static-holder shape as {@link RemoteCueStoreHolder}: a plain,
 * freely-constructible {@link PlayerCue} value with no Minecraft import of
 * its own, exposed through exactly one client-side access point.
 */
public final class LocalCueState {

    private static PlayerCue current;

    private LocalCueState() {
    }

    /** Replaces the currently known local cue. Called once per tick from {@link ClientCueCapture}. */
    public static void update(PlayerCue cue) {
        current = cue;
    }

    /** Empty before the first tick after joining, and again immediately after {@link #reset}. */
    public static Optional<PlayerCue> get() {
        return Optional.ofNullable(current);
    }

    /**
     * Forgets the locally-observed cue. Called alongside
     * {@link RemoteCueStoreHolder#get()}{@code .clear()} whenever the
     * handshake leaves {@code ACTIVE} or is renegotiated (see
     * {@code ClientHandshakeNetworking}) — a stale value from a previous
     * session must not linger into a new one, exactly like the remote store.
     */
    public static void reset() {
        current = null;
    }
}
