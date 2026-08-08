package dev.zsithious.socialcues.core.relay;

import java.util.Objects;

/**
 * Result of one {@link CueRelay#ingest} call. {@code violationStreak} counts
 * consecutive {@link IngestStatus#RATE_LIMITED}/{@link IngestStatus#TOO_LARGE}/
 * {@link IngestStatus#MALFORMED} results for that sender since their last
 * accepted message; it resets to 0 on any non-violation outcome. DESIGN.md
 * §8.7 mentions a configurable "kick eşiği" (kick threshold) — comparing
 * this streak against that threshold is exactly what an adapter (Paper's
 * plugin message listener) is expected to do; the relay only counts, it
 * never kicks anyone itself (no platform access from {@code core}).
 */
public record IngestOutcome(IngestStatus status, int violationStreak) {

    public IngestOutcome {
        Objects.requireNonNull(status, "status");
        if (violationStreak < 0) {
            throw new IllegalArgumentException("violationStreak must be >= 0, was " + violationStreak);
        }
    }

    public boolean isViolation() {
        return status == IngestStatus.RATE_LIMITED
                || status == IngestStatus.TOO_LARGE
                || status == IngestStatus.MALFORMED;
    }
}
