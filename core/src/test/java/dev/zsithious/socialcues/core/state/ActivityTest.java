package dev.zsithious.socialcues.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActivityTest {

    @Test
    void hasExactlyTheEightValuesFromDesign() {
        Activity[] values = Activity.values();
        assertEquals(8, values.length, "DESIGN.md §4 lists exactly 8 Activity values");
    }

    @Test
    void ordinalsFitInAWireByte() {
        for (Activity activity : Activity.values()) {
            assertTrue(activity.ordinal() >= 0 && activity.ordinal() <= 255);
        }
    }

    @Test
    void valueOfIsStable() {
        assertEquals(Activity.NORMAL, Activity.valueOf("NORMAL"));
        assertEquals(Activity.TYPING_COMMAND, Activity.valueOf("TYPING_COMMAND"));
        assertEquals(Activity.SPEAKING, Activity.valueOf("SPEAKING"));
    }
}
