package dev.zsithious.socialcues.core.protocol;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * Ordinal-based wire mapping for the two state enums, with strict range
 * checks on decode. Kept in core.protocol (not core.state) so the state
 * enums stay wire-format-agnostic.
 */
public final class EnumCodec {

    private static final Activity[] ACTIVITIES = Activity.values();
    private static final ScreenKind[] SCREEN_KINDS = ScreenKind.values();

    private EnumCodec() {
    }

    public static int toWire(Activity activity) {
        return activity.ordinal();
    }

    public static Activity activityFromWire(int wireValue) {
        if (wireValue < 0 || wireValue >= ACTIVITIES.length) {
            throw new ProtocolDecodeException("Activity ordinal out of range: " + wireValue);
        }
        return ACTIVITIES[wireValue];
    }

    public static int toWire(ScreenKind screenKind) {
        return screenKind.ordinal();
    }

    public static ScreenKind screenKindFromWire(int wireValue) {
        if (wireValue < 0 || wireValue >= SCREEN_KINDS.length) {
            throw new ProtocolDecodeException("ScreenKind ordinal out of range: " + wireValue);
        }
        return SCREEN_KINDS[wireValue];
    }
}
