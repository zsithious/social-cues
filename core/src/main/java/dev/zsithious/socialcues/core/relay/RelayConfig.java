package dev.zsithious.socialcues.core.relay;

import dev.zsithious.socialcues.core.protocol.ProtocolConstants;

/**
 * DESIGN.md §5/§8.7 — the relay-side tunables. All defaults come straight
 * from DESIGN.md's own numbers, not invented here: near radius mirrors the
 * P1 placeholder already used by {@code mcshared.network.ServerHandshake}
 * (48 blocks), {@code updateIntervalTicks} and the rate limit match §5's
 * "varsayılan 4 tick" / "≤4 CueUpdate/saniye/oyuncu", the global broadcast
 * floor matches "değişimde ve ≤1/saniye", and the packet size cap reuses
 * {@link ProtocolConstants#MAX_C2S_PACKET_SIZE} so there is exactly one
 * place that number is defined.
 */
public record RelayConfig(double nearRadius, int updateIntervalTicks, int maxUpdatesPerSecond,
                           long globalBroadcastMinIntervalMs, int maxPacketSize) {

    public RelayConfig {
        if (nearRadius <= 0) {
            throw new IllegalArgumentException("nearRadius must be > 0, was " + nearRadius);
        }
        if (updateIntervalTicks <= 0) {
            throw new IllegalArgumentException("updateIntervalTicks must be > 0, was " + updateIntervalTicks);
        }
        if (maxUpdatesPerSecond <= 0) {
            throw new IllegalArgumentException("maxUpdatesPerSecond must be > 0, was " + maxUpdatesPerSecond);
        }
        if (globalBroadcastMinIntervalMs < 0) {
            throw new IllegalArgumentException(
                    "globalBroadcastMinIntervalMs must be >= 0, was " + globalBroadcastMinIntervalMs);
        }
        if (maxPacketSize <= 0) {
            throw new IllegalArgumentException("maxPacketSize must be > 0, was " + maxPacketSize);
        }
    }

    public static RelayConfig defaults() {
        return new RelayConfig(48.0, 4, 4, 1000L, ProtocolConstants.MAX_C2S_PACKET_SIZE);
    }
}
