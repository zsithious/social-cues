package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md §6/§10.1: command-vs-chat is decided purely by the session's
 * first keystroke, never by reading the field's text. These are the ground
 * truth for that "first wins" rule.
 */
class CommandDraftDetectorTest {

    @Test
    void noKeystrokesYetIsNotACommandDraft() {
        CommandDraftDetector detector = new CommandDraftDetector();
        assertFalse(detector.isCommandDraft());
    }

    @Test
    void firstKeySlashIsACommandDraft() {
        CommandDraftDetector detector = new CommandDraftDetector();
        detector.onKeyPress(true);
        assertTrue(detector.isCommandDraft());
    }

    @Test
    void secondKeySlashAfterNonSlashFirstKeyStaysChat() {
        CommandDraftDetector detector = new CommandDraftDetector();
        detector.onKeyPress(false);
        detector.onKeyPress(true); // slash, but not first -> must not flip the verdict
        assertFalse(detector.isCommandDraft());
    }

    @Test
    void onlyTheFirstKeystrokeIsEverConsulted() {
        CommandDraftDetector detector = new CommandDraftDetector();
        detector.onKeyPress(true); // first key: slash -> locked in as a command draft
        detector.onKeyPress(false);
        detector.onKeyPress(false);
        assertTrue(detector.isCommandDraft(), "later non-slash keys must not undo the first key's verdict");
    }

    @Test
    void noKeyAtAllReportsChatNotCommand() {
        CommandDraftDetector detector = new CommandDraftDetector();
        // Session opened and closed (or never typed in) without a single
        // keystroke: must default to "chat", never "command".
        assertFalse(detector.isCommandDraft());
    }

    @Test
    void resetForgetsThePreviousSessionsVerdict() {
        CommandDraftDetector detector = new CommandDraftDetector();
        detector.onKeyPress(true);
        assertTrue(detector.isCommandDraft());

        detector.reset();
        assertFalse(detector.isCommandDraft(), "reset() must clear the verdict before a new first key arrives");

        detector.onKeyPress(false);
        assertFalse(detector.isCommandDraft());
    }

    @Test
    void resetThenSlashFirstKeyIsACommandDraftAgain() {
        CommandDraftDetector detector = new CommandDraftDetector();
        detector.onKeyPress(false);
        detector.reset();
        detector.onKeyPress(true);
        assertTrue(detector.isCommandDraft());
    }
}
