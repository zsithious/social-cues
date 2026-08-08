package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** DESIGN.md §7 Katman 1 "Mesafeyle solma" — boundary behavior of the fade curve. */
class DistanceFadeTest {

    private static final double MAX = 32.0;

    @Test
    void atOrBeforeTheCameraIsFullyOpaque() {
        assertEquals(1.0, DistanceFade.alpha(0.0, MAX));
        assertEquals(1.0, DistanceFade.alpha(-5.0, MAX));
    }

    @Test
    void withinTheFadeStartFractionIsFullyOpaque() {
        double fadeStart = MAX * DistanceFade.FADE_START_FRACTION;
        assertEquals(1.0, DistanceFade.alpha(fadeStart - 0.01, MAX));
        assertEquals(1.0, DistanceFade.alpha(fadeStart, MAX));
    }

    @Test
    void atExactlyMaxDistanceIsFullyTransparent() {
        assertEquals(0.0, DistanceFade.alpha(MAX, MAX));
    }

    @Test
    void pastMaxDistanceIsFullyTransparent() {
        assertEquals(0.0, DistanceFade.alpha(MAX + 50.0, MAX));
    }

    @Test
    void betweenFadeStartAndMaxDistanceIsStrictlyBetween0And1() {
        double fadeStart = MAX * DistanceFade.FADE_START_FRACTION;
        double midpoint = (fadeStart + MAX) / 2.0;
        double alpha = DistanceFade.alpha(midpoint, MAX);
        assertTrue(alpha > 0.0 && alpha < 1.0, "expected a strictly intermediate alpha, was " + alpha);
        // The midpoint of the fade range should fade to (approximately) half opacity.
        assertEquals(0.5, alpha, 1e-9);
    }

    @Test
    void fadeIsMonotonicallyNonIncreasingWithDistance() {
        double previous = DistanceFade.alpha(0.0, MAX);
        for (double d = 1.0; d <= MAX + 5.0; d += 1.0) {
            double current = DistanceFade.alpha(d, MAX);
            assertTrue(current <= previous, "alpha increased with distance at d=" + d);
            previous = current;
        }
    }

    @Test
    void nonPositiveMaxDistanceIsAlwaysFullyTransparent() {
        assertEquals(0.0, DistanceFade.alpha(0.0, 0.0));
        assertEquals(0.0, DistanceFade.alpha(0.0, -10.0));
        assertEquals(0.0, DistanceFade.alpha(5.0, Double.NaN));
    }

    @Test
    void nanDistanceTreatedAsAtTheCamera() {
        assertEquals(1.0, DistanceFade.alpha(Double.NaN, MAX));
    }

    @Test
    void combinedAlphaMultipliesByOpacityAndClamps() {
        double fadeStart = MAX * DistanceFade.FADE_START_FRACTION;
        assertEquals(0.5, DistanceFade.combinedAlpha(fadeStart - 1.0, MAX, 0.5));
        assertEquals(0.0, DistanceFade.combinedAlpha(0.0, MAX, 0.0));
        // Defense in depth: an out-of-range opacity must not push the result out of [0,1].
        assertEquals(1.0, DistanceFade.combinedAlpha(0.0, MAX, 5.0));
        assertEquals(0.0, DistanceFade.combinedAlpha(0.0, MAX, -5.0));
    }
}
