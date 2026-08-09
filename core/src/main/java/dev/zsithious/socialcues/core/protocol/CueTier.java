package dev.zsithious.socialcues.core.protocol;

/**
 * DESIGN.md §5 — which of the two broadcast tiers a {@link CueBatch} carries:
 * {@link #NEAR} (full detail — {@code core.relay.CueRelay}'s near-tier
 * eligibility, consumed by Katman 1/3's world render) or {@link #GLOBAL}
 * (coarse, activity-only — the tab-list-facing Katman 2 tier,
 * {@code core.policy.EffectivePolicy#applyGlobalCoarse}'s output). Carried on
 * the wire as a single byte, ordinal-coded exactly like {@code Activity}/
 * {@code ScreenKind} (see {@link EnumCodec}).
 *
 * <p><b>Why this field exists (P5 hand-test bug, 2026-08-09):</b> before it
 * did, a {@link CueBatch} looked identical on the wire regardless of which
 * tier produced it, and {@code core.client.RemoteCueStore} stored every
 * incoming entry in one shared map keyed only by player id. The global
 * tier's coarse, ≤1/s entries — deliberately {@code ScreenKind.UNKNOWN},
 * intensity 0, flags 0 by design, see {@code EffectivePolicy#applyGlobalCoarse}
 * — would then silently overwrite a detailed near-tier entry for the same
 * player whenever the global broadcast happened to land after it, because
 * "last write wins" was the only rule a single {@code Map#put} loop could
 * express. That was the root cause of a hand-tested bug: a survival player's
 * held-panel render (Katman 3, near-tier-only content) would flash the
 * correct inventory GUI for a moment, then revert to a single-chest texture
 * about a second later, exactly the cadence of {@code
 * global-broadcast-min-interval-ms}. See {@code core.client.RemoteCueStore}'s
 * own Javadoc for the client-side half of the fix (two maps instead of one).
 *
 * <p>Ordinal-coded and append-only for the same reason as {@code ScreenKind}
 * (see that enum's own Javadoc): protocol v1, mod not yet released, so there
 * is no live compatibility concern yet, but "append only, never reorder" is
 * still the right habit to start with.
 */
public enum CueTier {
    NEAR,
    GLOBAL
}
