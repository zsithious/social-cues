package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.ScreenKind;

class EnumCodecTest {

    @Test
    void activityRoundTripsForEveryValue() {
        for (Activity activity : Activity.values()) {
            assertEquals(activity, EnumCodec.activityFromWire(EnumCodec.toWire(activity)));
        }
    }

    @Test
    void screenKindRoundTripsForEveryValue() {
        for (ScreenKind screenKind : ScreenKind.values()) {
            assertEquals(screenKind, EnumCodec.screenKindFromWire(EnumCodec.toWire(screenKind)));
        }
    }

    @Test
    void activityFromWireRejectsNegativeAndTooLarge() {
        assertThrows(ProtocolDecodeException.class, () -> EnumCodec.activityFromWire(-1));
        assertThrows(ProtocolDecodeException.class, () -> EnumCodec.activityFromWire(Activity.values().length));
        assertThrows(ProtocolDecodeException.class, () -> EnumCodec.activityFromWire(255));
    }

    @Test
    void screenKindFromWireRejectsNegativeAndTooLarge() {
        assertThrows(ProtocolDecodeException.class, () -> EnumCodec.screenKindFromWire(-1));
        assertThrows(ProtocolDecodeException.class, () -> EnumCodec.screenKindFromWire(ScreenKind.values().length));
        assertThrows(ProtocolDecodeException.class, () -> EnumCodec.screenKindFromWire(255));
    }
}
