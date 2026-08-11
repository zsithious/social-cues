/**
 * DESIGN.md §7 — bucket A's non-mixin render glue: Layer 1's billboard ({@link
 * dev.zsithious.socialcues.adapter.bucketa.render.CueBillboardRenderer}) and
 * Layer 3's held panel ({@link
 * dev.zsithious.socialcues.adapter.bucketa.render.CueScreenPanelRenderer}),
 * both called from {@code adapter.bucketa.mixin.PlayerEntityRendererMixin}'s
 * single hook.
 *
 * <p>Two classes, where buckets BC and D have three: {@code CueUuidHolder} has
 * no counterpart here. It exists on the newer rows only because a {@code
 * PlayerEntityRenderState} carries no player id and the render thread never
 * sees the entity again; on 1.21–1.21.1 the renderer is handed the entity
 * itself, so the id is simply read where it is used.
 *
 * <p>Every actual "should this render, at what alpha, which icon" decision
 * lives in {@code core.client} (DESIGN.md §7's P4b task note §3.2) — this
 * package is glue only, reading Minecraft state and calling into it.
 */
package dev.zsithious.socialcues.adapter.bucketa.render;
