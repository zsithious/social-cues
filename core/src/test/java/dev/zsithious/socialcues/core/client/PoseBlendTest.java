package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §7 Katman 3, P5a — {@link PoseBlend}'s ease in/out state machine.
 * Every assertion here uses a tolerance, never exact float equality, because
 * every observable weight has passed through {@link PoseBlend}'s smoothstep
 * curve. Timings (ease-in ~0.22s typing / ~0.35s screen / ~1.60s idle,
 * ease-out ~0.25s for everything) are the ones documented in {@link
 * PoseBlend}'s own class Javadoc; this test drives simulated ticks well past
 * or well short of them rather than asserting on the private constants
 * directly, so it stays meaningful even if those numbers are tuned by eye
 * in-game later.
 */
class PoseBlendTest {

    private static final float TICK = 1f / 20f;

    @Test
    void unknownPlayerWithNoCueReturnsNullAndIsNotTracked() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();

        PoseBlend.Blend result = blend.update(id, null, TICK);

        assertNull(result);
        assertEquals(0, blend.trackedCount());
    }

    @Test
    void weightRisesFromZeroToOneOverTheDocumentedEaseInTime() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT);

        PoseBlend.Blend result = null;
        for (int i = 0; i < 12; i++) { // 12 * 1/20s = 0.6s, comfortably past the ~0.22s documented ease-in.
            result = blend.update(id, typing, TICK);
        }

        assertNotNull(result);
        assertTrue(result.weight() > 0.95f, "expected typing to be fully eased in by 0.6s, was " + result.weight());
    }

    @Test
    void idleEasesInMarkedlySlowerThanTyping() {
        PoseBlend typingBlend = new PoseBlend();
        PoseBlend idleBlend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT);
        PlayerCue idle = cue(id, Activity.AFK);

        PoseBlend.Blend typingResult = null;
        PoseBlend.Blend idleResult = null;
        // Same elapsed time (0.3s) for both: comfortably past typing's ~0.22s
        // documented ease-in, well short of idle's ~1.60s one.
        for (int i = 0; i < 6; i++) {
            typingResult = typingBlend.update(id, typing, TICK);
            idleResult = idleBlend.update(id, idle, TICK);
        }

        assertNotNull(typingResult);
        assertNotNull(idleResult);
        assertTrue(typingResult.weight() > 0.9f,
                "typing should be nearly fully eased in by 0.3s, was " + typingResult.weight());
        assertTrue(idleResult.weight() < 0.5f,
                "idle should still be easing in at 0.3s, was " + idleResult.weight());

        // Given enough time (comfortably past the ~1.60s documented ease-in),
        // idle does eventually reach full strength too -- it is slower, not stuck.
        for (int i = 0; i < 40; i++) { // + 2.0s
            idleResult = idleBlend.update(id, idle, TICK);
        }
        assertTrue(idleResult.weight() > 0.95f,
                "idle should be fully eased in well past 1.60s, was " + idleResult.weight());
    }

    @Test
    void weightFallsToZeroAndEntryIsRemovedAfterCueDisappears() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT);

        for (int i = 0; i < 12; i++) {
            blend.update(id, typing, TICK);
        }
        assertEquals(1, blend.trackedCount());

        PoseBlend.Blend result = null;
        for (int i = 0; i < 10; i++) { // 10 * 1/20s = 0.5s, comfortably past the ~0.25s documented ease-out.
            result = blend.update(id, null, TICK);
        }

        assertNull(result);
        assertEquals(0, blend.trackedCount());
    }

    @Test
    void whileFadingOutTheBlendStillReportsTheLeavingCueNotANewOne() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT);

        for (int i = 0; i < 12; i++) {
            blend.update(id, typing, TICK);
        }

        // One tick after the cue disappears: still fading, not yet dropped.
        PoseBlend.Blend result = blend.update(id, null, TICK);

        assertNotNull(result, "should still be fading out, not yet dropped");
        assertEquals(Activity.TYPING_CHAT, result.cue().activity(),
                "while fading out, the blend must still report the pose that is leaving");
        assertTrue(result.weight() > 0f && result.weight() < 1f);
    }

    @Test
    void switchingWithinTheSamePoseFamilyPreservesWeight() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue chat = cue(id, Activity.TYPING_CHAT);
        PlayerCue sign = cue(id, Activity.TYPING_SIGN);

        PoseBlend.Blend before = null;
        for (int i = 0; i < 3; i++) { // partway through the ease-in, not yet full -- needed for this test to mean anything.
            before = blend.update(id, chat, TICK);
        }
        assertNotNull(before);
        assertTrue(before.weight() < 0.95f, "test needs a partial weight to be meaningful, was " + before.weight());

        PoseBlend.Blend after = blend.update(id, sign, TICK);

        assertNotNull(after);
        assertEquals(Activity.TYPING_SIGN, after.cue().activity());
        // Same pose family (all four TYPING_* share one, per PoseBlend.family): the
        // ramp continues from roughly where it was, it does not restart from zero.
        assertTrue(after.weight() >= before.weight() - 0.05f,
                "switching within the same pose family should not drop the weight back toward zero");
    }

    @Test
    void switchingPoseFamilyCrossFadesInsteadOfSwappingOrDropping() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue chat = cue(id, Activity.TYPING_CHAT);
        PlayerCue inScreen = cue(id, Activity.IN_SCREEN);

        for (int i = 0; i < 12; i++) { // fully eased in as TYPING_CHAT
            blend.update(id, chat, TICK);
        }

        // The instant the family changes, the *old* pose must still be the one
        // being animated, on its way out — swapping at full weight would teleport
        // the limbs between two unrelated positions in a single frame.
        PoseBlend.Blend justAfter = blend.update(id, inScreen, TICK);
        assertNotNull(justAfter, "a pose-family switch must not drop the tracked entry");
        assertEquals(1, blend.trackedCount());
        assertEquals(Activity.TYPING_CHAT, justAfter.cue().activity(),
                "the outgoing pose keeps animating until it has faded");
        assertTrue(justAfter.weight() < 1f, "the outgoing pose must be falling, was " + justAfter.weight());

        // Once the dip bottoms out the queued pose takes over and ramps back up.
        PoseBlend.Blend latest = justAfter;
        for (int i = 0; i < 40 && latest.cue().activity() != Activity.IN_SCREEN; i++) {
            latest = blend.update(id, inScreen, TICK);
            assertNotNull(latest, "the entry must survive the whole cross-fade");
        }
        assertEquals(Activity.IN_SCREEN, latest.cue().activity(),
                "the queued pose should have taken over well within the ease-out time");

        float before = latest.weight();
        for (int i = 0; i < 8; i++) {
            latest = blend.update(id, inScreen, TICK);
        }
        assertTrue(latest.weight() > before,
                "the incoming pose should be ramping up again, was " + latest.weight());
    }

    @Test
    void forgetDropsOnePlayer() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        blend.update(id, cue(id, Activity.TYPING_CHAT), TICK);
        assertEquals(1, blend.trackedCount());

        blend.forget(id);

        assertEquals(0, blend.trackedCount());
        assertNull(blend.update(id, null, TICK));
    }

    @Test
    void retainOnlyDropsPlayersThatAreNoLongerPresent() {
        PoseBlend blend = new PoseBlend();
        UUID stays = UUID.randomUUID();
        UUID leaves = UUID.randomUUID();
        blend.update(stays, cue(stays, Activity.TYPING_CHAT), TICK);
        blend.update(leaves, cue(leaves, Activity.TYPING_CHAT), TICK);
        assertEquals(2, blend.trackedCount());

        // A player who left is never passed to update() again, so easing them out
        // can never happen on its own — without retainOnly they would be remembered
        // at whatever weight they walked away with for the rest of the session.
        blend.retainOnly(Set.of(stays));

        assertEquals(1, blend.trackedCount());
        assertNotNull(blend.update(stays, cue(stays, Activity.TYPING_CHAT), TICK));
        assertNull(blend.update(leaves, null, TICK));
    }

    @Test
    void retainOnlyWithEveryoneStillPresentChangesNothing() {
        PoseBlend blend = new PoseBlend();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        blend.update(a, cue(a, Activity.IN_SCREEN), TICK);
        blend.update(b, cue(b, Activity.AFK), TICK);

        blend.retainOnly(Set.of(a, b));

        assertEquals(2, blend.trackedCount());
    }

    @Test
    void clearDropsEveryPlayer() {
        PoseBlend blend = new PoseBlend();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        blend.update(a, cue(a, Activity.TYPING_CHAT), TICK);
        blend.update(b, cue(b, Activity.AFK), TICK);
        assertEquals(2, blend.trackedCount());

        blend.clear();

        assertEquals(0, blend.trackedCount());
    }

    @Test
    void hugeDeltaIsClampedRatherThanTeleportingThePose() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        // Idle's ~1.60s documented ease-in is comfortably longer than PoseBlend's
        // own delta clamp ceiling (0.25s, DESIGN.md's task note), so even a single
        // enormous delta cannot reach full weight in one call.
        PlayerCue idle = cue(id, Activity.AFK);

        PoseBlend.Blend result = blend.update(id, idle, 1000f);

        assertNotNull(result);
        assertTrue(result.weight() < 0.5f,
                "a single huge delta must not teleport the pose to full weight, was " + result.weight());
        assertTrue(result.weight() > 0f);
    }

    @Test
    void nanAndNegativeDeltasDoNotCorruptTheWeight() {
        PoseBlend blend = new PoseBlend();
        UUID id = UUID.randomUUID();
        PlayerCue typing = cue(id, Activity.TYPING_CHAT);

        PoseBlend.Blend baseline = null;
        for (int i = 0; i < 3; i++) {
            baseline = blend.update(id, typing, TICK);
        }
        assertNotNull(baseline);

        PoseBlend.Blend afterNaN = blend.update(id, typing, Float.NaN);
        PoseBlend.Blend afterNegative = blend.update(id, typing, -5f);

        assertNotNull(afterNaN);
        assertFalse(Float.isNaN(afterNaN.weight()));
        assertEquals(baseline.weight(), afterNaN.weight(), 1e-6f);

        assertNotNull(afterNegative);
        assertFalse(Float.isNaN(afterNegative.weight()));
        assertEquals(baseline.weight(), afterNegative.weight(), 1e-6f);
    }

    private static PlayerCue cue(UUID id, Activity activity) {
        return new PlayerCue(id, activity, ScreenKind.UNKNOWN, 0, 0, 0L);
    }
}
