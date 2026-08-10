package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.client.PoseFrame.Limb;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §7 Katman 3, P5a/P5b — {@link PoseAnimator}'s pure maths. No
 * Minecraft involved, so this is the part of Layer 3 that can (and must) be
 * tested precisely rather than by hand in-game.
 *
 * <p>Rewritten against the P5b {@link PoseFrame} shape ({@code Limb}-grouped
 * fields, {@code screenRise}, pseudo-random per-hand typing, asymmetric
 * in-screen holding/working arms, body sway on idle) — the previous version
 * of this file was written against the flat pre-P5b record and the old
 * opposite-phase sine typing motion, neither of which exist anymore.
 */
class PoseAnimatorTest {

    private static final float TICKS_PER_SECOND = 20f;

    // ------------------------------------------------------------- weight / identity

    @Test
    void weightZeroReturnsExactlyNoneForEveryActivity() {
        UUID id = UUID.randomUUID();
        for (Activity activity : Activity.values()) {
            PlayerCue cue = cue(id, activity, 128, CueFlags.SLEEPY);
            assertEquals(PoseFrame.NONE, PoseAnimator.frameFor(cue, 37f, 0f, false));
            // Negative weight clamps to 0 too (frameFor's own documented contract).
            assertEquals(PoseFrame.NONE, PoseAnimator.frameFor(cue, 37f, -5f, false));
        }
    }

    @Test
    void normalAndSpeakingAlwaysReturnNoneRegardlessOfWeight() {
        UUID id = UUID.randomUUID();
        for (Activity activity : new Activity[] {Activity.NORMAL, Activity.SPEAKING}) {
            PlayerCue cue = cue(id, activity, 255, 0);
            for (float w : new float[] {0f, 0.001f, 0.25f, 0.5f, 1f, 2f}) {
                assertEquals(PoseFrame.NONE, PoseAnimator.frameFor(cue, 123.4f, w, false),
                        "activity=" + activity + " weight=" + w);
            }
        }
    }

    @Test
    void isIdentityAgreesWithWeightZeroAndWithNone() {
        UUID id = UUID.randomUUID();
        assertTrue(PoseFrame.NONE.isIdentity());

        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 200, 0);
        assertTrue(PoseAnimator.frameFor(typing, 10f, 0f, false).isIdentity());
        // A cue that genuinely has a pose at weight > 0 must NOT read as identity
        // (otherwise a renderer would wrongly skip drawing it).
        assertFalse(PoseAnimator.frameFor(typing, 10f, 1f, false).isIdentity());

        PlayerCue afk = cue(id, Activity.AFK, 0, 0);
        assertFalse(PoseAnimator.frameFor(afk, 10f, 1f, false).isIdentity());
    }

    // ------------------------------------------------------------------ scaling

    @Test
    void everyFieldScalesLinearlyWithWeight() {
        UUID id = UUID.randomUUID();
        float ageTicks = 53.25f;
        assertScalesLinearly(cue(id, Activity.TYPING_CHAT, 190, 0), ageTicks);
        assertScalesLinearly(cue(id, Activity.IN_SCREEN, 0, 0), ageTicks);
        assertScalesLinearly(cue(id, Activity.AFK, 0, CueFlags.SLEEPY), ageTicks);
    }

    private static void assertScalesLinearly(PlayerCue cue, float ageTicks) {
        float w1 = 0.5f;
        float w2 = 1.0f;
        double ratio = w2 / w1;

        PoseFrame f1 = PoseAnimator.frameFor(cue, ageTicks, w1, false);
        PoseFrame f2 = PoseAnimator.frameFor(cue, ageTicks, w2, false);

        assertLimbScaled(f1.rightArm(), f2.rightArm(), ratio, "rightArm");
        assertLimbScaled(f1.leftArm(), f2.leftArm(), ratio, "leftArm");
        assertLimbScaled(f1.head(), f2.head(), ratio, "head");
        assertLimbScaled(f1.body(), f2.body(), ratio, "body");
        assertScaled(f1.screenWeight(), f2.screenWeight(), ratio, "screenWeight");
        assertScaled(f1.screenTilt(), f2.screenTilt(), ratio, "screenTilt");
        // screenRise is a position (how high the panel sits), not an intensity --
        // PoseAnimator.scale() deliberately leaves it unscaled by weight, unlike
        // every other field here. See screenRiseDoesNotScaleWithWeight below.
    }

    private static void assertLimbScaled(Limb l1, Limb l2, double ratio, String name) {
        assertScaled(l1.pitch(), l2.pitch(), ratio, name + ".pitch");
        assertScaled(l1.yaw(), l2.yaw(), ratio, name + ".yaw");
        assertScaled(l1.roll(), l2.roll(), ratio, name + ".roll");
    }

    private static void assertScaled(float atW1, float atW2, double ratio, String field) {
        double expected = atW1 * ratio;
        assertEquals(expected, atW2, 1e-4, field + " did not scale linearly with weight");
    }

    @Test
    void screenRiseDoesNotScaleWithWeightItIsAPositionNotAnIntensity() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 128, 0);
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN, 0, 0);
        float ageTicks = 17f;

        float typingRiseLow = PoseAnimator.frameFor(typing, ageTicks, 0.2f, false).screenRise();
        float typingRiseHigh = PoseAnimator.frameFor(typing, ageTicks, 1f, false).screenRise();
        assertEquals(typingRiseLow, typingRiseHigh, 1e-6f,
                "screenRise is a fixed height, not something weight should fade");

        float screenRiseLow = PoseAnimator.frameFor(inScreen, ageTicks, 0.2f, false).screenRise();
        float screenRiseHigh = PoseAnimator.frameFor(inScreen, ageTicks, 1f, false).screenRise();
        assertEquals(screenRiseLow, screenRiseHigh, 1e-6f);

        // And it's genuinely nonzero whenever there's a screen at all -- a panel
        // sitting at height 0 (the entity's feet) would not read as "held up".
        assertTrue(typingRiseHigh > 0f);
        assertTrue(screenRiseHigh > 0f);
    }

    // ----------------------------------------------------------------- determinism

    @Test
    void sameCueSameInstantIsByteIdenticalAcrossCalls() {
        UUID id = UUID.randomUUID();
        for (Activity activity : Activity.values()) {
            PlayerCue cue = cue(id, activity, 173, CueFlags.SLEEPY);
            PoseFrame first = PoseAnimator.frameFor(cue, 91.5f, 0.77f, false);
            PoseFrame second = PoseAnimator.frameFor(cue, 91.5f, 0.77f, false);
            assertEquals(first, second, "activity=" + activity + " was not deterministic");
        }
    }

    @Test
    void differentPlayerUuidsProduceDifferentFramesAtTheSameInstant() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // Typing and in-screen both derive their per-hand/per-slot randomness from
        // cue.id().hashCode() (the "seed" in PoseAnimator); two different players
        // must not move identically (class Javadoc: "iki kişinin yan yana yazması
        // kilitli adım olmasın").
        PoseFrame typingA = PoseAnimator.frameFor(cue(a, Activity.TYPING_CHAT, 210, 0), 50f, 1f, false);
        PoseFrame typingB = PoseAnimator.frameFor(cue(b, Activity.TYPING_CHAT, 210, 0), 50f, 1f, false);
        assertNotEquals(typingA, typingB);

        PoseFrame idleA = PoseAnimator.frameFor(cue(a, Activity.AFK, 0, 0), 50f, 1f, false);
        PoseFrame idleB = PoseAnimator.frameFor(cue(b, Activity.AFK, 0, 0), 50f, 1f, false);
        assertNotEquals(idleA, idleB);
    }

    // ------------------------------------------------------- headAim (DESIGN.md §7 P5 hand-test fix)

    /**
     * DESIGN.md §7 P5 hand-test fix: {@code headAim = clamp01(w * 2f)}, so it
     * must saturate to exactly 1 once the pose's own weight reaches 0.5 --
     * "the head is already looking at the target by the time the rest of the
     * pose is only half blended in" is the whole point of the 2x rate.
     */
    @Test
    void headAimReachesFullStrengthAtHalfWeightForTypingAndInScreen() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 128, 0);
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN, 0, 0);

        assertEquals(1f, PoseAnimator.frameFor(typing, 10f, 0.5f, false).headAim(), 1e-6f);
        assertEquals(1f, PoseAnimator.frameFor(inScreen, 10f, 0.5f, false).headAim(), 1e-6f);
        // And it is not saturated yet just below half weight -- confirms the
        // 2x rule rather than some other curve that also happens to hit 1 at 0.5.
        assertEquals(0.8f, PoseAnimator.frameFor(typing, 10f, 0.4f, false).headAim(), 1e-6f);
        assertEquals(0.8f, PoseAnimator.frameFor(inScreen, 10f, 0.4f, false).headAim(), 1e-6f);
    }

    @Test
    void headAimIsZeroAtWeightZeroAndForIdle() {
        UUID id = UUID.randomUUID();
        assertEquals(0f, PoseFrame.NONE.headAim(), "PoseFrame.NONE must carry no head-aim target");

        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 128, 0);
        assertEquals(0f, PoseAnimator.frameFor(typing, 10f, 0f, false).headAim());

        // Idle deliberately keeps the old additive-only droop (DESIGN.md §7 P5
        // hand-test fix's own brief: "idle() (AFK): headAim kullanma") -- it
        // must never set a head-aim target, at any weight.
        PlayerCue idle = cue(id, Activity.AFK, 0, 0);
        assertEquals(0f, PoseAnimator.frameFor(idle, 10f, 1f, false).headAim(),
                "idle must not use headAim, only the additive droop");
    }

    @Test
    void headAimTargetsMatchTheDocumentedPitchesAndFaceStraightAtTheBody() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 128, 0);
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN, 0, 0);

        PoseFrame typingFrame = PoseAnimator.frameFor(typing, 10f, 1f, false);
        assertEquals(0.42f, typingFrame.headAimPitch(), 1e-6f);
        assertEquals(0f, typingFrame.headAimYaw(), 1e-6f);

        PoseFrame inScreenFrame = PoseAnimator.frameFor(inScreen, 10f, 1f, false);
        assertEquals(0.32f, inScreenFrame.headAimPitch(), 1e-6f);
        assertEquals(0f, inScreenFrame.headAimYaw(), 1e-6f);
    }

    // ---------------------------------------------------------------------- typing

    /**
     * DESIGN.md §7 P5 hand-test fix, the regression test for HATA1: hands must
     * tuck IN toward the body, which the model-space sign rule (see {@code
     * PoseAnimator}'s own class Javadoc) means negative yaw on the right arm,
     * positive on the left -- never the same sign on both. Sampled at max
     * intensity (worst case for the reach/dart perturbations that ride on top
     * of the base yaw) across several seconds so a single lucky instant cannot
     * hide a sign regression.
     */
    @Test
    void typingHandsTuckInwardRightArmYawNegativeLeftArmYawPositive() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.TYPING_CHAT, 255, 0);

        for (float seconds = 0f; seconds <= 8f; seconds += 0.05f) {
            PoseFrame frame = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false);
            assertTrue(frame.rightArm().yaw() < 0f,
                    "expected right arm yaw < 0 (tucked in) at t=" + seconds + ", was " + frame.rightArm().yaw());
            assertTrue(frame.leftArm().yaw() > 0f,
                    "expected left arm yaw > 0 (tucked in) at t=" + seconds + ", was " + frame.leftArm().yaw());
        }
    }

    @Test
    void typingHasAScreen() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.TYPING_CHAT, 128, 0);
        assertTrue(PoseAnimator.frameFor(cue, 12f, 1f, false).hasScreen());
    }

    /**
     * DESIGN.md §7 P5b: "a keyboard sits out in front of you at desk height,
     * not clasped up against your chest" -- typing's arm pitch must be
     * shallower (less negative) than in-screen's <em>holding</em> arm pitch at
     * every instant, not just on average. Compared as ranges (the worst-case
     * typing sample against the best-case in-screen-hold sample) so this does
     * not depend on knowing either pose's private constants.
     */
    @Test
    void typingArmPitchIsShallowerThanInScreenHoldPitch() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 255, 0); // max intensity: widest tap swing, worst case for this claim
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN, 0, 0);

        float minTypingPitch = Float.POSITIVE_INFINITY;
        float maxHoldPitch = Float.NEGATIVE_INFINITY;
        for (float seconds = 0f; seconds <= 6f; seconds += 0.02f) {
            float ageTicks = seconds * TICKS_PER_SECOND;
            float typingPitch = PoseAnimator.frameFor(typing, ageTicks, 1f, false).rightArm().pitch();
            minTypingPitch = Math.min(minTypingPitch, typingPitch);

            // The holding arm is IN_SCREEN's left arm -- PoseAnimator.inScreen()'s own
            // Javadoc: "The right arm works, the left holds."
            float holdPitch = PoseAnimator.frameFor(inScreen, ageTicks, 1f, false).leftArm().pitch();
            maxHoldPitch = Math.max(maxHoldPitch, holdPitch);
        }

        assertTrue(minTypingPitch > maxHoldPitch,
                "expected every typing sample to be shallower than every in-screen hold sample: "
                        + "worst typing=" + minTypingPitch + " worst hold=" + maxHoldPitch);
    }

    /**
     * DESIGN.md §7 P5b: taps are "pseudo-random per hand ... rather than two
     * arms in perfect opposite phase". A perfect sin/sin+pi pair would have
     * every sample's right/left deltas pointing in opposite directions; this
     * shows that is not the case by finding an instant where both hands move
     * the same way between two samples -- the robust, existence-based check
     * the task brief suggests, rather than a fragile correlation coefficient.
     */
    @Test
    void typingHandsAreNotLockedInPerfectAntiphase() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.TYPING_CHAT, 220, 0);

        Float previousRight = null;
        Float previousLeft = null;
        boolean sawSameDirectionMove = false;
        for (float seconds = 0f; seconds <= 12f && !sawSameDirectionMove; seconds += 0.03f) {
            PoseFrame frame = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false);
            float right = frame.rightArm().pitch();
            float left = frame.leftArm().pitch();
            if (previousRight != null) {
                double deltaRight = right - previousRight;
                double deltaLeft = left - previousLeft;
                if (Math.signum(deltaRight) != 0 && Math.signum(deltaRight) == Math.signum(deltaLeft)) {
                    sawSameDirectionMove = true;
                }
            }
            previousRight = right;
            previousLeft = left;
        }

        assertTrue(sawSameDirectionMove,
                "expected at least one instant where both hands move the same direction "
                        + "(independent per-hand tap streams), never happens under perfect antiphase");
    }

    /**
     * DESIGN.md §7 P5b: "a higher intensity produces more taps per second".
     * Counts local maxima of the right arm's pitch (each tap's attack/release
     * shape produces exactly one) over a long, finely-sampled window -- no
     * threshold/baseline needed, so this stays correct regardless of the
     * tuned tap amplitude constants.
     */
    @Test
    void higherIntensityProducesMoreTapsPerSecond() {
        UUID id = UUID.randomUUID();
        PlayerCue slow = cue(id, Activity.TYPING_CHAT, 0, 0);
        PlayerCue fast = cue(id, Activity.TYPING_CHAT, 255, 0);

        int slowPeaks = countLocalMaxima(slow, 20f, 1f / 200f);
        int fastPeaks = countLocalMaxima(fast, 20f, 1f / 200f);

        assertTrue(fastPeaks > slowPeaks,
                "expected higher intensity to produce more taps: slow=" + slowPeaks + " fast=" + fastPeaks);
    }

    private static int countLocalMaxima(PlayerCue cue, float windowSeconds, float stepSeconds) {
        float previous = PoseAnimator.frameFor(cue, 0f, 1f, false).rightArm().pitch();
        float current = PoseAnimator.frameFor(cue, stepSeconds * TICKS_PER_SECOND, 1f, false).rightArm().pitch();
        int peaks = 0;
        for (float seconds = 2 * stepSeconds; seconds <= windowSeconds; seconds += stepSeconds) {
            float next = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false).rightArm().pitch();
            if (current > previous && current > next) {
                peaks++;
            }
            previous = current;
            current = next;
        }
        return peaks;
    }

    /**
     * DESIGN.md §7 P5 second hand-test fix, the regression test for HATA A
     * ("iki kol aynı anda aynı yöne hareket ediyor, titriyor gibi"). The bug
     * was never in the per-hand hash -- two different seeds already existed
     * before this fix -- it was that both {@code tapStream} calls shared one
     * time grid ({@code seconds * hz}, identical for both hands), so whenever
     * the hash happened to select a tap for both hands on the same step,
     * their attack/release envelopes peaked at the exact same instant. This
     * checks the actual fix (independent rate + phase per hand, see {@code
     * PoseAnimator.tapStream}'s own Javadoc), not just the tuned constants:
     * over a long, finely-sampled window, only a small minority of one
     * hand's tap peaks should land within a couple of samples of the other
     * hand's nearest peak. A shared time grid would put a large majority of
     * them there instead (whenever both hands' hash selected the same step).
     */
    @Test
    void typingHandsTapOnIndependentTimeGridsNotJustIndependentHashes() {
        UUID id = UUID.randomUUID();
        // Max intensity: fastest tap rate, most peaks to sample from, and the
        // worst case for accidental coincidence (more taps per second means
        // more opportunities for a shared grid to line two of them up).
        PlayerCue cue = cue(id, Activity.TYPING_CHAT, 255, 0);

        float stepSeconds = 1f / 400f;
        float windowSeconds = 20f;
        List<Float> rightPeaks = peakTimes(cue, windowSeconds, stepSeconds, frame -> frame.rightArm().pitch());
        List<Float> leftPeaks = peakTimes(cue, windowSeconds, stepSeconds, frame -> frame.leftArm().pitch());

        assertTrue(rightPeaks.size() > 10 && leftPeaks.size() > 10,
                "expected plenty of taps to sample from over " + windowSeconds + "s: "
                        + "right=" + rightPeaks.size() + " left=" + leftPeaks.size());

        // A couple of sample steps of slack: under the old shared-grid bug a
        // coincident peak lands at EXACTLY the same true instant regardless of
        // sampling, so two independently-detected peaks for it can differ by
        // at most about one sample step each -- comfortably caught by this
        // tolerance. Under the fix, peak spacing is at least ~1/TYPING_MAX_HZ
        // apart (~0.11s), far larger than this tolerance, so a genuine
        // coincidence this close is not expected to happen by chance.
        float coincidenceTolerance = stepSeconds * 2f;
        long coincidences = rightPeaks.stream()
                .filter(rightTime -> leftPeaks.stream().anyMatch(leftTime -> Math.abs(leftTime - rightTime) <= coincidenceTolerance))
                .count();
        double coincidenceFraction = (double) coincidences / rightPeaks.size();

        assertTrue(coincidenceFraction < 0.3,
                "expected only a small minority of right-hand tap peaks to coincide with a left-hand peak "
                        + "(a shared time grid would put a majority there): "
                        + coincidences + "/" + rightPeaks.size() + " = " + coincidenceFraction);
    }

    private static List<Float> peakTimes(PlayerCue cue, float windowSeconds, float stepSeconds, FloatExtractor extractor) {
        List<Float> peaks = new ArrayList<>();
        float previous = extractor.apply(PoseAnimator.frameFor(cue, 0f, 1f, false));
        float current = extractor.apply(PoseAnimator.frameFor(cue, stepSeconds * TICKS_PER_SECOND, 1f, false));
        for (float seconds = 2 * stepSeconds; seconds <= windowSeconds; seconds += stepSeconds) {
            float next = extractor.apply(PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false));
            if (current > previous && current > next) {
                peaks.add(seconds - stepSeconds); // the sample time `current` was taken at
            }
            previous = current;
            current = next;
        }
        return peaks;
    }

    @Test
    void allFourTypingActivitiesProduceTheSamePoseFamily() {
        UUID id = UUID.randomUUID();
        int intensity = 137;
        float ageTicks = 42.5f;
        float weight = 0.66f;

        PoseFrame chat = PoseAnimator.frameFor(cue(id, Activity.TYPING_CHAT, intensity, 0), ageTicks, weight, false);
        PoseFrame command = PoseAnimator.frameFor(cue(id, Activity.TYPING_COMMAND, intensity, 0), ageTicks, weight, false);
        PoseFrame sign = PoseAnimator.frameFor(cue(id, Activity.TYPING_SIGN, intensity, 0), ageTicks, weight, false);
        PoseFrame book = PoseAnimator.frameFor(cue(id, Activity.TYPING_BOOK, intensity, 0), ageTicks, weight, false);

        assertEquals(chat, command);
        assertEquals(chat, sign);
        assertEquals(chat, book);
    }

    // ------------------------------------------------------------------ in-screen

    @Test
    void inScreenHasAScreen() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.IN_SCREEN, 0, 0);
        assertTrue(PoseAnimator.frameFor(cue, 5f, 1f, false).hasScreen());
    }

    @Test
    void inScreenReportsScreenWeightEqualToWeightAndNonZeroTilt() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.IN_SCREEN, 0, 0);

        for (float w : new float[] {0.1f, 0.5f, 1f}) {
            // ageTicks == 0 puts both wobble sines at their zero crossing, leaving
            // just the resting base tilt -- guaranteed non-zero for any w > 0.
            PoseFrame frame = PoseAnimator.frameFor(cue, 0f, w, false);
            assertEquals(w, frame.screenWeight(), 1e-6f);
            assertNotEquals(0f, frame.screenTilt(), 1e-6f);
        }
    }

    /**
     * DESIGN.md §7 P5b: "the asymmetry is the feature" -- the two arms must
     * not be mirror images of one another the way the old typing pose was.
     * Checked as "their pitches are far from being exact opposites", which a
     * true mirror pose (right.pitch() == -left.pitch()) would fail.
     */
    @Test
    void inScreenArmsAreNotMirrorImages() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.IN_SCREEN, 0, 0);

        for (float seconds = 0f; seconds <= 3f; seconds += 0.5f) {
            PoseFrame frame = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false);
            float sumOfPitches = frame.rightArm().pitch() + frame.leftArm().pitch();
            // A mirror pose would have right.pitch() == -left.pitch(), i.e. sumOfPitches
            // == 0. The work/hold poses sit at entirely different base angles, so this
            // sum stays solidly nonzero at every instant.
            assertTrue(Math.abs(sumOfPitches) > 0.05f,
                    "arms read as mirror images at t=" + seconds + ": sum=" + sumOfPitches);
        }
    }

    /**
     * DESIGN.md §7 P5 hand-test fix, the regression test for HATA1 in
     * {@code inScreen()}: the holding (left) arm's roll is dominated by the
     * static {@code SCREEN_HOLD_ROLL} term (the wobble riding on top of it is
     * small by comparison), so it must be positive -- tucked inward -- at
     * every instant. The working (right) arm's roll also has a static inward
     * (negative) base, but the reach envelope riding on top of it is large
     * enough to cross zero on individual samples, so this checks the sign
     * that must dominate over a long window instead: negative samples must
     * outnumber non-negative ones. A HATA1-style regression (both arms given
     * the same sign) would flip both of these checks.
     */
    @Test
    void inScreenArmsTuckInwardHoldRollPositiveWorkRollLeansNegative() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.IN_SCREEN, 0, 0);

        int workNegative = 0;
        int workTotal = 0;
        for (float seconds = 0f; seconds <= 60f; seconds += 0.1f) {
            PoseFrame frame = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false);
            assertTrue(frame.leftArm().roll() > 0f,
                    "expected the holding (left) arm's roll > 0 (tucked in) at t=" + seconds
                            + ", was " + frame.leftArm().roll());
            if (frame.rightArm().roll() < 0f) {
                workNegative++;
            }
            workTotal++;
        }

        assertTrue(workNegative > workTotal / 2,
                "expected the working (right) arm's roll to be negative (tucked in) on a majority of samples: "
                        + workNegative + "/" + workTotal);
    }

    /**
     * DESIGN.md §7 P5b: "the holding arm's pitch varies far less over a long
     * window than the working arm's" -- the hold arm only gets the panel's
     * own small wobble, the work arm ranges across the whole reach envelope.
     */
    @Test
    void inScreenHoldingArmVariesFarLessThanWorkingArm() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.IN_SCREEN, 0, 0);

        float minHold = Float.POSITIVE_INFINITY;
        float maxHold = Float.NEGATIVE_INFINITY;
        float minWork = Float.POSITIVE_INFINITY;
        float maxWork = Float.NEGATIVE_INFINITY;
        for (float seconds = 0f; seconds <= 15f; seconds += 0.05f) {
            PoseFrame frame = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false);
            float hold = frame.leftArm().pitch();
            float work = frame.rightArm().pitch();
            minHold = Math.min(minHold, hold);
            maxHold = Math.max(maxHold, hold);
            minWork = Math.min(minWork, work);
            maxWork = Math.max(maxWork, work);
        }

        float holdRange = maxHold - minHold;
        float workRange = maxWork - minWork;
        assertTrue(holdRange < workRange * 0.5,
                "expected the holding arm's pitch range to be far smaller than the working arm's: "
                        + "hold=" + holdRange + " work=" + workRange);
    }

    // ---------------------------------------------------------------------- idle

    @Test
    void idleHasNoScreen() {
        UUID id = UUID.randomUUID();
        for (int flags : new int[] {0, CueFlags.SLEEPY}) {
            PlayerCue cue = cue(id, Activity.AFK, 0, flags);
            assertFalse(PoseAnimator.frameFor(cue, 9f, 1f, false).hasScreen());
        }
    }

    /** DESIGN.md §7 P5b: idle now also sways the body, not just the head. */
    @Test
    void idleBodyIsNonZeroSomewhereInAWindow() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.AFK, 0, 0);

        boolean sawNonZeroBody = false;
        for (float seconds = 0f; seconds <= 10f; seconds += 0.1f) {
            Limb body = PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false).body();
            if (!body.isZero()) {
                sawNonZeroBody = true;
                break;
            }
        }
        assertTrue(sawNonZeroBody, "expected AFK's body limb to move at some point in a 10s window");
    }

    @Test
    void sleepyDroopsTheHeadFurtherThanPlainAfk() {
        UUID id = UUID.randomUUID();
        PlayerCue plain = cue(id, Activity.AFK, 0, 0);
        PlayerCue sleepy = cue(id, Activity.AFK, 0, CueFlags.SLEEPY);

        // ageTicks == 0 puts the nod/sway wobble at its own zero crossing,
        // isolating the pure droop constant each branch adds.
        float plainPitch = PoseAnimator.frameFor(plain, 0f, 1f, false).head().pitch();
        float sleepyPitch = PoseAnimator.frameFor(sleepy, 0f, 1f, false).head().pitch();

        assertTrue(sleepyPitch > plainPitch,
                "expected SLEEPY to droop further: plain=" + plainPitch + " sleepy=" + sleepyPitch);
    }

    /**
     * DESIGN.md §7 P5b: "the body sway and the head sway are not in
     * lockstep" -- they run on different, non-matching frequencies
     * ({@code AFK_NOD_HZ} vs {@code AFK_BODY_SWAY_HZ}), so counting how many
     * times each one peaks over the same window should not agree.
     */
    @Test
    void idleBodySwayAndHeadSwayAreNotInLockstep() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.AFK, 0, 0);
        float windowSeconds = 40f;
        float stepSeconds = 0.05f;

        int headPeaks = countLocalMaxima(cue, windowSeconds, stepSeconds, frame -> frame.head().pitch());
        int bodyPeaks = countLocalMaxima(cue, windowSeconds, stepSeconds, frame -> frame.body().roll());

        assertNotEquals(headPeaks, bodyPeaks,
                "expected head and body sway to peak a different number of times over " + windowSeconds
                        + "s if they are genuinely on different frequencies (head=" + headPeaks
                        + " body=" + bodyPeaks + ")");
    }

    // ------------------------------------------------- reducedMotion (P6 §4.1)

    /**
     * "Every time-varying term is removed" — the core contract. Sampled at two
     * {@code ageTicks} far enough apart that any surviving oscillation term
     * (drift/tap/reach for typing, wobble/reach for in-screen, nod/sway/loll
     * for idle) would almost certainly show up as a difference; under
     * {@code reducedMotion} there must be none.
     */
    @Test
    void reducedMotionProducesAnIdenticalFrameAtDifferentAgeTicksForEveryPosedActivity() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 200, 0);
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN, 0, 0);
        PlayerCue afk = cue(id, Activity.AFK, 0, 0);
        PlayerCue sleepy = cue(id, Activity.AFK, 0, CueFlags.SLEEPY);

        for (PlayerCue c : new PlayerCue[] {typing, inScreen, afk, sleepy}) {
            PoseFrame early = PoseAnimator.frameFor(c, 5f, 1f, true);
            PoseFrame late = PoseAnimator.frameFor(c, 9137f, 1f, true);
            assertEquals(early, late, "activity=" + c.activity() + " flags=" + c.flags()
                    + " was not stable under reducedMotion");
            // Not just stable at PoseFrame.NONE -- it must still be a visible pose.
            assertFalse(early.isIdentity());
        }
    }

    /**
     * "State changes still happen" — reducedMotion must not collapse every
     * activity to the same held frame; the steady pose it holds still has to
     * differ by what the player is actually doing.
     */
    @Test
    void reducedMotionFramesStillDifferByActivity() {
        UUID id = UUID.randomUUID();
        PoseFrame typing = PoseAnimator.frameFor(cue(id, Activity.TYPING_CHAT, 128, 0), 40f, 1f, true);
        PoseFrame inScreen = PoseAnimator.frameFor(cue(id, Activity.IN_SCREEN, 0, 0), 40f, 1f, true);
        PoseFrame afk = PoseAnimator.frameFor(cue(id, Activity.AFK, 0, 0), 40f, 1f, true);

        assertNotEquals(typing, inScreen);
        assertNotEquals(typing, afk);
        assertNotEquals(inScreen, afk);
    }

    /**
     * The held reducedMotion frame must be the same pose the full animation
     * rests at between taps, not some other angle — same regression check as
     * {@link #typingHandsTuckInwardRightArmYawNegativeLeftArmYawPositive}, just
     * confirming it still holds at the one steady instant reducedMotion keeps.
     */
    @Test
    void reducedMotionTypingStillTucksHandsInward() {
        UUID id = UUID.randomUUID();
        PoseFrame frame = PoseAnimator.frameFor(cue(id, Activity.TYPING_CHAT, 255, 0), 77f, 1f, true);
        assertTrue(frame.rightArm().yaw() < 0f, "expected right arm yaw < 0 (tucked in), was " + frame.rightArm().yaw());
        assertTrue(frame.leftArm().yaw() > 0f, "expected left arm yaw > 0 (tucked in), was " + frame.leftArm().yaw());
    }

    /**
     * The droop itself (plain AFK vs. SLEEPY) is state, not motion, so it must
     * survive reducedMotion exactly like {@link #sleepyDroopsTheHeadFurtherThanPlainAfk}
     * shows it does without it.
     */
    @Test
    void reducedMotionSleepyStillDroopsTheHeadFurtherThanPlainAfk() {
        UUID id = UUID.randomUUID();
        PlayerCue plain = cue(id, Activity.AFK, 0, 0);
        PlayerCue sleepy = cue(id, Activity.AFK, 0, CueFlags.SLEEPY);

        float plainPitch = PoseAnimator.frameFor(plain, 0f, 1f, true).head().pitch();
        float sleepyPitch = PoseAnimator.frameFor(sleepy, 0f, 1f, true).head().pitch();

        assertTrue(sleepyPitch > plainPitch,
                "expected SLEEPY to droop further even under reducedMotion: plain=" + plainPitch + " sleepy=" + sleepyPitch);
    }

    /** Unlike {@link #idleBodyIsNonZeroSomewhereInAWindow}, the body sway is exactly the kind of term reducedMotion removes. */
    @Test
    void reducedMotionAfkBodyIsPerfectlyStill() {
        UUID id = UUID.randomUUID();
        PlayerCue cue = cue(id, Activity.AFK, 0, 0);
        Limb body = PoseAnimator.frameFor(cue, 733f, 1f, true).body();
        assertTrue(body.isZero(), "expected AFK's body to be perfectly still under reducedMotion, was " + body);
    }

    /**
     * headAim is a blend-to-target driven by {@code weight} alone (a state
     * change, DESIGN.md §7 P5 hand-test fix), not a per-tick oscillation --
     * it must keep ramping under reducedMotion exactly as {@link
     * #headAimReachesFullStrengthAtHalfWeightForTypingAndInScreen} shows it
     * does without it.
     */
    @Test
    void reducedMotionHeadAimStillRampsWithWeight() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 128, 0);
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN, 0, 0);

        assertEquals(1f, PoseAnimator.frameFor(typing, 10f, 0.5f, true).headAim(), 1e-6f);
        assertEquals(1f, PoseAnimator.frameFor(inScreen, 10f, 0.5f, true).headAim(), 1e-6f);
        assertEquals(0.8f, PoseAnimator.frameFor(typing, 10f, 0.4f, true).headAim(), 1e-6f);
    }

    @Test
    void reducedMotionStillHasAScreenForTypingAndInScreen() {
        UUID id = UUID.randomUUID();
        assertTrue(PoseAnimator.frameFor(cue(id, Activity.TYPING_CHAT, 128, 0), 10f, 1f, true).hasScreen());
        assertTrue(PoseAnimator.frameFor(cue(id, Activity.IN_SCREEN, 0, 0), 10f, 1f, true).hasScreen());
    }

    @Test
    void reducedMotionWeightZeroStillReturnsExactlyNone() {
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT, 128, 0);
        assertEquals(PoseFrame.NONE, PoseAnimator.frameFor(typing, 37f, 0f, true));
    }

    private interface FloatExtractor {
        float apply(PoseFrame frame);
    }

    private static int countLocalMaxima(PlayerCue cue, float windowSeconds, float stepSeconds, FloatExtractor extractor) {
        float previous = extractor.apply(PoseAnimator.frameFor(cue, 0f, 1f, false));
        float current = extractor.apply(PoseAnimator.frameFor(cue, stepSeconds * TICKS_PER_SECOND, 1f, false));
        int peaks = 0;
        for (float seconds = 2 * stepSeconds; seconds <= windowSeconds; seconds += stepSeconds) {
            float next = extractor.apply(PoseAnimator.frameFor(cue, seconds * TICKS_PER_SECOND, 1f, false));
            if (current > previous && current > next) {
                peaks++;
            }
            previous = current;
            current = next;
        }
        return peaks;
    }

    private static PlayerCue cue(UUID id, Activity activity, int intensity, int flags) {
        return new PlayerCue(id, activity, ScreenKind.UNKNOWN, intensity, flags, 0L);
    }
}
