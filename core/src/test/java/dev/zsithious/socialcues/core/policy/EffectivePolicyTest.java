package dev.zsithious.socialcues.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §5: "Etkin izne uymayan alanları gönderim anında sıfırlar."
 * These tests are the ground truth for exactly which field gets reset by
 * which missing bit, since that mapping is only described in prose in
 * DESIGN.md and nowhere else.
 */
class EffectivePolicyTest {

    private static final UUID ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static PlayerCue cue(Activity activity, ScreenKind screen, int intensity, int flags) {
        return new PlayerCue(ID, activity, screen, intensity, flags, 0L);
    }

    @Test
    void effectiveBitsIsBitwiseAnd() {
        int policy = PolicyBits.TYPING | PolicyBits.SCREENS | PolicyBits.VOICE;
        int prefs = PolicyBits.TYPING | PolicyBits.IDLE;
        assertEquals(PolicyBits.TYPING, EffectivePolicy.effectiveBits(policy, prefs));
    }

    @Test
    void fullPermissionPassesEverythingThrough() {
        PlayerCue real = cue(Activity.TYPING_CHAT, ScreenKind.INVENTORY, 200, CueFlags.SNEAKING);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, PolicyBits.ALL);
        assertEquals(new CueBatch.Entry(ID, Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 200, CueFlags.SNEAKING), masked);
        // screen stays UNKNOWN here only because activity isn't IN_SCREEN; verified separately below.
    }

    @Test
    void typingBitOffDowngradesAllFourTypingActivitiesToNormal() {
        int noTyping = PolicyBits.ALL & ~PolicyBits.TYPING;
        for (Activity typing : new Activity[] {
                Activity.TYPING_CHAT, Activity.TYPING_COMMAND, Activity.TYPING_SIGN, Activity.TYPING_BOOK}) {
            PlayerCue real = cue(typing, ScreenKind.UNKNOWN, 100, 0);
            CueBatch.Entry masked = EffectivePolicy.applyNear(real, noTyping);
            assertEquals(Activity.NORMAL, masked.activity(), "expected " + typing + " to downgrade");
        }
    }

    @Test
    void screensBitOffDowngradesInScreenToNormal() {
        int noScreens = PolicyBits.ALL & ~PolicyBits.SCREENS;
        PlayerCue real = cue(Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, noScreens);
        assertEquals(Activity.NORMAL, masked.activity());
        assertEquals(ScreenKind.UNKNOWN, masked.screenKind());
    }

    @Test
    void screenDetailBitOffKeepsInScreenButHidesKind() {
        int noDetail = PolicyBits.ALL & ~PolicyBits.SCREEN_DETAIL;
        PlayerCue real = cue(Activity.IN_SCREEN, ScreenKind.ANVIL, 0, 0);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, noDetail);
        assertEquals(Activity.IN_SCREEN, masked.activity());
        assertEquals(ScreenKind.UNKNOWN, masked.screenKind());
    }

    @Test
    void idleBitOffDowngradesAfkToNormalAndClearsSleepy() {
        int noIdle = PolicyBits.ALL & ~PolicyBits.IDLE;
        PlayerCue real = cue(Activity.AFK, ScreenKind.UNKNOWN, 0, CueFlags.SLEEPY);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, noIdle);
        assertEquals(Activity.NORMAL, masked.activity());
        assertEquals(0, masked.flags() & CueFlags.SLEEPY, "SLEEPY must not leak once AFK itself is hidden");
    }

    @Test
    void sleepySurvivesWhenIdleIsAllowed() {
        PlayerCue real = cue(Activity.AFK, ScreenKind.UNKNOWN, 0, CueFlags.SLEEPY);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, PolicyBits.ALL);
        assertEquals(Activity.AFK, masked.activity());
        assertTrue((masked.flags() & CueFlags.SLEEPY) != 0);
    }

    @Test
    void voiceBitOffDowngradesSpeakingToNormal() {
        int noVoice = PolicyBits.ALL & ~PolicyBits.VOICE;
        PlayerCue real = cue(Activity.SPEAKING, ScreenKind.UNKNOWN, 0, 0);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, noVoice);
        assertEquals(Activity.NORMAL, masked.activity());
    }

    @Test
    void intensityBitOffForcesZeroRegardlessOfActivity() {
        int noIntensity = PolicyBits.ALL & ~PolicyBits.INTENSITY;
        PlayerCue real = cue(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 255, 0);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, noIntensity);
        assertEquals(0, masked.intensity());
    }

    @Test
    void mutedSelfFlagOverridesEverythingEvenWithFullPolicy() {
        PlayerCue real = cue(Activity.TYPING_CHAT, ScreenKind.INVENTORY, 200, CueFlags.MUTED_SELF | CueFlags.SNEAKING);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, PolicyBits.ALL);
        assertEquals(new CueBatch.Entry(ID, Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), masked);
    }

    @Test
    void sneakingFlagIsNeverGatedByPolicy() {
        PlayerCue real = cue(Activity.NORMAL, ScreenKind.UNKNOWN, 0, CueFlags.SNEAKING);
        CueBatch.Entry masked = EffectivePolicy.applyNear(real, PolicyBits.NONE);
        assertEquals(CueFlags.SNEAKING, masked.flags());
    }

    // ---- global tier coarsening ------------------------------------------

    @Test
    void globalTierBitOffExcludesPlayerEntirely() {
        CueBatch.Entry nearMasked = new CueBatch.Entry(ID, Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 100, 0);
        Optional<CueBatch.Entry> coarse = EffectivePolicy.applyGlobalCoarse(nearMasked, PolicyBits.NONE);
        assertTrue(coarse.isEmpty());
    }

    @Test
    void globalTierBitOnAlwaysStripsScreenKindAndIntensity() {
        CueBatch.Entry nearMasked = new CueBatch.Entry(ID, Activity.IN_SCREEN, ScreenKind.ANVIL, 200, CueFlags.SNEAKING);
        Optional<CueBatch.Entry> coarse = EffectivePolicy.applyGlobalCoarse(nearMasked, PolicyBits.GLOBAL_TIER);
        assertTrue(coarse.isPresent());
        assertEquals(new CueBatch.Entry(ID, Activity.IN_SCREEN, ScreenKind.UNKNOWN, 0, 0), coarse.get());
    }

    @Test
    void globalAfkBitOffDowngradesAfkToNormalInGlobalTierOnly() {
        CueBatch.Entry nearMasked = new CueBatch.Entry(ID, Activity.AFK, ScreenKind.UNKNOWN, 0, CueFlags.SLEEPY);
        int globalTierNoAfk = PolicyBits.GLOBAL_TIER; // GLOBAL_AFK not set
        Optional<CueBatch.Entry> coarse = EffectivePolicy.applyGlobalCoarse(nearMasked, globalTierNoAfk);
        assertTrue(coarse.isPresent());
        assertEquals(Activity.NORMAL, coarse.get().activity());
    }

    @Test
    void globalAfkBitOnKeepsAfkVisibleInGlobalTier() {
        CueBatch.Entry nearMasked = new CueBatch.Entry(ID, Activity.AFK, ScreenKind.UNKNOWN, 0, 0);
        int both = PolicyBits.GLOBAL_TIER | PolicyBits.GLOBAL_AFK;
        Optional<CueBatch.Entry> coarse = EffectivePolicy.applyGlobalCoarse(nearMasked, both);
        assertEquals(Activity.AFK, coarse.orElseThrow().activity());
    }

    @Test
    void globalCoarsePreservesNonAfkActivityUnchanged() {
        CueBatch.Entry nearMasked = new CueBatch.Entry(ID, Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0);
        Optional<CueBatch.Entry> coarse = EffectivePolicy.applyGlobalCoarse(nearMasked, PolicyBits.GLOBAL_TIER);
        assertEquals(Activity.NORMAL, coarse.orElseThrow().activity());
        assertFalse(coarse.get().activity() == Activity.AFK);
    }
}
