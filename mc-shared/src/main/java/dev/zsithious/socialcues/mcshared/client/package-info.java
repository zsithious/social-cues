/**
 * DESIGN.md §14 P3 "İstemci yakalama" — Fabric client-side capture: which
 * screen is open, whether a key was pressed, when the player last did
 * anything, and the current server policy ({@link
 * dev.zsithious.socialcues.mcshared.client.ServerPolicyState}). Every actual
 * decision (policy masking, change detection, rate limiting, the
 * registry-id-to-{@code ScreenKind} table) lives in {@code core.client} and
 * is unit tested there without Minecraft; {@link
 * dev.zsithious.socialcues.mcshared.client.ClientCueCapture} only reads
 * Minecraft/Fabric API state and feeds it in. Client-only — reachable solely
 * from {@code SocialCuesClientInitializer}, never from the common
 * entrypoint, so a dedicated server never links these classes.
 */
package dev.zsithious.socialcues.mcshared.client;
