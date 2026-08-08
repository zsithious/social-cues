package dev.zsithious.socialcues.mcshared.client;

import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.util.IdleTimer;

/**
 * The two {@code ServerHello} fields P3's capture logic needs
 * ({@code policyBits}, {@code idleThresholdTicks}; DESIGN.md §5), remembered
 * across ticks. Deliberately separate from {@code core.handshake.ClientHandshake}
 * (P1): that class owns only the dormant/active *transition* logic and knows
 * nothing about payload contents on purpose; this is the small piece of
 * Minecraft-side state that remembers what the last accepted
 * {@code ServerHello} actually said, updated by {@link ClientHandshakeNetworking}'s
 * receiver.
 *
 * <p>Values are meaningless before the handshake is
 * {@code HandshakeState.ACTIVE} — callers must gate on
 * {@code ClientHandshakeNetworking.isActive()} first, exactly like the rest
 * of P3's capture logic. Only ever touched from the client thread (Fabric
 * fires connection/tick events on it), matching the concurrency assumption
 * every other piece of this mod's Minecraft-side state already makes (see
 * {@code core.relay.CueRelay}'s Javadoc) — no synchronization needed.
 */
public final class ServerPolicyState {

    private static int policyBits = PolicyBits.ALL;
    private static int idleThresholdTicks = IdleTimer.DEFAULT_IDLE_THRESHOLD_TICKS;

    private ServerPolicyState() {
    }

    public static void update(int newPolicyBits, int newIdleThresholdTicks) {
        policyBits = newPolicyBits;
        idleThresholdTicks = newIdleThresholdTicks;
    }

    /** Back to defaults on disconnect, so a stale value from a previous server can never leak into a new session. */
    public static void reset() {
        policyBits = PolicyBits.ALL;
        idleThresholdTicks = IdleTimer.DEFAULT_IDLE_THRESHOLD_TICKS;
    }

    public static int policyBits() {
        return policyBits;
    }

    public static int idleThresholdTicks() {
        return idleThresholdTicks;
    }
}
