package dev.zsithious.socialcues.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdleTimerTest {

    private static final int THRESHOLD_TICKS = IdleTimer.DEFAULT_IDLE_THRESHOLD_TICKS; // 5 min

    @Test
    void ticksToMillisConversion() {
        assertEquals(1000L, IdleTimer.ticksToMillis(20));
        assertEquals(50L, IdleTimer.ticksToMillis(1));
        assertEquals(0L, IdleTimer.ticksToMillis(0));
        assertEquals(300_000L, IdleTimer.ticksToMillis(THRESHOLD_TICKS));
    }

    @Test
    void notAfkImmediatelyAfterActivity() {
        IdleTimer timer = new IdleTimer(0L);
        assertFalse(timer.isAfk(0L, THRESHOLD_TICKS));
        assertFalse(timer.isAfk(299_999L, THRESHOLD_TICKS));
    }

    @Test
    void becomesAfkExactlyAtThreshold() {
        IdleTimer timer = new IdleTimer(0L);
        long thresholdMillis = IdleTimer.ticksToMillis(THRESHOLD_TICKS);
        assertTrue(timer.isAfk(thresholdMillis, THRESHOLD_TICKS));
        assertFalse(timer.isAfk(thresholdMillis - 1, THRESHOLD_TICKS));
    }

    @Test
    void becomesSleepyAtTwiceTheThreshold() {
        IdleTimer timer = new IdleTimer(0L);
        long thresholdMillis = IdleTimer.ticksToMillis(THRESHOLD_TICKS);

        assertTrue(timer.isAfk(thresholdMillis, THRESHOLD_TICKS));
        assertFalse(timer.isSleepy(thresholdMillis, THRESHOLD_TICKS), "not sleepy yet at 1x threshold");

        assertTrue(timer.isSleepy(2 * thresholdMillis, THRESHOLD_TICKS));
        assertFalse(timer.isSleepy(2 * thresholdMillis - 1, THRESHOLD_TICKS));
    }

    @Test
    void recordActivityResetsTheClock() {
        IdleTimer timer = new IdleTimer(0L);
        long thresholdMillis = IdleTimer.ticksToMillis(THRESHOLD_TICKS);

        assertTrue(timer.isAfk(thresholdMillis, THRESHOLD_TICKS));

        timer.recordActivity(thresholdMillis);
        assertFalse(timer.isAfk(thresholdMillis, THRESHOLD_TICKS));
        assertFalse(timer.isAfk(thresholdMillis + thresholdMillis - 1, THRESHOLD_TICKS));
    }

    @Test
    void idleMillisNeverNegative() {
        IdleTimer timer = new IdleTimer(1000L);
        // A "now" before the recorded activity (clock skew) must not yield a negative idle time.
        assertEquals(0L, timer.idleMillis(500L));
    }

    @Test
    void zeroThresholdMeansImmediatelyAfk() {
        IdleTimer timer = new IdleTimer(0L);
        assertTrue(timer.isAfk(0L, 0));
        assertTrue(timer.isSleepy(0L, 0));
    }
}
