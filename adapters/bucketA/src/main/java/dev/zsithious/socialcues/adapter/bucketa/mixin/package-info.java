/**
 * DESIGN.md §7 / §3.1 — bucket A's mixins, referenced by name from
 * {@code adapters/bucketA/src/main/resources/socialcues.mixins.json} (its
 * {@code "package"} field points here).
 *
 * <p>Three classes, where buckets BC and D have four: {@link
 * dev.zsithious.socialcues.adapter.bucketa.mixin.PlayerEntityRendererMixin}
 * hosts Layers 1 and 3's world rendering, {@link
 * dev.zsithious.socialcues.adapter.bucketa.mixin.PlayerEntityModelMixin}
 * applies Layer 3's pose, and {@link
 * dev.zsithious.socialcues.adapter.bucketa.mixin.PlayerListHudMixin} draws
 * Layer 2's tab list icon. The missing fourth is the render-state mixin the
 * newer buckets need to smuggle a player id onto a {@code
 * PlayerEntityRenderState}: before 1.21.2 there are no render states, the live
 * entity is right there, and the whole {@code CueUuidHolder} arrangement
 * dissolves — see {@code PlayerEntityRendererMixin}'s Javadoc.
 *
 * <p>All three are {@code "client"}-list-only in the mixin config (never
 * applied on a dedicated server) and every one degrades to "draw nothing"
 * rather than throwing on any unexpected input (DESIGN.md §11).
 */
package dev.zsithious.socialcues.adapter.bucketa.mixin;
