package dev.zsithious.socialcues.core.client;

/**
 * DESIGN.md §7 Katman 1 "Mesafeyle solma, config'te maksimum mesafe ve ölçek"
 * — the distance-to-opacity curve for the billboard layer, entirely as pure
 * Java arithmetic so the boundary behavior (0 distance, exactly at
 * {@code maxDistance}, past it, a degenerate {@code maxDistance}) is JUnit
 * tested without booting Minecraft.
 *
 * <p><b>Curve shape</b> (an independent engineering decision — DESIGN.md
 * only asks for "solma", not a specific curve): full opacity from the camera
 * out to {@link #FADE_START_FRACTION} of {@code maxDistance}, then a linear
 * ramp down to fully transparent exactly at {@code maxDistance}. A hard cutoff
 * at {@code maxDistance} (no fade at all) would make indicators pop in/out
 * abruptly as players move; fading over the whole range would make close-up
 * indicators dimmer than necessary. Splitting the two keeps indicators fully
 * legible for players who are actually near while still giving the
 * "about to disappear" cue a smooth transition instead of a hard edge.
 */
public final class DistanceFade {

    /**
     * Fraction of {@code maxDistance} at which fading begins; strictly
     * inside {@code (0, 1)} so both the "always full opacity" and "fade over
     * the whole range" degenerate cases are avoidable by construction.
     */
    public static final double FADE_START_FRACTION = 0.75;

    private DistanceFade() {
    }

    /**
     * @return 1.0 at {@code distance <= 0}, linearly down to 0.0 at
     *         {@code distance >= maxDistance}, 1.0 for everything closer than
     *         {@link #FADE_START_FRACTION} of {@code maxDistance}. A
     *         non-positive or {@code NaN} {@code maxDistance} always yields
     *         0.0 (nothing to show without a valid range); a {@code NaN}
     *         {@code distance} is treated as "at the camera" (1.0), the safer
     *         default for a value that should never actually be {@code NaN}
     *         in practice (a squared-distance computation gone wrong should
     *         not also make indicators silently invisible).
     */
    public static double alpha(double distance, double maxDistance) {
        if (Double.isNaN(maxDistance) || maxDistance <= 0.0) {
            return 0.0;
        }
        if (Double.isNaN(distance) || distance <= 0.0) {
            return 1.0;
        }
        if (distance >= maxDistance) {
            return 0.0;
        }
        double fadeStart = maxDistance * FADE_START_FRACTION;
        if (distance <= fadeStart) {
            return 1.0;
        }
        double span = maxDistance - fadeStart;
        double t = (distance - fadeStart) / span;
        return 1.0 - clamp01(t);
    }

    /**
     * {@link #alpha(double, double)} multiplied by the user's own
     * {@link ClientConfigData#opacity()} setting (DESIGN.md §9), clamped back
     * into {@code [0, 1]} in case a future caller passes an
     * already-out-of-range opacity (defense in depth — {@link ClientConfigData}
     * already clamps {@code opacity} itself, so this is normally a no-op).
     */
    public static double combinedAlpha(double distance, double maxDistance, double opacity) {
        return clamp01(alpha(distance, maxDistance) * opacity);
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
