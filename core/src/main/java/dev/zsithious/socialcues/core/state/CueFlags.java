package dev.zsithious.socialcues.core.state;

/**
 * DESIGN.md §4 — bit flags packed into a single wire byte
 * (see core.protocol.CueUpdate / CueBatch.Entry).
 */
public interface CueFlags {
    int SNEAKING = 1;
    int REDUCED_DETAIL = 1 << 1;
    int SLEEPY = 1 << 2;
    int MUTED_SELF = 1 << 3;
}
