package dev.zsithious.socialcues.core.policy;

/**
 * DESIGN.md §5: "{@code afk-visibility} sunucu config'i bitlere şöyle
 * çevrilir: {@code off} → bit 3 kapalı; {@code nearby} → bit 3 açık, bit 7
 * kapalı; {@code all} → bit 3 ve bit 7 açık." This is the single source of
 * truth for that conversion — both {@link PolicyBits#of} and the Paper
 * config loader go through {@link #applyTo(int)} instead of hand-toggling
 * bits 3/7, so the mapping can't drift between the two call sites.
 */
public enum AfkVisibility {
    /** AFK state is never shared, near or global (clears bits 3 and 7). */
    OFF,
    /** AFK visible to nearby players only (bit 3 on, bit 7 off). */
    NEARBY,
    /** AFK visible near and in the global/tab-list tier (bits 3 and 7 on). */
    ALL;

    /** Sets/clears bits 3 ({@link PolicyBits#IDLE}) and 7 ({@link PolicyBits#GLOBAL_AFK}); leaves all other bits untouched. */
    public int applyTo(int policyBits) {
        int cleared = policyBits & ~(PolicyBits.IDLE | PolicyBits.GLOBAL_AFK);
        return switch (this) {
            case OFF -> cleared;
            case NEARBY -> cleared | PolicyBits.IDLE;
            case ALL -> cleared | PolicyBits.IDLE | PolicyBits.GLOBAL_AFK;
        };
    }

    /** Parses {@code config.yml}'s {@code afk-visibility: off|nearby|all}, case-insensitively. */
    public static AfkVisibility fromConfigString(String value) {
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "off" -> OFF;
            case "nearby" -> NEARBY;
            case "all" -> ALL;
            default -> throw new IllegalArgumentException(
                    "afk-visibility must be one of off|nearby|all, was: " + value);
        };
    }
}
