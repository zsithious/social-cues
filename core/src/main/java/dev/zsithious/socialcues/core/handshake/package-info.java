/**
 * DESIGN.md §5 "El sıkışma" — the platform-independent client handshake
 * state machine ({@link dev.zsithious.socialcues.core.handshake.ClientHandshake}).
 * Pure Java, no Minecraft/Bukkit imports, so it can be unit tested without
 * booting a client or server. See {@code mcshared.network} for the Fabric
 * adapter that drives it.
 */
package dev.zsithious.socialcues.core.handshake;
