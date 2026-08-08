/**
 * DESIGN.md §8's platform-independent relay
 * ({@link dev.zsithious.socialcues.core.relay.CueRelay}): the
 * {@code Map<UUID, PlayerCue>} state store, join/leave, the near/global
 * two-tier delta broadcast, rate limiting, and packet-size/malformed-input
 * policing. No Minecraft/Bukkit imports — the platform only has to supply a
 * {@link dev.zsithious.socialcues.core.relay.VisibilityChecker} and feed
 * lifecycle events and raw bytes in; see {@code paper.relay} (P2) for the
 * Bukkit adapter and DESIGN.md §8's "röle mantığı iki kez yazılmayacak".
 */
package dev.zsithious.socialcues.core.relay;
