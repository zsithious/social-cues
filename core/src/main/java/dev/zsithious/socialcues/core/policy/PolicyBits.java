package dev.zsithious.socialcues.core.policy;

/**
 * DESIGN.md §5 "Politika ve paylaşım bitleri" — the shared bit layout used
 * by both {@code ServerHello.policyBits} (what the server allows) and
 * {@code SharePrefs.prefBits} (what the client agrees to share). Both sides
 * of the handshake speak the same 8 bits, which is what makes
 * {@code policyBits AND prefBits} ("effective permission", see
 * {@link EffectivePolicy}) a meaningful operation.
 */
public interface PolicyBits {

    int TYPING = 1;
    int SCREENS = 1 << 1;
    int SCREEN_DETAIL = 1 << 2;
    int IDLE = 1 << 3;
    int VOICE = 1 << 4;
    int INTENSITY = 1 << 5;
    int GLOBAL_TIER = 1 << 6;
    int GLOBAL_AFK = 1 << 7;

    /** All 8 bits set — used as the default {@code SharePrefs} value before a client ever sends one. */
    int ALL = 0xFF;

    int NONE = 0;

    /**
     * Builds a policyBits/prefBits value from individually named switches,
     * so callers (Paper's config loader) never have to hand-assemble a raw
     * bitmask. {@code afkVisibility} alone controls {@link #IDLE} and
     * {@link #GLOBAL_AFK} — see {@link AfkVisibility#applyTo(int)} — because
     * those two bits together *are* the three-state off/nearby/all AFK
     * visibility policy described in DESIGN.md §5; a separate boolean for
     * either would just be a second, conflicting way to say the same thing.
     */
    static int of(boolean typing, boolean screens, boolean screenDetail, boolean voice,
                   boolean intensity, boolean globalTier, AfkVisibility afkVisibility) {
        int bits = NONE;
        if (typing) {
            bits |= TYPING;
        }
        if (screens) {
            bits |= SCREENS;
        }
        if (screenDetail) {
            bits |= SCREEN_DETAIL;
        }
        if (voice) {
            bits |= VOICE;
        }
        if (intensity) {
            bits |= INTENSITY;
        }
        if (globalTier) {
            bits |= GLOBAL_TIER;
        }
        return afkVisibility.applyTo(bits);
    }
}
