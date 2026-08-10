/**
 * DESIGN.md §7 / §3.1 P4b — bucketC's mixins, referenced by name from
 * {@code adapters/bucketC/src/main/resources/socialcues.mixins.json} (its
 * {@code "package"} field points here). Three classes: {@link
 * dev.zsithious.socialcues.adapter.bucketbc.mixin.PlayerEntityRenderStateMixin}
 * and {@link dev.zsithious.socialcues.adapter.bucketbc.mixin.PlayerEntityRendererMixin}
 * together get a {@code UUID} onto a player render state (Layer 1 needs one,
 * vanilla render states don't carry one — see {@code CueUuidHolder}'s
 * Javadoc), and {@link dev.zsithious.socialcues.adapter.bucketbc.mixin.PlayerListHudMixin}
 * draws Layer 2's tab list icon. All three are {@code "client"}-list-only in
 * the mixin config (never applied on a dedicated server) and every one
 * degrades to "draw nothing" rather than throwing on any unexpected input
 * (DESIGN.md §11).
 */
package dev.zsithious.socialcues.adapter.bucketbc.mixin;
