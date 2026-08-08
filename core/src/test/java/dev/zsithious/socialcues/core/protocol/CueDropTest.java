package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CueDropTest {

    @Test
    void roundTripsEmptyDrop() {
        CueDrop original = new CueDrop(List.of());
        byte[] encoded = S2CMessages.encode(original);
        assertEquals(CueDrop.TYPE_ID, encoded[0] & 0xFF);
        assertEquals(original, S2CMessages.decode(encoded));
    }

    @Test
    void roundTripsMultipleIds() {
        CueDrop original = new CueDrop(List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        byte[] encoded = S2CMessages.encode(original);
        assertEquals(original, S2CMessages.decode(encoded));
    }

    @Test
    void decodeRejectsNegativeCount() {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(-5);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueDrop.decode(reader));
    }

    @Test
    void decodeRejectsCountLargerThanRemainingBytesCouldSupport() {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1_000_000);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueDrop.decode(reader));
    }

    @Test
    void decodeRejectsTruncatedUuid() {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        writer.writeByte(1); // only 1 of the 16 bytes a uuid needs
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> CueDrop.decode(reader));
    }
}
