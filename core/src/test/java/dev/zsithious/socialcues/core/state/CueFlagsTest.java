package dev.zsithious.socialcues.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CueFlagsTest {

    @Test
    void flagsAreDistinctSingleBits() {
        int[] flags = { CueFlags.SNEAKING, CueFlags.REDUCED_DETAIL, CueFlags.SLEEPY, CueFlags.MUTED_SELF };
        for (int flag : flags) {
            assertEquals(1, Integer.bitCount(flag), "each flag must be exactly one bit");
        }
        int union = flags[0] | flags[1] | flags[2] | flags[3];
        assertEquals(flags.length, Integer.bitCount(union), "flags must not overlap");
    }

    @Test
    void allFlagsCombinedFitInAWireByte() {
        int all = CueFlags.SNEAKING | CueFlags.REDUCED_DETAIL | CueFlags.SLEEPY | CueFlags.MUTED_SELF;
        assertEquals(0b1111, all);
        assertEquals(true, all <= 255);
    }
}
