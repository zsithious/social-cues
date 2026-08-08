package dev.zsithious.socialcues.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenKindTest {

    @Test
    void hasExactlyTheTwentyOneValuesFromDesign() {
        assertEquals(21, ScreenKind.values().length, "DESIGN.md §4 lists exactly 21 ScreenKind values");
    }

    @Test
    void ordinalsFitInAWireByte() {
        for (ScreenKind screenKind : ScreenKind.values()) {
            assertTrue(screenKind.ordinal() >= 0 && screenKind.ordinal() <= 255);
        }
    }

    @Test
    void moddedAndUnknownExistAsFallbackValues() {
        assertEquals(ScreenKind.MODDED, ScreenKind.valueOf("MODDED"));
        assertEquals(ScreenKind.UNKNOWN, ScreenKind.valueOf("UNKNOWN"));
    }
}
