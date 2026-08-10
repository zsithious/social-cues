/**
 * DESIGN.md §7 P4b — bucketC's non-mixin render glue: Layer 1's actual
 * drawing code ({@link
 * dev.zsithious.socialcues.adapter.bucketbc.render.CueBillboardRenderer},
 * called from {@code adapter.bucketbc.mixin.PlayerEntityRendererMixin}) and
 * the small duck interface ({@link
 * dev.zsithious.socialcues.adapter.bucketbc.render.CueUuidHolder}) two classes
 * in {@code adapter.bucketbc.mixin} use to get a player id onto a render
 * state. Every actual "should this render, at what alpha, which icon"
 * decision lives in {@code core.client} (DESIGN.md §7's P4b task note §3.2) —
 * this package is glue only, reading Minecraft/Fabric API state and calling
 * into it.
 */
package dev.zsithious.socialcues.adapter.bucketbc.render;
