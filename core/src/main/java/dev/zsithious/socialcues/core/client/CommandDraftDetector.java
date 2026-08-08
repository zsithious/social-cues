package dev.zsithious.socialcues.core.client;

/**
 * DESIGN.md §6/§10.1: whether an open chat session is drafting a command
 * ({@code TYPING_COMMAND}) or an ordinary message ({@code TYPING_CHAT}),
 * decided purely from whether the session's very first keystroke was the
 * slash key — <b>never</b> from the chat field's text.
 *
 * <p>An earlier version of this decision called
 * {@code TextFieldWidget.getText()} to peek at the field's first character.
 * That materializes the player's entire in-progress message as a Java
 * {@code String} on every keystroke (this runs up to ~20 times/second while
 * typing) — "only look at the first character" is not something the API
 * actually offers; the whole string exists in memory regardless of how
 * little of it is used afterward. That is exactly what DESIGN.md §10.1's
 * headline guarantee ("yazılan metin hiçbir zaman okunmaz") rules out, so
 * this class exists to make the correct approach a pure, unit-testable
 * decision: one boolean in, decided once per session, never a string.
 * {@code mcshared.client.ClientCueCapture} is the only caller — it feeds in
 * {@code key.key() == GLFW_KEY_SLASH} for each
 * {@code ScreenKeyboardEvents.afterKeyPress} while a {@code ChatScreen} is
 * open, and {@code mc-shared}'s {@code checkNoTextAccess} Gradle task
 * mechanically forbids any {@code getText()}/{@code getMessage()}/
 * {@code chatField}/{@code originalChatText} occurrence from creeping back
 * in there.
 *
 * <p>A chat opened pre-filled with {@code "/"} by a keybinding (rather than
 * typed) cannot be distinguished this way and is reported as
 * {@code TYPING_CHAT} — an accepted, purely cosmetic gap, not a compromise
 * on the "never read" guarantee.
 */
public final class CommandDraftDetector {

    private boolean firstKeySeen;
    private boolean firstKeyWasSlash;

    /**
     * Call once per keystroke while the chat session this instance tracks is
     * open. Every keystroke after the first is a no-op — the verdict is
     * locked in by the first one, matching "sadece ilk karaktere bakılabilir".
     *
     * @param isSlashKey whether this keystroke's keycode was the slash key
     */
    public void onKeyPress(boolean isSlashKey) {
        if (!firstKeySeen) {
            firstKeySeen = true;
            firstKeyWasSlash = isSlashKey;
        }
    }

    /**
     * @return {@code true} once the session's first keystroke was the slash
     *         key; {@code false} before any keystroke has been observed, or
     *         once the first one turned out not to be slash.
     */
    public boolean isCommandDraft() {
        return firstKeyWasSlash;
    }

    /** Forgets everything, for a freshly (re)opened chat session. */
    public void reset() {
        firstKeySeen = false;
        firstKeyWasSlash = false;
    }
}
