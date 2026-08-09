package dev.zsithious.socialcues.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ScreenKindTest {

    @Test
    void hasExactlyTheTwentyFiveValuesFromDesign() {
        // DESIGN.md §4 originally listed 21; DESIGN.md §7's P5 hand-test fix
        // (HATA7) appended CONTAINER_SMALL/HOPPER/SHULKER/DISPENSER at the end
        // (ordinal-coded on the wire -- see this enum's own Javadoc on why
        // appending, not inserting, was the safe choice), bringing this to 25.
        assertEquals(25, ScreenKind.values().length, "DESIGN.md §4/§7 list exactly 25 ScreenKind values");
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
