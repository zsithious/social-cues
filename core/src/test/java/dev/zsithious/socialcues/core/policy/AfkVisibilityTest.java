package dev.zsithious.socialcues.core.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** DESIGN.md §5's exact off/nearby/all -> bit 3 (IDLE) / bit 7 (GLOBAL_AFK) conversion table. */
class AfkVisibilityTest {

    @Test
    void offClearsBothBits() {
        int result = AfkVisibility.OFF.applyTo(PolicyBits.ALL);
        assertEquals(PolicyBits.ALL & ~(PolicyBits.IDLE | PolicyBits.GLOBAL_AFK), result);
    }

    @Test
    void nearbySetsIdleOnlyNotGlobalAfk() {
        int result = AfkVisibility.NEARBY.applyTo(PolicyBits.NONE);
        assertEquals(PolicyBits.IDLE, result);
    }

    @Test
    void allSetsBothIdleAndGlobalAfk() {
        int result = AfkVisibility.ALL.applyTo(PolicyBits.NONE);
        assertEquals(PolicyBits.IDLE | PolicyBits.GLOBAL_AFK, result);
    }

    @Test
    void doesNotDisturbUnrelatedBits() {
        int base = PolicyBits.TYPING | PolicyBits.SCREENS | PolicyBits.GLOBAL_TIER;
        assertEquals(base, AfkVisibility.OFF.applyTo(base));
        assertEquals(base | PolicyBits.IDLE, AfkVisibility.NEARBY.applyTo(base));
        assertEquals(base | PolicyBits.IDLE | PolicyBits.GLOBAL_AFK, AfkVisibility.ALL.applyTo(base));
    }

    @Test
    void fromConfigStringParsesAllThreeCaseInsensitively() {
        assertEquals(AfkVisibility.OFF, AfkVisibility.fromConfigString("off"));
        assertEquals(AfkVisibility.NEARBY, AfkVisibility.fromConfigString("Nearby"));
        assertEquals(AfkVisibility.ALL, AfkVisibility.fromConfigString("ALL"));
    }

    @Test
    void fromConfigStringRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> AfkVisibility.fromConfigString("everyone"));
    }
}
