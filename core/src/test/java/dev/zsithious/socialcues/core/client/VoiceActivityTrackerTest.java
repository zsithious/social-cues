package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md §6 "Konuşma" / §14 P8: the hold window that turns raw microphone
 * transmission into a steady {@code SPEAKING} cue.
 */
class VoiceActivityTrackerTest {

    private static final long T0 = 1_000_000L;

    @Test
    void reportsNothingBeforeAnyTransmission() {
        VoiceActivityTracker tracker = new VoiceActivityTracker();
        assertFalse(tracker.isSpeaking(T0));
        assertFalse(tracker.update(false, T0));
        // Far in the future is still "never transmitted", not "window expired".
        assertFalse(tracker.isSpeaking(T0 + 10_000_000L));
    }

    @Test
    void transmittingReportsSpeakingImmediately() {
        VoiceActivityTracker tracker = new VoiceActivityTracker();
        assertTrue(tracker.update(true, T0));
    }

    @Test
    void holdsThroughASentenceGapThenDrops() {
        VoiceActivityTracker tracker = new VoiceActivityTracker(2000L);
        tracker.update(true, T0);

        // Inside the window: still speaking, which is the whole point --
        // without this the icon would flicker on every gap between sentences.
        assertTrue(tracker.update(false, T0 + 1));
        assertTrue(tracker.update(false, T0 + 1999));

        // The boundary is exclusive: exactly holdMs later the hold is over.
        assertFalse(tracker.update(false, T0 + 2000));
        assertFalse(tracker.update(false, T0 + 5000));
    }

    @Test
    void eachTransmissionRestartsTheWindow() {
        VoiceActivityTracker tracker = new VoiceActivityTracker(2000L);
        tracker.update(true, T0);
        assertTrue(tracker.update(false, T0 + 1500));

        // Talking again re-anchors the window on the new sample, so the hold
        // runs 2000ms from *there*, not from the first transmission.
        assertTrue(tracker.update(true, T0 + 1800));
        assertTrue(tracker.update(false, T0 + 3700));
        assertFalse(tracker.update(false, T0 + 3800));
    }

    @Test
    void zeroHoldPassesTheRawSignalThrough() {
        VoiceActivityTracker tracker = new VoiceActivityTracker(0L);
        assertTrue(tracker.update(true, T0));
        assertFalse(tracker.update(false, T0));
    }

    @Test
    void clockGoingBackwardsDoesNotLatchSpeakingForever() {
        VoiceActivityTracker tracker = new VoiceActivityTracker(2000L);
        tracker.update(true, T0);

        // NTP steps the wall clock back an hour. A naive `now - last < hold`
        // would read the negative age as "inside the window" for that whole
        // hour; the tracker re-anchors instead...
        long stepped = T0 - 3_600_000L;
        assertTrue(tracker.isSpeaking(stepped));

        // ...so the hold expires normally from the corrected clock.
        assertTrue(tracker.update(false, stepped + 1999));
        assertFalse(tracker.update(false, stepped + 2000));
    }

    @Test
    void resetForgetsAHeldTransmission() {
        VoiceActivityTracker tracker = new VoiceActivityTracker(2000L);
        tracker.update(true, T0);
        assertTrue(tracker.isSpeaking(T0 + 500));

        tracker.reset();

        // The bridge detached mid-hold: the cue must drop at once rather than
        // linger for the rest of a window nothing is backing any more.
        assertFalse(tracker.isSpeaking(T0 + 500));
    }

    @Test
    void rejectsNegativeHold() {
        assertThrows(IllegalArgumentException.class, () -> new VoiceActivityTracker(-1L));
    }

    @Test
    void defaultHoldIsTwoSeconds() {
        assertEquals(2000L, VoiceActivityTracker.DEFAULT_HOLD_MS);
        VoiceActivityTracker tracker = new VoiceActivityTracker();
        tracker.update(true, T0);
        assertTrue(tracker.update(false, T0 + 1999));
        assertFalse(tracker.update(false, T0 + 2000));
    }
}
