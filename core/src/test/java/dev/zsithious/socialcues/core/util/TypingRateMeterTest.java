package dev.zsithious.socialcues.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TypingRateMeterTest {

    @Test
    void startsAtZeroIntensity() {
        TypingRateMeter meter = new TypingRateMeter();
        assertEquals(0, meter.intensity(0L));
        assertEquals(0, meter.intensity(10_000L));
    }

    @Test
    void singleKeystrokeDoesNotYetRaiseIntensity() {
        // There is no interval to measure a rate from until a second event arrives.
        TypingRateMeter meter = new TypingRateMeter();
        meter.recordKeystroke(1000L);
        assertEquals(0, meter.intensity(1000L));
    }

    @Test
    void sustainedFastTypingSaturatesIntensity() {
        TypingRateMeter meter = new TypingRateMeter(); // default cap: 12 keys/sec
        long t = 0L;
        // 20 keys/sec, sustained for 7.5s (150 keystrokes) - well above the cap.
        for (int i = 0; i < 150; i++) {
            meter.recordKeystroke(t);
            t += 50L;
        }
        assertEquals(255, meter.intensity(t));
    }

    @Test
    void intensityIsMonotonicWhileTypingSteadily() {
        TypingRateMeter meter = new TypingRateMeter();
        long t = 0L;
        int previous = -1;
        for (int i = 0; i < 20; i++) {
            meter.recordKeystroke(t);
            int current = meter.intensity(t);
            assertTrue(current >= previous, "intensity should not drop while typing steadily fast");
            previous = current;
            t += 50L;
        }
    }

    @Test
    void intensityDecaysTowardsZeroWithoutFurtherInput() {
        TypingRateMeter meter = new TypingRateMeter();
        long t = 0L;
        for (int i = 0; i < 150; i++) {
            meter.recordKeystroke(t);
            t += 50L;
        }
        int atStop = meter.intensity(t);
        assertTrue(atStop > 0, "precondition: should be actively typing at t");

        int afterOneHalfLife = meter.intensity(t + TypingRateMeter.DEFAULT_HALF_LIFE_MILLIS);
        int afterManyHalfLives = meter.intensity(t + 20 * TypingRateMeter.DEFAULT_HALF_LIFE_MILLIS);

        assertTrue(afterOneHalfLife < atStop, "intensity must decay after typing stops");
        assertEquals(0, afterManyHalfLives, "intensity must fall to zero after long enough idle time");
    }

    @Test
    void intensityStaysWithinWireByteRangeForExtremeInputs() {
        TypingRateMeter meter = new TypingRateMeter();
        // Two keystrokes at the exact same timestamp must not divide by zero
        // or overflow the 0-255 range.
        meter.recordKeystroke(0L);
        meter.recordKeystroke(0L);
        int intensity = meter.intensity(0L);
        assertTrue(intensity >= 0 && intensity <= 255);
    }

    @Test
    void steadyStateRateConvergesToInstantaneousInterKeystrokeRate() {
        // With a fixed inter-keystroke interval, the EWMA's fixed point is the
        // instantaneous rate implied by that interval (1000/interval keys/sec) —
        // not half of it. Use a high cap so the result isn't clipped at 255,
        // to actually observe convergence rather than saturation.
        double intervalMs = 200.0; // 5 keys/sec
        double expectedInstantRate = 1000.0 / intervalMs;
        double maxKeysPerSecond = 50.0; // high enough that 5 keys/sec never saturates

        TypingRateMeter meter = new TypingRateMeter(maxKeysPerSecond, TypingRateMeter.DEFAULT_HALF_LIFE_MILLIS);
        long t = 0L;
        long lastKeystrokeT = 0L;
        for (int i = 0; i < 500; i++) { // 100s of sustained typing, many half-lives
            meter.recordKeystroke(t);
            lastKeystrokeT = t;
            t += (long) intervalMs;
        }

        // Query right at the last keystroke, not after it — otherwise the
        // elapsed gap since then decays the reading too, which is correct
        // behaviour for intensity() but not what this test is measuring.
        int expectedIntensity = (int) Math.round((expectedInstantRate / maxKeysPerSecond) * 255.0);
        int actualIntensity = meter.intensity(lastKeystrokeT);
        assertTrue(Math.abs(actualIntensity - expectedIntensity) <= 1,
                "expected intensity near " + expectedIntensity + " (rate -> instantRate), was " + actualIntensity);
    }

    @Test
    void constructorRejectsInvalidTuning() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TypingRateMeter(0.0, 1500L));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TypingRateMeter(12.0, 0L));
    }
}
