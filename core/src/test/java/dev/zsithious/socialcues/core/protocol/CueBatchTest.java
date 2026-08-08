package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.ScreenKind;

class CueBatchTest {

    @Test
    void roundTripsEmptyBatch() {
        CueBatch original = new CueBatch(List.of());
        byte[] encoded = S2CMessages.encode(original);
        assertEquals(CueBatch.TYPE_ID, encoded[0] & 0xFF);
        assertEquals(original, S2CMessages.decode(encoded));
    }

    @Test
    void roundTripsMultipleEntries() {
        CueBatch original = new CueBatch(List.of(
                new CueBatch.Entry(UUID.randomUUID(), Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 100, 0),
                new CueBatch.Entry(UUID.randomUUID(), Activity.AFK, ScreenKind.UNKNOWN, 0, 4),
                new CueBatch.Entry(UUID.randomUUID(), Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0)
        ));
        byte[] encoded = S2CMessages.encode(original);
        assertEquals(original, S2CMessages.decode(encoded));
    }

    @Test
    void entryConstructorRejectsOutOfRangeIntensityOrFlags() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new CueBatch.Entry(id, Activity.NORMAL, ScreenKind.UNKNOWN, 256, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new CueBatch.Entry(id, Activity.NORMAL, ScreenKind.UNKNOWN, 0, -1));
    }

    @Test
    void decodeRejectsNegativeCount() {
        // Craft a raw body whose "count" varint decodes to -1.
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(-1);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueBatch.decode(reader));
    }

    @Test
    void decodeRejectsCountLargerThanRemainingBytesCouldSupport() {
        // count says "1,000,000 entries" but the body is empty afterwards —
        // must fail fast, not try to allocate a million-entry ArrayList.
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1_000_000);
        ByteReader reader = new ByteReader(writer.toByteArray());
        ProtocolDecodeException ex = assertThrows(ProtocolDecodeException.class, () -> CueBatch.decode(reader));
        assertTrue(ex.getMessage().contains("1000000"));
    }

    @Test
    void decodeRejectsTruncatedEntry() {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        writer.writeUuid(UUID.randomUUID());
        writer.writeByte(EnumCodec.toWire(Activity.NORMAL));
        // missing screenKind, intensity, flags for the declared entry
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueBatch.decode(reader));
    }

    @Test
    void decodeRejectsActivityOrdinalOutOfRangeInsideEntry() {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        writer.writeUuid(UUID.randomUUID());
        writer.writeByte(200); // invalid Activity ordinal
        writer.writeByte(EnumCodec.toWire(ScreenKind.UNKNOWN));
        writer.writeByte(0);
        writer.writeByte(0);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueBatch.decode(reader));
    }
}
