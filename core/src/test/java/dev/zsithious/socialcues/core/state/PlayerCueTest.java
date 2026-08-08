package dev.zsithious.socialcues.core.state;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class PlayerCueTest {

    @Test
    void hasFlagChecksBitsIndependently() {
        PlayerCue cue = new PlayerCue(UUID.randomUUID(), Activity.AFK, ScreenKind.UNKNOWN,
                0, CueFlags.SLEEPY | CueFlags.MUTED_SELF, 0L);
        assertTrue(cue.hasFlag(CueFlags.SLEEPY));
        assertTrue(cue.hasFlag(CueFlags.MUTED_SELF));
        assertFalse(cue.hasFlag(CueFlags.SNEAKING));
        assertFalse(cue.hasFlag(CueFlags.REDUCED_DETAIL));
    }

    @Test
    void rejectsNullId() {
        assertThrows(NullPointerException.class,
                () -> new PlayerCue(null, Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, 0L));
    }

    @Test
    void rejectsIntensityOutOfRange() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerCue(id, Activity.NORMAL, ScreenKind.UNKNOWN, 256, 0, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerCue(id, Activity.NORMAL, ScreenKind.UNKNOWN, -1, 0, 0L));
    }

    @Test
    void rejectsFlagsOutOfRange() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new PlayerCue(id, Activity.NORMAL, ScreenKind.UNKNOWN, 0, 256, 0L));
    }
}
