package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.ScreenKind;

class CueUpdateTest {

    @Test
    void roundTrips() {
        CueUpdate original = new CueUpdate(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 200,
                CueFlags.SNEAKING | CueFlags.REDUCED_DETAIL);
        byte[] encoded = C2SMessages.encode(original);
        assertEquals(original, C2SMessages.decode(encoded));
    }

    @Test
    void roundTripsEveryActivityAndScreenKind() {
        for (Activity activity : Activity.values()) {
            for (ScreenKind screenKind : ScreenKind.values()) {
                CueUpdate original = new CueUpdate(activity, screenKind, 0, 0);
                byte[] encoded = C2SMessages.encode(original);
                assertEquals(original, C2SMessages.decode(encoded));
            }
        }
    }

    @Test
    void intensityAndFlagsBoundaryValuesRoundTrip() {
        CueUpdate min = new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0);
        CueUpdate max = new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, 255, 255);
        assertEquals(min, C2SMessages.decode(C2SMessages.encode(min)));
        assertEquals(max, C2SMessages.decode(C2SMessages.encode(max)));
    }

    @Test
    void constructorRejectsIntensityOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, 256, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, -1, 0));
    }

    @Test
    void constructorRejectsFlagsOutOfRange() {
        assertThrows(IllegalArgumentException.class,
                () -> new CueUpdate(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 256));
    }

    @Test
    void decodeRejectsActivityOrdinalOutOfRange() {
        ByteWriter writer = new ByteWriter();
        writer.writeByte(99); // no Activity has ordinal 99
        writer.writeByte(EnumCodec.toWire(ScreenKind.UNKNOWN));
        writer.writeByte(0);
        writer.writeByte(0);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueUpdate.decode(reader));
    }

    @Test
    void decodeRejectsScreenKindOrdinalOutOfRange() {
        ByteWriter writer = new ByteWriter();
        writer.writeByte(EnumCodec.toWire(Activity.NORMAL));
        writer.writeByte(200); // no ScreenKind has ordinal 200
        writer.writeByte(0);
        writer.writeByte(0);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueUpdate.decode(reader));
    }

    @Test
    void decodeRejectsShortBody() {
        ByteWriter writer = new ByteWriter();
        writer.writeByte(EnumCodec.toWire(Activity.NORMAL));
        // missing screenKind, intensity, flags
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueUpdate.decode(reader));
    }
}
