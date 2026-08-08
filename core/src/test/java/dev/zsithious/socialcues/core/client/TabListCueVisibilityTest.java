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

/** DESIGN.md §7 Katman 2 / P4b task note §3.2: the tab list icon's own, simpler, rule set. */
class TabListCueVisibilityTest {

    private static final UUID ID = UUID.randomUUID();
    private static final String NAME = "Alex";

    @Test
    void ordinaryOtherPlayerRendersByDefault() {
        assertTrue(TabListCueVisibility.shouldRenderIcon(cue(Activity.IN_SCREEN, 0), ClientConfigData.defaults(), NAME));
    }

    @Test
    void layer2DisabledSuppressesEverything() {
        ClientConfigData config = withLayer2(false);
        assertFalse(TabListCueVisibility.shouldRenderIcon(cue(Activity.IN_SCREEN, 0), config, NAME));
    }

    @Test
    void normalActivityNeverRenders() {
        assertFalse(TabListCueVisibility.shouldRenderIcon(cue(Activity.NORMAL, 0), ClientConfigData.defaults(), NAME));
    }

    @Test
    void mutedSelfFlagSuppresses() {
        assertFalse(TabListCueVisibility.shouldRenderIcon(
                cue(Activity.AFK, CueFlags.MUTED_SELF), ClientConfigData.defaults(), NAME));
    }

    @Test
    void locallyMutedPlayerNameSuppresses() {
        ClientConfigData config = withMuted(Set.of(NAME.toLowerCase(java.util.Locale.ROOT)));
        assertFalse(TabListCueVisibility.shouldRenderIcon(cue(Activity.IN_SCREEN, 0), config, NAME));
    }

    @Test
    void muteListMatchIsCaseInsensitive() {
        ClientConfigData config = withMuted(Set.of("ALEX"));
        assertFalse(TabListCueVisibility.shouldRenderIcon(cue(Activity.IN_SCREEN, 0), config, NAME));
    }

    @Test
    void afkThroughTheGlobalCoarseTierStillRendersEvenWithoutTheSleepyFlag() {
        // core.policy.EffectivePolicy#applyGlobalCoarse always zeroes flags, so a
        // sleepy-but-globally-broadcast AFK cue arrives here with flags == 0 — this must
        // not be mistaken for "nothing to show"; it still renders as plain AFK.
        assertTrue(TabListCueVisibility.shouldRenderIcon(cue(Activity.AFK, 0), ClientConfigData.defaults(), NAME));
    }

    @Test
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class,
                () -> TabListCueVisibility.shouldRenderIcon(null, ClientConfigData.defaults(), NAME));
        assertThrows(NullPointerException.class,
                () -> TabListCueVisibility.shouldRenderIcon(cue(Activity.AFK, 0), null, NAME));
    }

    private static PlayerCue cue(Activity activity, int flags) {
        return new PlayerCue(ID, activity, ScreenKind.UNKNOWN, 0, flags, 0L);
    }

    private static ClientConfigData withLayer2(boolean layer2Enabled) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(d.layer1Enabled(), layer2Enabled, d.layer3Enabled(), d.scale(), d.opacity(),
                d.maxDistance(), d.showOnSelf(), d.reducedMotion(), d.textOnly(), d.shareTyping(), d.shareScreens(),
                d.shareScreenDetail(), d.shareIdle(), d.shareVoice(), d.mutedPlayers());
    }

    private static ClientConfigData withMuted(Set<String> mutedPlayers) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(d.layer1Enabled(), d.layer2Enabled(), d.layer3Enabled(), d.scale(), d.opacity(),
                d.maxDistance(), d.showOnSelf(), d.reducedMotion(), d.textOnly(), d.shareTyping(), d.shareScreens(),
                d.shareScreenDetail(), d.shareIdle(), d.shareVoice(), mutedPlayers);
    }
}
