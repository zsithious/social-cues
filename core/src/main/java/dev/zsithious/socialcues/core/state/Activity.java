package dev.zsithious.socialcues.core.state;

/**
 * DESIGN.md §4 — a player's single dominant activity at any moment.
 * Exactly one value is ever active at a time; {@link #IN_SCREEN} carries
 * further detail via {@link ScreenKind}.
 */
public enum Activity {
    NORMAL,
    TYPING_CHAT,
    TYPING_COMMAND,
    TYPING_SIGN,
    TYPING_BOOK,
    IN_SCREEN,
    AFK,
    SPEAKING
}
