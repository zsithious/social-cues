/**
 * DESIGN.md §3/§6 — MC-touching code that compiles identically across all
 * 12 target versions (network registration, config I/O, tick logic).
 * P1 (DESIGN.md §14): CustomPayload registration and the handshake/dormant
 * behaviour live in the {@code network} subpackage; this package holds the
 * two Fabric entrypoints ({@link
 * dev.zsithious.socialcues.mcshared.SocialCuesInitializer} for common code,
 * {@link dev.zsithious.socialcues.mcshared.SocialCuesClientInitializer} for
 * client-only code).
 */
package dev.zsithious.socialcues.mcshared;
