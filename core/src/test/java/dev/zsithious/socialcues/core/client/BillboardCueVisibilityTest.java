package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/** DESIGN.md §7 Katman 1 / P4b task note §3.2: every independent "should render" rule. */
class BillboardCueVisibilityTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String NAME = "Steve";

    @Test
    void ordinaryOtherPlayerWithinRangeRendersByDefault() {
        assertTrue(BillboardCueVisibility.shouldRender(
                cue(Activity.TYPING_CHAT, 0), false, false, 10.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void layer1DisabledSuppressesEverything() {
        ClientConfigData config = withLayer1(false);
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.TYPING_CHAT, 0), false, false, 10.0, config, NAME));
    }

    @Test
    void normalActivityNeverRenders() {
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.NORMAL, 0), false, false, 10.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void mutedSelfFlagSuppressesRegardlessOfActivity() {
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.AFK, CueFlags.MUTED_SELF), false, false, 10.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void locallyMutedPlayerNameSuppresses() {
        ClientConfigData config = withMuted(Set.of(NAME.toLowerCase(java.util.Locale.ROOT)));
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.TYPING_CHAT, 0), false, false, 10.0, config, NAME));
    }

    @Test
    void selfDefaultsToHiddenEvenInThirdPerson() {
        // ClientConfigData.defaults().showOnSelf() == false.
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.TYPING_CHAT, 0), true, true, 5.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void selfWithShowOnSelfEnabledStillHiddenInFirstPerson() {
        ClientConfigData config = withShowOnSelf(true);
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.TYPING_CHAT, 0), true, false, 5.0, config, NAME));
    }

    @Test
    void selfWithShowOnSelfEnabledRendersInThirdPerson() {
        ClientConfigData config = withShowOnSelf(true);
        assertTrue(BillboardCueVisibility.shouldRender(
                cue(Activity.TYPING_CHAT, 0), true, true, 5.0, config, NAME));
    }

    @Test
    void exactlyAtMaxDistanceStillRenders() {
        ClientConfigData config = withMaxDistance(20.0);
        assertTrue(BillboardCueVisibility.shouldRender(
                cue(Activity.AFK, 0), false, false, 20.0, config, NAME));
    }

    @Test
    void beyondMaxDistanceDoesNotRender() {
        ClientConfigData config = withMaxDistance(20.0);
        assertFalse(BillboardCueVisibility.shouldRender(
                cue(Activity.AFK, 0), false, false, 20.01, config, NAME));
    }

    @Test
    void reducedDetailFlagAloneDoesNotSuppressLayer1() {
        // Documents the intentional non-effect explained in BillboardCueVisibility's
        // Javadoc: Layer 1 has no per-ScreenKind detail to lose, so REDUCED_DETAIL is
        // not a visibility gate here, unlike MUTED_SELF.
        assertTrue(BillboardCueVisibility.shouldRender(
                cue(Activity.IN_SCREEN, CueFlags.REDUCED_DETAIL), false, false, 5.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class, () -> BillboardCueVisibility.shouldRender(
                null, false, false, 1.0, ClientConfigData.defaults(), NAME));
        assertThrows(NullPointerException.class, () -> BillboardCueVisibility.shouldRender(
                cue(Activity.AFK, 0), false, false, 1.0, null, NAME));
    }

    // --------------------------------------------------- passesSharedRules (P6 §4.4)

    /**
     * The exact regression the {@code passesSharedRules}/{@code shouldRender}
     * split (P6 §4.4) exists to prevent: before the split, every rule below
     * lived inside {@code shouldRender} together with the {@code
     * layer1Enabled} check, so a caller with no separate way to skip just that
     * one check — Layer 3's held panel/pose, which reused {@code
     * shouldRender} wholesale — had Layer 1 being switched off silently kill
     * it too. {@code passesSharedRules} is what Layer 3 is meant to call
     * instead, so it must not know or care what {@code layer1Enabled} says.
     */
    @Test
    void passesSharedRulesIgnoresLayer1EnabledTheRegressionThatMotivatedTheSplit() {
        ClientConfigData config = withLayer1(false);
        assertTrue(BillboardCueVisibility.passesSharedRules(
                cue(Activity.TYPING_CHAT, 0), false, false, 10.0, config, NAME));
    }

    @Test
    void shouldRenderIsExactlyLayer1EnabledAndPassesSharedRules() {
        PlayerCue cue = cue(Activity.TYPING_CHAT, 0);
        ClientConfigData layer1On = ClientConfigData.defaults();
        ClientConfigData layer1Off = withLayer1(false);

        assertTrue(BillboardCueVisibility.passesSharedRules(cue, false, false, 10.0, layer1On, NAME));
        assertTrue(BillboardCueVisibility.shouldRender(cue, false, false, 10.0, layer1On, NAME));

        // Same shared-rule inputs, only layer1Enabled differs: passesSharedRules
        // must not move, shouldRender must track layer1Enabled exactly.
        assertTrue(BillboardCueVisibility.passesSharedRules(cue, false, false, 10.0, layer1Off, NAME));
        assertFalse(BillboardCueVisibility.shouldRender(cue, false, false, 10.0, layer1Off, NAME));
    }

    @Test
    void passesSharedRulesOrdinaryOtherPlayerWithinRangePassesByDefault() {
        assertTrue(BillboardCueVisibility.passesSharedRules(
                cue(Activity.TYPING_CHAT, 0), false, false, 10.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void passesSharedRulesNormalActivityNeverPasses() {
        assertFalse(BillboardCueVisibility.passesSharedRules(
                cue(Activity.NORMAL, 0), false, false, 10.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void passesSharedRulesMutedSelfFlagSuppressesRegardlessOfActivity() {
        assertFalse(BillboardCueVisibility.passesSharedRules(
                cue(Activity.AFK, CueFlags.MUTED_SELF), false, false, 10.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void passesSharedRulesLocallyMutedPlayerNameSuppresses() {
        ClientConfigData config = withMuted(Set.of(NAME.toLowerCase(java.util.Locale.ROOT)));
        assertFalse(BillboardCueVisibility.passesSharedRules(
                cue(Activity.TYPING_CHAT, 0), false, false, 10.0, config, NAME));
    }

    @Test
    void passesSharedRulesSelfDefaultsToHiddenEvenInThirdPerson() {
        assertFalse(BillboardCueVisibility.passesSharedRules(
                cue(Activity.TYPING_CHAT, 0), true, true, 5.0, ClientConfigData.defaults(), NAME));
    }

    @Test
    void passesSharedRulesSelfWithShowOnSelfEnabledStillHiddenInFirstPerson() {
        ClientConfigData config = withShowOnSelf(true);
        assertFalse(BillboardCueVisibility.passesSharedRules(
                cue(Activity.TYPING_CHAT, 0), true, false, 5.0, config, NAME));
    }

    @Test
    void passesSharedRulesSelfWithShowOnSelfEnabledPassesInThirdPerson() {
        ClientConfigData config = withShowOnSelf(true);
        assertTrue(BillboardCueVisibility.passesSharedRules(
                cue(Activity.TYPING_CHAT, 0), true, true, 5.0, config, NAME));
    }

    @Test
    void passesSharedRulesExactlyAtMaxDistanceStillPasses() {
        ClientConfigData config = withMaxDistance(20.0);
        assertTrue(BillboardCueVisibility.passesSharedRules(
                cue(Activity.AFK, 0), false, false, 20.0, config, NAME));
    }

    @Test
    void passesSharedRulesBeyondMaxDistanceFails() {
        ClientConfigData config = withMaxDistance(20.0);
        assertFalse(BillboardCueVisibility.passesSharedRules(
                cue(Activity.AFK, 0), false, false, 20.01, config, NAME));
    }

    @Test
    void passesSharedRulesNullArgumentsRejected() {
        assertThrows(NullPointerException.class, () -> BillboardCueVisibility.passesSharedRules(
                null, false, false, 1.0, ClientConfigData.defaults(), NAME));
        assertThrows(NullPointerException.class, () -> BillboardCueVisibility.passesSharedRules(
                cue(Activity.AFK, 0), false, false, 1.0, null, NAME));
    }

    private static PlayerCue cue(Activity activity, int flags) {
        return new PlayerCue(ID, activity, ScreenKind.UNKNOWN, 0, flags, 0L);
    }

    private static ClientConfigData withLayer1(boolean layer1Enabled) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(layer1Enabled, d.layer2Enabled(), d.layer3Enabled(), d.scale(), d.opacity(),
                d.maxDistance(), d.showOnSelf(), d.reducedMotion(), d.textOnly(), d.shareNothing(), d.shareTyping(), d.shareScreens(),
                d.shareScreenDetail(), d.shareIdle(), d.shareVoice(), d.mutedPlayers());
    }

    private static ClientConfigData withShowOnSelf(boolean showOnSelf) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(d.layer1Enabled(), d.layer2Enabled(), d.layer3Enabled(), d.scale(), d.opacity(),
                d.maxDistance(), showOnSelf, d.reducedMotion(), d.textOnly(), d.shareNothing(), d.shareTyping(), d.shareScreens(),
                d.shareScreenDetail(), d.shareIdle(), d.shareVoice(), d.mutedPlayers());
    }

    private static ClientConfigData withMaxDistance(double maxDistance) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(d.layer1Enabled(), d.layer2Enabled(), d.layer3Enabled(), d.scale(), d.opacity(),
                maxDistance, d.showOnSelf(), d.reducedMotion(), d.textOnly(), d.shareNothing(), d.shareTyping(), d.shareScreens(),
                d.shareScreenDetail(), d.shareIdle(), d.shareVoice(), d.mutedPlayers());
    }

    private static ClientConfigData withMuted(Set<String> mutedPlayers) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(d.layer1Enabled(), d.layer2Enabled(), d.layer3Enabled(), d.scale(), d.opacity(),
                d.maxDistance(), d.showOnSelf(), d.reducedMotion(), d.textOnly(), d.shareNothing(), d.shareTyping(), d.shareScreens(),
                d.shareScreenDetail(), d.shareIdle(), d.shareVoice(), mutedPlayers);
    }
}
