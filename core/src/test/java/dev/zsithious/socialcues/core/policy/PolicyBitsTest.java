package dev.zsithious.socialcues.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** DESIGN.md §5's bit table, plus the named-switch builder used instead of a raw bitmask in config.yml. */
class PolicyBitsTest {

    @Test
    void bitsMatchDesignTablePositions() {
        assertEquals(1, PolicyBits.TYPING);
        assertEquals(1 << 1, PolicyBits.SCREENS);
        assertEquals(1 << 2, PolicyBits.SCREEN_DETAIL);
        assertEquals(1 << 3, PolicyBits.IDLE);
        assertEquals(1 << 4, PolicyBits.VOICE);
        assertEquals(1 << 5, PolicyBits.INTENSITY);
        assertEquals(1 << 6, PolicyBits.GLOBAL_TIER);
        assertEquals(1 << 7, PolicyBits.GLOBAL_AFK);
        assertEquals(0xFF, PolicyBits.ALL);
    }

    @Test
    void ofBuildsExactlyTheRequestedBits() {
        int bits = PolicyBits.of(true, false, true, false, true, false, AfkVisibility.OFF);
        assertEquals(PolicyBits.TYPING | PolicyBits.SCREEN_DETAIL | PolicyBits.INTENSITY, bits);
    }

    @Test
    void ofDelegatesAfkBitsToAfkVisibility() {
        int allOn = PolicyBits.of(true, true, true, true, true, true, AfkVisibility.ALL);
        assertEquals(PolicyBits.ALL, allOn);

        int noAfk = PolicyBits.of(true, true, true, true, true, true, AfkVisibility.OFF);
        assertEquals(PolicyBits.ALL & ~(PolicyBits.IDLE | PolicyBits.GLOBAL_AFK), noAfk);
    }

    @Test
    void ofWithEverythingFalseAndAfkOffIsNone() {
        assertEquals(PolicyBits.NONE, PolicyBits.of(false, false, false, false, false, false, AfkVisibility.OFF));
    }
}
