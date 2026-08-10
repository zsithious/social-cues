package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §7 Katman 1 — {@link CueIconMotion}'s pure maths: the sleep
 * icon's idle bob/tilt, deliberately independent of Layer 3 (see the class
 * Javadoc — this has to keep working with the pose layer switched off).
 */
class CueIconMotionTest {

    // Generous, not tight: proving "bounded" does not require pinning down
    // CueIconMotion's private amplitude constants exactly, just that they
    // cannot blow up.
    private static final float BOB_BOUND_BLOCKS = 0.15f;
    private static final float TILT_BOUND_RADIANS = 0.5f;

    @Test
    void zeroForEveryNonAfkActivity() {
        UUID id = UUID.randomUUID();
        for (Activity activity : Activity.values()) {
            if (activity == Activity.AFK) {
                continue;
            }
            PlayerCue cue = cue(id, activity);
            for (float seconds = 0f; seconds <= 6f; seconds += 0.7f) {
                float ageTicks = seconds * 20f;
                assertEquals(0f, CueIconMotion.bobBlocks(cue, ageTicks, false), 0f,
                        "expected zero bob for " + activity + " at t=" + seconds);
                assertEquals(0f, CueIconMotion.tiltRadians(cue, ageTicks, false), 0f,
                        "expected zero tilt for " + activity + " at t=" + seconds);
            }
        }
    }

    @Test
    void nonZeroAndBoundedForAfk() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.AFK);

        boolean sawNonZeroBob = false;
        boolean sawNonZeroTilt = false;
        for (float seconds = 0f; seconds <= 20f; seconds += 0.1f) {
            float ageTicks = seconds * 20f;
            float bob = CueIconMotion.bobBlocks(cue, ageTicks, false);
            float tilt = CueIconMotion.tiltRadians(cue, ageTicks, false);

            assertTrue(Math.abs(bob) <= BOB_BOUND_BLOCKS, "bob out of bounds at t=" + seconds + ": " + bob);
            assertTrue(Math.abs(tilt) <= TILT_BOUND_RADIANS, "tilt out of bounds at t=" + seconds + ": " + tilt);

            if (bob != 0f) {
                sawNonZeroBob = true;
            }
            if (tilt != 0f) {
                sawNonZeroTilt = true;
            }
        }

        assertTrue(sawNonZeroBob, "expected AFK's bob to be nonzero at some point in a 20s window");
        assertTrue(sawNonZeroTilt, "expected AFK's tilt to be nonzero at some point in a 20s window");
    }

    @Test
    void deterministicForTheSamePlayerAndInstant() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.AFK);
        float ageTicks = 733.25f;

        assertEquals(CueIconMotion.bobBlocks(cue, ageTicks, false), CueIconMotion.bobBlocks(cue, ageTicks, false), 0f);
        assertEquals(CueIconMotion.tiltRadians(cue, ageTicks, false), CueIconMotion.tiltRadians(cue, ageTicks, false), 0f);
    }

    /**
     * DESIGN.md §7 Katman 1: "a row of sleeping players does not bob in
     * unison" — two fixed, distinct UUIDs (not random, so this is never
     * flaky) must land on different phases and therefore diverge somewhere
     * across a sampled window.
     */
    @Test
    void twoDifferentPlayersAreOutOfPhase() {
        // Deliberately not a "111...1"/"222...2"-style pattern: java.util.UUID's
        // hashCode() XORs the most- and least-significant 64 bits together, so a
        // UUID whose two halves repeat the same nibble collapses to hashCode() ==
        // 0 regardless of which digit -- both fixed UUIDs below have distinct
        // halves specifically so the two phases they produce actually differ.
        UUID a = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6");
        UUID b = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
        PlayerCue cueA = cue(a, Activity.AFK);
        PlayerCue cueB = cue(b, Activity.AFK);

        boolean sawBobDifference = false;
        boolean sawTiltDifference = false;
        for (float seconds = 0f; seconds <= 20f; seconds += 0.1f) {
            float ageTicks = seconds * 20f;
            if (Math.abs(CueIconMotion.bobBlocks(cueA, ageTicks, false) - CueIconMotion.bobBlocks(cueB, ageTicks, false)) > 1e-4f) {
                sawBobDifference = true;
            }
            if (Math.abs(CueIconMotion.tiltRadians(cueA, ageTicks, false) - CueIconMotion.tiltRadians(cueB, ageTicks, false)) > 1e-4f) {
                sawTiltDifference = true;
            }
        }
        assertTrue(sawBobDifference, "expected two different players' sleep bob to diverge somewhere in a 20s window");
        assertTrue(sawTiltDifference, "expected two different players' sleep tilt to diverge somewhere in a 20s window");
    }

    /**
     * P6 §4.1: {@code reducedMotion} must zero the sine term entirely, not
     * merely dampen it — AFK is the one activity that is otherwise nonzero
     * (see {@link #nonZeroAndBoundedForAfk}), so it is the only case that can
     * actually distinguish "zeroed" from "coincidentally near zero".
     */
    @Test
    void reducedMotionIsAlwaysZeroEvenForAfk() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.AFK);
        for (float seconds = 0f; seconds <= 20f; seconds += 0.37f) {
            float ageTicks = seconds * 20f;
            assertEquals(0f, CueIconMotion.bobBlocks(cue, ageTicks, true), 0f,
                    "expected zero bob under reducedMotion at t=" + seconds);
            assertEquals(0f, CueIconMotion.tiltRadians(cue, ageTicks, true), 0f,
                    "expected zero tilt under reducedMotion at t=" + seconds);
        }
    }

    private static PlayerCue cue(UUID id, Activity activity) {
        return new PlayerCue(id, activity, ScreenKind.UNKNOWN, 0, 0, 0L);
    }
}
