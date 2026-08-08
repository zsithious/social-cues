package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/** DESIGN.md §7 P4b: the AFK+SLEEPY substitution and the plain per-Activity fallback. */
class CueDisplaySelectorTest {

    private static final UUID ID = UUID.randomUUID();

    @Test
    void plainActivityUsesItsOwnAtlasCellAndLangKey() {
        PlayerCue typing = cue(Activity.TYPING_CHAT, 0);
        assertEquals(CueIconAtlas.cellFor(Activity.TYPING_CHAT), CueDisplaySelector.atlasCellFor(typing));
        assertEquals(CueLangKeys.keyFor(Activity.TYPING_CHAT), CueDisplaySelector.langKeyFor(typing));
    }

    @Test
    void afkWithoutSleepyUsesThePlainAfkCellAndKey() {
        PlayerCue afk = cue(Activity.AFK, 0);
        assertEquals(CueIconAtlas.cellFor(Activity.AFK), CueDisplaySelector.atlasCellFor(afk));
        assertEquals(CueLangKeys.keyFor(Activity.AFK), CueDisplaySelector.langKeyFor(afk));
    }

    @Test
    void afkWithSleepySubstitutesTheDedicatedSleepyCellAndKey() {
        PlayerCue sleepy = cue(Activity.AFK, CueFlags.SLEEPY);
        assertEquals(CueIconAtlas.SLEEPY_CELL, CueDisplaySelector.atlasCellFor(sleepy));
        assertEquals(CueLangKeys.SLEEPY_FLAG_KEY, CueDisplaySelector.langKeyFor(sleepy));
        // The sleepy cell/key must actually differ from the plain AFK ones, otherwise
        // the substitution above would be invisible/unreachable in practice.
        assertNotEquals(CueIconAtlas.cellFor(Activity.AFK), CueDisplaySelector.atlasCellFor(sleepy));
        assertNotEquals(CueLangKeys.keyFor(Activity.AFK), CueDisplaySelector.langKeyFor(sleepy));
    }

    @Test
    void sleepyFlagOnAnyOtherActivityIsIgnored() {
        // DESIGN.md §4: SLEEPY only ever means anything alongside AFK. A sender that
        // (incorrectly, or via a future bug upstream) sets SLEEPY while typing must not
        // trigger the AFK-only substitution.
        PlayerCue typingWithStraySleepyFlag = cue(Activity.TYPING_CHAT, CueFlags.SLEEPY);
        assertEquals(CueIconAtlas.cellFor(Activity.TYPING_CHAT), CueDisplaySelector.atlasCellFor(typingWithStraySleepyFlag));
        assertEquals(CueLangKeys.keyFor(Activity.TYPING_CHAT), CueDisplaySelector.langKeyFor(typingWithStraySleepyFlag));
    }

    @Test
    void nullCueRejected() {
        assertThrows(NullPointerException.class, () -> CueDisplaySelector.atlasCellFor(null));
        assertThrows(NullPointerException.class, () -> CueDisplaySelector.langKeyFor(null));
    }

    private static PlayerCue cue(Activity activity, int flags) {
        return new PlayerCue(ID, activity, ScreenKind.UNKNOWN, 0, flags, 0L);
    }
}
