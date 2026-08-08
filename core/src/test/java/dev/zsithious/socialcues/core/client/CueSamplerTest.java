package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.protocol.CueUpdate;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §14 P3's "asıl doğrulama yolu": change detection, the ≤4/s
 * self-throttle, and policy masking, composed as the single "should I send
 * a CueUpdate now" decision.
 */
class CueSamplerTest {

    @Test
    void firstSampleAlwaysSends() {
        CueSampler sampler = new CueSampler();
        Optional<CueUpdate> update = sampler.sample(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);
        assertTrue(update.isPresent());
        assertEquals(new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), update.get());
    }

    @Test
    void unchangedStateNeverResendsEvenAfterLongDelay() {
        CueSampler sampler = new CueSampler();
        sampler.sample(Activity.AFK, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);

        // Far beyond the rate-limit window; still no send, because DESIGN.md
        // §5's rule is "değişmeyeni tekrar göndermez" — change detection, not
        // a periodic heartbeat.
        Optional<CueUpdate> second = sampler.sample(Activity.AFK, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 60_000L);
        assertTrue(second.isEmpty());
    }

    @Test
    void changedStateSendsImmediatelyWhenOutsideRateLimitWindow() {
        CueSampler sampler = new CueSampler(250L);
        sampler.sample(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);

        Optional<CueUpdate> second =
                sampler.sample(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 100, 0, PolicyBits.ALL, 1_000L);
        assertTrue(second.isPresent());
        assertEquals(Activity.TYPING_CHAT, second.get().activity());
    }

    @Test
    void rapidChangeWithinWindowIsHeldBackThenSentOnceWindowClears() {
        CueSampler sampler = new CueSampler(250L);
        sampler.sample(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);

        // 50ms later: state differs from what was sent, but inside the 250ms
        // self-throttle window -> held back.
        Optional<CueUpdate> heldBack =
                sampler.sample(Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0, PolicyBits.ALL, 50L);
        assertTrue(heldBack.isEmpty());

        // Still inside the window at 200ms -> still held back.
        Optional<CueUpdate> stillHeldBack =
                sampler.sample(Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0, PolicyBits.ALL, 200L);
        assertTrue(stillHeldBack.isEmpty());

        // Window has cleared (>= 250ms since the last actual send at t=0) and
        // the state is still different from what was last sent -> sends now.
        Optional<CueUpdate> sentAfterWindow =
                sampler.sample(Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0, PolicyBits.ALL, 250L);
        assertTrue(sentAfterWindow.isPresent());
        assertEquals(new CueUpdate(Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0), sentAfterWindow.get());
    }

    @Test
    void policyMaskingAppliesBeforeChangeDetection() {
        CueSampler sampler = new CueSampler();
        int noTyping = PolicyBits.ALL & ~PolicyBits.TYPING;

        Optional<CueUpdate> first =
                sampler.sample(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 200, 0, noTyping, 0L);
        assertTrue(first.isPresent());
        // TYPING bit is off -> masked down to NORMAL, exactly like EffectivePolicy.applyNear.
        assertEquals(Activity.NORMAL, first.get().activity());

        // A second, different *raw* activity with the same intensity masks
        // down to the exact same (NORMAL, UNKNOWN, 200, 0) result and must be
        // treated as "no change" — masking happens before change detection,
        // not after.
        Optional<CueUpdate> second =
                sampler.sample(Activity.TYPING_COMMAND, ScreenKind.UNKNOWN, 200, 0, noTyping, 1_000L);
        assertTrue(second.isEmpty());
    }

    @Test
    void intensityBitOffZeroesIntensityEvenWhenTyping() {
        CueSampler sampler = new CueSampler();
        int noIntensity = PolicyBits.ALL & ~PolicyBits.INTENSITY;
        Optional<CueUpdate> update =
                sampler.sample(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 255, 0, noIntensity, 0L);
        assertEquals(0, update.orElseThrow().intensity());
    }

    @Test
    void mutedSelfCollapsesToNeutralRegardlessOfPolicy() {
        CueSampler sampler = new CueSampler();
        Optional<CueUpdate> update = sampler.sample(
                Activity.TYPING_CHAT, ScreenKind.INVENTORY, 200, CueFlags.MUTED_SELF, PolicyBits.ALL, 0L);
        assertEquals(new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), update.orElseThrow());
    }

    @Test
    void resetForcesUnconditionalResendOfSameState() {
        CueSampler sampler = new CueSampler();
        sampler.sample(Activity.AFK, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);

        Optional<CueUpdate> beforeReset = sampler.sample(Activity.AFK, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 10L);
        assertTrue(beforeReset.isEmpty());

        sampler.reset();

        Optional<CueUpdate> afterReset = sampler.sample(Activity.AFK, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 20L);
        assertTrue(afterReset.isPresent());
    }

    @Test
    void resetAlsoClearsRateLimitClock() {
        CueSampler sampler = new CueSampler(10_000L);
        sampler.sample(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);
        sampler.reset();

        // Without the reset, this would still be inside the 10s window and
        // held back; reset() must clear lastSentAtMillis too, not just the
        // last-sent value.
        Optional<CueUpdate> update =
                sampler.sample(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 10, 0, PolicyBits.ALL, 1L);
        assertTrue(update.isPresent());
    }

    @Test
    void negativeMinSendIntervalRejected() {
        assertThrows(IllegalArgumentException.class, () -> new CueSampler(-1L));
    }

    @Test
    void zeroMinSendIntervalNeverHoldsBackAChange() {
        CueSampler sampler = new CueSampler(0L);
        sampler.sample(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);
        Optional<CueUpdate> update = sampler.sample(Activity.AFK, ScreenKind.UNKNOWN, 0, 0, PolicyBits.ALL, 0L);
        assertTrue(update.isPresent());
    }

    @Test
    void screenKindOnlySurvivesMaskingWhenActivityIsInScreen() {
        CueSampler sampler = new CueSampler();
        Optional<CueUpdate> update =
                sampler.sample(Activity.NORMAL, ScreenKind.FURNACE, 0, 0, PolicyBits.ALL, 0L);
        // EffectivePolicy.applyNear only preserves screenKind when activity == IN_SCREEN.
        assertEquals(ScreenKind.UNKNOWN, update.orElseThrow().screenKind());
        assertFalse(update.get().screenKind() == ScreenKind.FURNACE);
    }
}
