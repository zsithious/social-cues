package dev.zsithious.socialcues.core.util;

/**
 * DESIGN.md §4/§6 — tracks time since last input (key/mouse/movement/look)
 * and classifies AFK / SLEEPY. "Ticks" here is just a unit (20/second),
 * not a Minecraft type — this stays pure logic on purpose.
 */
public final class IdleTimer {

    public static final int MC_TICKS_PER_SECOND = 20;

    /** DESIGN.md §6: default AFK threshold is 5 minutes. */
    public static final int DEFAULT_IDLE_THRESHOLD_TICKS = 5 * 60 * MC_TICKS_PER_SECOND;

    private long lastActivityMs;

    public IdleTimer(long startMs) {
        this.lastActivityMs = startMs;
    }

    public void recordActivity(long nowMs) {
        this.lastActivityMs = nowMs;
    }

    public long idleMillis(long nowMs) {
        return Math.max(0L, nowMs - lastActivityMs);
    }

    public boolean isAfk(long nowMs, int idleThresholdTicks) {
        return idleMillis(nowMs) >= ticksToMillis(idleThresholdTicks);
    }

    /** DESIGN.md §4: 2x the AFK threshold sets the SLEEPY flag. */
    public boolean isSleepy(long nowMs, int idleThresholdTicks) {
        return idleMillis(nowMs) >= 2L * ticksToMillis(idleThresholdTicks);
    }

    public static long ticksToMillis(int ticks) {
        return ticks * 1000L / MC_TICKS_PER_SECOND;
    }
}
