package dev.zsithious.socialcues.core.relay;

/**
 * Outcome of {@link CueRelay#ingest}. One enum covers all three C2S message
 * types plus the boundary violations DESIGN.md §5/§8.5 requires the relay to
 * police (size cap, rate limit, malformed input) — see {@link IngestOutcome}
 * for the accompanying violation-streak counter that a kick-threshold policy
 * would compare against.
 */
public enum IngestStatus {
    /** A {@code CueUpdate} was accepted and the player's stored cue was updated. */
    ACCEPTED,
    /** A {@code SharePrefs} was accepted and the player's prefBits were updated. */
    PREFS_UPDATED,
    /** A {@code ClientHello} was seen; the caller should reply with a {@code ServerHello}. */
    HELLO_RECEIVED,
    /** DESIGN.md §5: "oyuncu başına ≤4 CueUpdate/saniye; aşan paketler düşer." */
    RATE_LIMITED,
    /** DESIGN.md §5: "sunucu 64 bayt üstünü reddeder." */
    TOO_LARGE,
    /** Failed to decode, or decoded to something structurally invalid. */
    MALFORMED,
    /** {@code sender} has not (or no longer) {@link CueRelay#join joined} the relay. */
    UNKNOWN_SENDER
}
