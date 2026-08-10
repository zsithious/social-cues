package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.policy.PolicyBits;

/**
 * DESIGN.md §9 P4a: default values, numeric clamping, mute-list
 * normalization, and the {@code SharePrefs.prefBits} derivation this class
 * feeds into {@code mcshared.client.ClientCueCapture}.
 */
class ClientConfigDataTest {

    private static ClientConfigData withScale(double scale) {
        return builder().scaleField(scale).build();
    }

    // Small hand-rolled builder so individual tests don't have to spell out
    // all 15 record components every time; kept private to this test class.
    private static Builder builder() {
        return new Builder();
    }

    private static final class Builder {
        boolean layer1 = true;
        boolean layer2 = true;
        boolean layer3 = true;
        double scale = ClientConfigData.DEFAULT_SCALE;
        double opacity = ClientConfigData.DEFAULT_OPACITY;
        double maxDistance = ClientConfigData.DEFAULT_MAX_DISTANCE;
        boolean showOnSelf = false;
        boolean reducedMotion = false;
        boolean textOnly = false;
        boolean shareNothing = false;
        boolean shareTyping = true;
        boolean shareScreens = true;
        boolean shareScreenDetail = true;
        boolean shareIdle = true;
        boolean shareVoice = true;
        Set<String> mutedPlayers = Set.of();

        Builder scaleField(double v) {
            scale = v;
            return this;
        }

        Builder opacityField(double v) {
            opacity = v;
            return this;
        }

        Builder maxDistanceField(double v) {
            maxDistance = v;
            return this;
        }

        Builder shareNothingField(boolean v) {
            shareNothing = v;
            return this;
        }

        Builder shareScreensField(boolean v) {
            shareScreens = v;
            return this;
        }

        Builder shareScreenDetailField(boolean v) {
            shareScreenDetail = v;
            return this;
        }

        Builder shareTypingField(boolean v) {
            shareTyping = v;
            return this;
        }

        Builder shareIdleField(boolean v) {
            shareIdle = v;
            return this;
        }

        Builder shareVoiceField(boolean v) {
            shareVoice = v;
            return this;
        }

        Builder mutedPlayersField(Set<String> v) {
            mutedPlayers = v;
            return this;
        }

        ClientConfigData build() {
            return new ClientConfigData(layer1, layer2, layer3, scale, opacity, maxDistance, showOnSelf,
                    reducedMotion, textOnly, shareNothing, shareTyping, shareScreens, shareScreenDetail, shareIdle,
                    shareVoice, mutedPlayers);
        }
    }

    @Test
    void defaultsMatchDesignDocDefaultValues() {
        ClientConfigData defaults = ClientConfigData.defaults();
        assertTrue(defaults.layer1Enabled());
        assertTrue(defaults.layer2Enabled());
        assertTrue(defaults.layer3Enabled());
        assertEquals(ClientConfigData.DEFAULT_SCALE, defaults.scale());
        assertEquals(ClientConfigData.DEFAULT_OPACITY, defaults.opacity());
        assertEquals(ClientConfigData.DEFAULT_MAX_DISTANCE, defaults.maxDistance());
        // DESIGN.md §7 "Kendi oyuncusu": self-indicators default off.
        assertFalse(defaults.showOnSelf());
        assertFalse(defaults.reducedMotion());
        assertFalse(defaults.textOnly());
        assertFalse(defaults.shareNothing());
        assertTrue(defaults.shareTyping());
        assertTrue(defaults.shareScreens());
        assertTrue(defaults.shareScreenDetail());
        assertTrue(defaults.shareIdle());
        assertTrue(defaults.shareVoice());
        assertTrue(defaults.mutedPlayers().isEmpty());
    }

    @Test
    void defaultsPrefBitsEqualsAllBitsSetMatchingPriorAllEnabledBehavior() {
        // P3 wired SharePrefsSource.allEnabled() (== PolicyBits.ALL) as the
        // only implementation that existed yet; the out-of-the-box config
        // must not silently narrow what P3 already shared by default.
        assertEquals(PolicyBits.ALL, ClientConfigData.defaults().prefBits());
    }

    @Test
    void scaleIsClampedToItsValidRange() {
        assertEquals(ClientConfigData.MIN_SCALE, withScale(-5.0).scale());
        assertEquals(ClientConfigData.MIN_SCALE, withScale(0.0).scale());
        assertEquals(ClientConfigData.MAX_SCALE, withScale(999.0).scale());
        assertEquals(2.0, withScale(2.0).scale());
    }

    @Test
    void nanScaleClampsToMinimum() {
        assertEquals(ClientConfigData.MIN_SCALE, withScale(Double.NaN).scale());
    }

    @Test
    void opacityIsClampedToZeroOne() {
        ClientConfigData tooLow = builder().opacityField(-1.0).build();
        ClientConfigData tooHigh = builder().opacityField(5.0).build();
        assertEquals(ClientConfigData.MIN_OPACITY, tooLow.opacity());
        assertEquals(ClientConfigData.MAX_OPACITY, tooHigh.opacity());
    }

    @Test
    void maxDistanceIsClampedToItsValidRange() {
        ClientConfigData tooLow = builder().maxDistanceField(-10.0).build();
        ClientConfigData tooHigh = builder().maxDistanceField(10_000.0).build();
        assertEquals(ClientConfigData.MIN_MAX_DISTANCE, tooLow.maxDistance());
        assertEquals(ClientConfigData.MAX_MAX_DISTANCE, tooHigh.maxDistance());
    }

    @Test
    void shareScreenDetailIsForcedFalseWhenScreensNotShared() {
        ClientConfigData data = builder().shareScreensField(false).shareScreenDetailField(true).build();
        assertFalse(data.shareScreenDetail());
    }

    @Test
    void shareScreenDetailStaysTrueWhenScreensAreShared() {
        ClientConfigData data = builder().shareScreensField(true).shareScreenDetailField(true).build();
        assertTrue(data.shareScreenDetail());
    }

    @Test
    void mutedPlayersAreNormalizedToLowerCaseTrimmedAndDeduped() {
        Set<String> raw = new LinkedHashSet<>();
        raw.add("  Steve ");
        raw.add("STEVE");
        raw.add("Alex");
        raw.add("");
        raw.add("   ");

        ClientConfigData data = builder().mutedPlayersField(raw).build();

        assertEquals(Set.of("steve", "alex"), data.mutedPlayers());
    }

    @Test
    void mutedPlayersSilentlyDropsNullEntries() {
        Set<String> raw = new LinkedHashSet<>();
        raw.add("Steve");
        raw.add(null);

        ClientConfigData data = builder().mutedPlayersField(raw).build();

        assertEquals(Set.of("steve"), data.mutedPlayers());
    }

    @Test
    void nullMutedPlayersSetIsRejected() {
        assertThrows(NullPointerException.class, () -> builder().mutedPlayersField(null).build());
    }

    @Test
    void isMutedIsCaseInsensitive() {
        ClientConfigData data = builder().mutedPlayersField(Set.of("Steve")).build();
        assertTrue(data.isMuted("steve"));
        assertTrue(data.isMuted("STEVE"));
        assertTrue(data.isMuted("StEvE"));
        assertFalse(data.isMuted("Alex"));
    }

    @Test
    void isMutedIsNullSafe() {
        ClientConfigData data = ClientConfigData.defaults();
        assertFalse(data.isMuted(null));
    }

    @Test
    void prefBitsReflectsEachToggleIndependently() {
        ClientConfigData allOff = builder()
                .shareTypingField(false).shareScreensField(false).shareScreenDetailField(false)
                .shareIdleField(false).shareVoiceField(false).build();
        // GLOBAL_TIER is always requested (see prefBits' Javadoc) even with
        // every signal off — matching PolicyBits.NONE plus that one bit.
        assertEquals(PolicyBits.GLOBAL_TIER, allOff.prefBits());
    }

    @Test
    void sharingTypingImpliesIntensityBit() {
        ClientConfigData data = builder()
                .shareTypingField(true).shareScreensField(false).shareScreenDetailField(false)
                .shareIdleField(false).shareVoiceField(false).build();
        int expected = PolicyBits.TYPING | PolicyBits.INTENSITY | PolicyBits.GLOBAL_TIER;
        assertEquals(expected, data.prefBits());
    }

    @Test
    void sharingIdleImpliesGlobalAfkBit() {
        ClientConfigData data = builder()
                .shareTypingField(false).shareScreensField(false).shareScreenDetailField(false)
                .shareIdleField(true).shareVoiceField(false).build();
        int expected = PolicyBits.IDLE | PolicyBits.GLOBAL_AFK | PolicyBits.GLOBAL_TIER;
        assertEquals(expected, data.prefBits());
    }

    @Test
    void prefBitsImplementsSharePrefsSource() {
        ClientConfigData data = ClientConfigData.defaults();
        SharePrefsSource asSource = data;
        assertEquals(data.prefBits(), asSource.prefBits());
    }

    // ------------------------------------------------------- shareNothing (P6 §3)

    @Test
    void shareNothingSuspendsTheShareFlagsWithoutErasingThem() {
        // The point of the rule, and the reason it is enforced in prefBits()
        // instead of the compact constructor: a master switch the user turns
        // back off must give them back the five choices they made, not five
        // silently-off switches. Persisting the erasure would make "share
        // nothing" a one-way door that looks like data loss.
        ClientConfigData data = builder()
                .shareNothingField(true)
                .shareTypingField(true).shareScreensField(true).shareScreenDetailField(true)
                .shareIdleField(true).shareVoiceField(true)
                .build();
        assertTrue(data.shareTyping());
        assertTrue(data.shareScreens());
        assertTrue(data.shareScreenDetail());
        assertTrue(data.shareIdle());
        assertTrue(data.shareVoice());
        assertEquals(PolicyBits.NONE, data.prefBits(), "nothing may leave this machine while shareNothing is set");
    }

    @Test
    void shareNothingFalseLeavesEveryShareFlagAsRequested() {
        ClientConfigData data = builder()
                .shareNothingField(false)
                .shareTypingField(true).shareScreensField(true).shareScreenDetailField(true)
                .shareIdleField(true).shareVoiceField(true)
                .build();
        assertTrue(data.shareTyping());
        assertTrue(data.shareScreens());
        assertTrue(data.shareScreenDetail());
        assertTrue(data.shareIdle());
        assertTrue(data.shareVoice());
    }

    @Test
    void shareNothingPrefBitsIsNoneIncludingGlobalTier() {
        // Unlike prefBitsReflectsEachToggleIndependently's "all five signals off"
        // case (which still carries GLOBAL_TIER, see that test), shareNothing
        // drops GLOBAL_TIER too -- see prefBits()'s own Javadoc for why opting out
        // of every signal has to mean opting out of the coarse tier as well.
        ClientConfigData data = builder()
                .shareNothingField(true)
                .shareTypingField(true).shareScreensField(true).shareScreenDetailField(true)
                .shareIdleField(true).shareVoiceField(true)
                .build();
        assertEquals(PolicyBits.NONE, data.prefBits());
    }
}
