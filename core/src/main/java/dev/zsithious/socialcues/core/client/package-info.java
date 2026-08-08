/**
 * DESIGN.md §14 P3/P4a/P4b "İstemci yakalama" ve render altyapısı — the
 * platform-independent half of everything the Minecraft-side client code
 * needs: a pure-Java table from a Minecraft {@code ScreenHandlerType}
 * registry id string to {@link dev.zsithious.socialcues.core.state.ScreenKind}
 * ({@link dev.zsithious.socialcues.core.client.ScreenKindMapper}), the
 * single injectable seam for "what has the local player agreed to share"
 * ({@link dev.zsithious.socialcues.core.client.SharePrefsSource}), the
 * change-detection/rate-limit/policy-masking decision of whether a
 * {@code CueUpdate} should be sent at all right now
 * ({@link dev.zsithious.socialcues.core.client.CueSampler}), the P4a store
 * that turns incoming {@code CueBatch}/{@code CueDrop} messages into the
 * {@code UUID -> PlayerCue} map P4b's render code reads
 * ({@link dev.zsithious.socialcues.core.client.RemoteCueStore}), the
 * render/privacy configuration model a future P6 config UI edits
 * ({@link dev.zsithious.socialcues.core.client.ClientConfigData}), and the
 * hand-drawn icon atlas' cell layout
 * ({@link dev.zsithious.socialcues.core.client.CueIconAtlas}), and its
 * {@code textOnly}-mode translation key counterpart
 * ({@link dev.zsithious.socialcues.core.client.CueLangKeys}). No
 * Minecraft/Bukkit imports — the Fabric client only has to supply the
 * observed {@code Activity}/{@code ScreenKind}/intensity/flags each tick, a
 * registry id string, and raw protocol messages; see {@code mcshared.client}
 * and {@code mcshared.config} for that glue.
 *
 * <p>P4b (DESIGN.md §7, Katman 1 + Katman 2 render) adds every MC-independent
 * render <em>decision</em>, so bucketD's adapter code is left with nothing to
 * decide, only to draw: {@link dev.zsithious.socialcues.core.client.CueDisplaySelector}
 * (which atlas cell / translation key a cue maps to, folding in the
 * AFK+SLEEPY variant), {@link dev.zsithious.socialcues.core.client.DistanceFade}
 * (Layer 1's distance-to-opacity curve), and the two layers' independent
 * "should anything render at all" gates,
 * {@link dev.zsithious.socialcues.core.client.BillboardCueVisibility} and
 * {@link dev.zsithious.socialcues.core.client.TabListCueVisibility}.
 */
package dev.zsithious.socialcues.core.client;
