package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class ByteReaderWriterTest {

    @Test
    void roundTripsUnsignedByte() {
        ByteWriter writer = new ByteWriter();
        writer.writeByte(0).writeByte(255).writeByte(128);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertEquals(0, reader.readUnsignedByte());
        assertEquals(255, reader.readUnsignedByte());
        assertEquals(128, reader.readUnsignedByte());
    }

    @Test
    void writeByteMasksToOneByte() {
        // Only the low 8 bits matter, mirroring how a wire byte field behaves.
        ByteWriter writer = new ByteWriter();
        writer.writeByte(0x1FF);
        assertEquals(0xFF, new ByteReader(writer.toByteArray()).readUnsignedByte());
    }

    @Test
    void roundTripsUuid() {
        UUID id = UUID.fromString("12345678-1234-5678-1234-567812345678");
        ByteWriter writer = new ByteWriter();
        writer.writeUuid(id);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertEquals(id, reader.readUuid());
    }

    @Test
    void roundTripsNilAndRandomUuids() {
        UUID nil = new UUID(0L, 0L);
        UUID random = UUID.randomUUID();
        ByteWriter writer = new ByteWriter();
        writer.writeUuid(nil);
        writer.writeUuid(random);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertEquals(nil, reader.readUuid());
        assertEquals(random, reader.readUuid());
    }

    @Test
    void roundTripsString() {
        ByteWriter writer = new ByteWriter();
        writer.writeString("hello", 32);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertEquals("hello", reader.readString(32));
    }

    @Test
    void roundTripsEmptyString() {
        ByteWriter writer = new ByteWriter();
        writer.writeString("", 32);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertEquals("", reader.readString(32));
    }

    @Test
    void writeStringRejectsOverLongInput() {
        assertThrows(IllegalArgumentException.class,
                () -> new ByteWriter().writeString("a".repeat(33), 32));
    }

    @Test
    void readStringRejectsDeclaredLengthLongerThanRemainingBytes() {
        // A crafted "length prefix says more than actually follows" packet.
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1000); // declares 1000 bytes of string content
        writer.writeByte('a');    // but only supplies one
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> reader.readString(32));
    }

    @Test
    void readStringRejectsDecodedLengthOverMax() {
        // Valid UTF-8, fully present, but longer than the field's char cap.
        ByteWriter writer = new ByteWriter();
        writer.writeString("a".repeat(40), 1000); // write without the 32-cap check
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> reader.readString(32));
    }

    @Test
    void readUnsignedByteRejectsEmptyInput() {
        ByteReader reader = new ByteReader(new byte[0]);
        assertThrows(ProtocolDecodeException.class, reader::readUnsignedByte);
    }

    @Test
    void readUuidRejectsTruncatedInput() {
        ByteReader reader = new ByteReader(new byte[10]); // needs 16
        assertThrows(ProtocolDecodeException.class, reader::readUuid);
    }

    @Test
    void hasRemainingAndRemainingAgree() {
        ByteReader reader = new ByteReader(new byte[]{1, 2});
        assertEquals(2, reader.remaining());
        reader.readUnsignedByte();
        assertEquals(1, reader.remaining());
        reader.readUnsignedByte();
        assertEquals(0, reader.remaining());
        assertEquals(false, reader.hasRemaining());
    }
}
