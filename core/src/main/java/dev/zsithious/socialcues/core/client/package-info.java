/**
 * DESIGN.md §14 P3 "İstemci yakalama" — the platform-independent half of
 * client-side capture: a pure-Java table from a Minecraft
 * {@code ScreenHandlerType} registry id string to
 * {@link dev.zsithious.socialcues.core.state.ScreenKind}
 * ({@link dev.zsithious.socialcues.core.client.ScreenKindMapper}), the
 * single injectable seam for "what has the local player agreed to share"
 * ({@link dev.zsithious.socialcues.core.client.SharePrefsSource}), and the
 * change-detection/rate-limit/policy-masking decision of whether a
 * {@code CueUpdate} should be sent at all right now
 * ({@link dev.zsithious.socialcues.core.client.CueSampler}). No
 * Minecraft/Bukkit imports — the Fabric client only has to supply the
 * observed {@code Activity}/{@code ScreenKind}/intensity/flags each tick and
 * a registry id string; see {@code mcshared.client} for that glue.
 */
package dev.zsithious.socialcues.core.client;
