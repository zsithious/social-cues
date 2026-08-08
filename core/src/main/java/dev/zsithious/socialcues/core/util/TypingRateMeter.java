package dev.zsithious.socialcues.core.util;

/**
 * DESIGN.md §4/§6 — exponentially decaying estimate of typing speed,
 * expressed directly as the 0-255 wire intensity used by
 * {@code core.protocol.CueUpdate}. Never sees the actual keys pressed,
 * only "a keystroke happened at time T" — the text itself is never in
 * scope for this class or anything it calls.
 *
 * <p>Implementation: an EWMA over the instantaneous inter-keystroke rate,
 * with a half-life so both "typing sped up" and "typing stopped" fade in
 * smoothly rather than jumping.
 */
public final class TypingRateMeter {

    /** Keystrokes/second that maps to the top of the wire range (255). */
    public static final double DEFAULT_MAX_KEYS_PER_SECOND = 12.0;

    /** DESIGN.md §6: "EMA, yarı ömür ~1.5s". */
    public static final long DEFAULT_HALF_LIFE_MILLIS = 1500L;

    private final double maxKeysPerSecond;
    private final long halfLifeMillis;

    private double rate;
    private long lastEventMs = -1L;
    private long lastUpdateMs = -1L;

    public TypingRateMeter() {
        this(DEFAULT_MAX_KEYS_PER_SECOND, DEFAULT_HALF_LIFE_MILLIS);
    }

    public TypingRateMeter(double maxKeysPerSecond, long halfLifeMillis) {
        if (maxKeysPerSecond <= 0) {
            throw new IllegalArgumentException("maxKeysPerSecond must be > 0");
        }
        if (halfLifeMillis <= 0) {
            throw new IllegalArgumentException("halfLifeMillis must be > 0");
        }
        this.maxKeysPerSecond = maxKeysPerSecond;
        this.halfLifeMillis = halfLifeMillis;
    }

    /**
     * Records a keystroke at {@code nowMs}. Caller decides what counts as a keystroke.
     *
     * <p>The prior rate is decayed exactly once per call (via {@code alpha} below) —
     * {@link #intensity(long)} is the only other place that decays, and only for
     * the idle gap since the last update. Decaying twice for the same interval
     * would make the steady-state rate converge to roughly half the real
     * instantaneous rate instead of to the instantaneous rate itself.
     */
    public void recordKeystroke(long nowMs) {
        if (lastEventMs >= 0) {
            long interval = Math.max(1L, nowMs - lastEventMs);
            double instantRate = 1000.0 / interval;
            double alpha = decayFactor(Math.min(interval, halfLifeMillis));
            rate = rate * alpha + instantRate * (1 - alpha);
        }
        lastEventMs = nowMs;
        lastUpdateMs = nowMs;
    }

    /** Current intensity 0-255, decayed to {@code nowMs} even with no new keystroke. */
    public int intensity(long nowMs) {
        decayTo(nowMs);
        double scaled = (rate / maxKeysPerSecond) * 255.0;
        return (int) Math.round(Math.min(255.0, Math.max(0.0, scaled)));
    }

    private void decayTo(long nowMs) {
        if (lastUpdateMs < 0) {
            lastUpdateMs = nowMs;
            return;
        }
        long elapsed = nowMs - lastUpdateMs;
        if (elapsed <= 0) {
            return;
        }
        rate *= decayFactor(elapsed);
        lastUpdateMs = nowMs;
    }

    private double decayFactor(long elapsedMillis) {
        return Math.pow(0.5, elapsedMillis / (double) halfLifeMillis);
    }
}
