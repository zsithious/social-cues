package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §7 P5b — {@link FakeChatStream}'s pure maths: deterministic
 * gibberish that reads as "someone is typing, at about this speed" without
 * ever carrying real content. See the class Javadoc for why the alphabet is
 * letters-and-spaces-only and why the whole thing is a pure hash of
 * (player, line, column) rather than any kind of stored state.
 */
class FakeChatStreamTest {

    private static final Pattern ONLY_LOWERCASE_AND_SPACE = Pattern.compile("[a-z ]*");

    @Test
    void alwaysReturnsExactlyVisibleLinesEntries() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, 128);
        for (float seconds : new float[] {0f, 0.1f, 1f, 5f, 37.25f, 400f}) {
            List<String> lines = FakeChatStream.lines(cue, seconds);
            assertEquals(FakeChatStream.VISIBLE_LINES, lines.size(), "wrong line count at seconds=" + seconds);
        }
    }

    @Test
    void deterministicForTheSameCueAndTime() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, 200);
        List<String> first = FakeChatStream.lines(cue, 12.5f);
        List<String> second = FakeChatStream.lines(cue, 12.5f);
        assertEquals(first, second);

        assertEquals(FakeChatStream.caretColumn(cue, 12.5f), FakeChatStream.caretColumn(cue, 12.5f));
    }

    @Test
    void twoDifferentPlayersProduceDifferentGibberish() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<String> linesA = FakeChatStream.lines(cue(a, 200), 5f);
        List<String> linesB = FakeChatStream.lines(cue(b, 200), 5f);
        org.junit.jupiter.api.Assertions.assertNotEquals(linesA, linesB);
    }

    /**
     * DESIGN.md §7 P5b: "the in-progress line grows over time ... and wraps at
     * MAX_LINE_LENGTH". Sampling a growing window at max intensity (fastest
     * cadence) must show the in-progress line's length growing, and, once
     * enough time has passed, wrapping back down -- i.e. the length must not
     * simply increase forever.
     */
    @Test
    void inProgressLineGrowsOverTimeAndEventuallyWraps() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, 255);

        int previousLength = -1;
        boolean sawGrowth = false;
        boolean sawWrap = false;
        for (float seconds = 0f; seconds <= 10f; seconds += 0.05f) {
            List<String> lines = FakeChatStream.lines(cue, seconds);
            String inProgress = lines.get(lines.size() - 1);
            int length = inProgress.length();
            assertTrue(length <= FakeChatStream.MAX_LINE_LENGTH,
                    "in-progress line exceeded MAX_LINE_LENGTH at seconds=" + seconds);
            if (previousLength >= 0) {
                if (length > previousLength) {
                    sawGrowth = true;
                }
                if (length < previousLength) {
                    // A drop in length while time only moves forward can only mean a
                    // wrap happened (a new, shorter line started).
                    sawWrap = true;
                }
            }
            previousLength = length;
        }

        assertTrue(sawGrowth, "expected the in-progress line to grow at some point over 10s at max intensity");
        assertTrue(sawWrap, "expected the in-progress line to wrap at least once over 10s at max intensity");
    }

    @Test
    void noLineStartsOrEndsWithASpaceOrContainsADoubleSpace() {
        UUID id = UUID.randomUUID();
        for (int intensity : new int[] {0, 64, 128, 200, 255}) {
            PlayerCue cue = cue(id, intensity);
            for (float seconds = 0f; seconds <= 30f; seconds += 0.7f) {
                for (String line : FakeChatStream.lines(cue, seconds)) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    assertFalse(line.startsWith(" "), "line starts with a space: [" + line + "]");
                    assertFalse(line.endsWith(" "), "line ends with a space: [" + line + "]");
                    assertFalse(line.contains("  "), "line contains a double space: [" + line + "]");
                }
            }
        }
    }

    @Test
    void onlyLowercaseLettersAndSpacesEverAppear() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, 180);
        for (float seconds = 0f; seconds <= 25f; seconds += 0.6f) {
            for (String line : FakeChatStream.lines(cue, seconds)) {
                assertTrue(ONLY_LOWERCASE_AND_SPACE.matcher(line).matches(),
                        "line contains something other than [a-z ]: [" + line + "]");
            }
        }
    }

    /**
     * DESIGN.md §7 P5b: "higher intensity produces characters faster". Sampled
     * at a fixed instant early enough that neither cadence has wrapped to a
     * second line yet, so caretColumn directly reflects total characters typed.
     */
    @Test
    void higherIntensityProducesCharactersFaster() {
        UUID id = UUID.randomUUID();
        PlayerCue slow = cue(id, 0);
        PlayerCue fast = cue(id, 255);
        float seconds = 2f;

        int slowColumn = FakeChatStream.caretColumn(slow, seconds);
        int fastColumn = FakeChatStream.caretColumn(fast, seconds);

        assertTrue(fastColumn > slowColumn,
                "expected higher intensity to type more characters by t=" + seconds
                        + "s: slow=" + slowColumn + " fast=" + fastColumn);
    }

    @Test
    void caretColumnStaysInRange() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, 255);
        for (float seconds = 0f; seconds <= 20f; seconds += 0.13f) {
            int column = FakeChatStream.caretColumn(cue, seconds);
            assertTrue(column >= 0 && column < FakeChatStream.MAX_LINE_LENGTH,
                    "caretColumn out of range at seconds=" + seconds + ": " + column);
        }
    }

    @Test
    void negativeOrZeroTimeNeverThrowsAndStaysInRange() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, 128);
        for (float seconds : new float[] {0f, -1f, -1000f}) {
            List<String> lines = FakeChatStream.lines(cue, seconds);
            assertEquals(FakeChatStream.VISIBLE_LINES, lines.size());
            int column = FakeChatStream.caretColumn(cue, seconds);
            assertTrue(column >= 0 && column < FakeChatStream.MAX_LINE_LENGTH);
        }
    }

    private static PlayerCue cue(UUID id, int intensity) {
        return new PlayerCue(id, Activity.TYPING_CHAT, ScreenKind.UNKNOWN, intensity, 0, 0L);
    }
}
