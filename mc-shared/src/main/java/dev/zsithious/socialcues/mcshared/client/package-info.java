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
 *
 * <p>P4b (DESIGN.md §7) adds one more piece, still MC-import-free itself:
 * {@link dev.zsithious.socialcues.mcshared.client.LocalCueState}, the local
 * player's own locally-observed cue (what {@code core.client.RemoteCueStore}
 * can never hold, backing Layer 1's {@code showOnSelf}). Rendering itself
 * needs no seam here: both layers are bucket-local mixins, which Fabric
 * Loader activates from each bucket's own {@code socialcues.mixins.json}
 * without this bucket-agnostic module having to bootstrap anything.
 */
package dev.zsithious.socialcues.mcshared.client;
