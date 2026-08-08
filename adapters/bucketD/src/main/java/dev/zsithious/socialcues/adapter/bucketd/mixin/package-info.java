/**
 * DESIGN.md §7 / §3.1 P4b — bucketD's mixins, referenced by name from
 * {@code adapters/bucketD/src/main/resources/socialcues.mixins.json} (its
 * {@code "package"} field points here). Three classes: {@link
 * dev.zsithious.socialcues.adapter.bucketd.mixin.PlayerEntityRenderStateMixin}
 * and {@link dev.zsithious.socialcues.adapter.bucketd.mixin.PlayerEntityRendererMixin}
 * together get a {@code UUID} onto a player render state (Layer 1 needs one,
 * vanilla render states don't carry one — see {@code CueUuidHolder}'s
 * Javadoc), and {@link dev.zsithious.socialcues.adapter.bucketd.mixin.PlayerListHudMixin}
 * draws Layer 2's tab list icon. All three are {@code "client"}-list-only in
 * the mixin config (never applied on a dedicated server) and every one
 * degrades to "draw nothing" rather than throwing on any unexpected input
 * (DESIGN.md §11).
 */
package dev.zsithious.socialcues.adapter.bucketd.mixin;
