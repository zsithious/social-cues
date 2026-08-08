package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VarIntTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 63, 127, 128, 129, 255, 16383, 16384, 2097151, 2097152,
            268435455, 268435456, Integer.MAX_VALUE, Integer.MIN_VALUE, -1, -128, -2097152})
    void roundTripsAndMatchesEncodedSize(int value) {
        ByteWriter writer = new ByteWriter();
        VarInt.write(writer, value);
        byte[] bytes = writer.toByteArray();

        assertEquals(VarInt.encodedSize(value), bytes.length,
                "encodedSize() must match the actual number of bytes written");

        ByteReader reader = new ByteReader(bytes);
        assertEquals(value, VarInt.read(reader));
        assertEquals(0, reader.remaining(), "reader should be fully consumed after one VarInt");
    }

    @org.junit.jupiter.api.Test
    void everyValueUsesAtMostFiveBytes() {
        assertEquals(5, VarInt.encodedSize(-1));
        assertEquals(5, VarInt.encodedSize(Integer.MIN_VALUE));
        assertEquals(5, VarInt.encodedSize(Integer.MAX_VALUE));
    }

    @org.junit.jupiter.api.Test
    void rejectsMoreThanFiveContinuationBytes() {
        // 6 bytes, every one with the continuation bit set: not a valid VarInt.
        byte[] malformed = { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF };
        ByteReader reader = new ByteReader(malformed);
        assertThrows(ProtocolDecodeException.class, () -> VarInt.read(reader));
    }

    @org.junit.jupiter.api.Test
    void rejectsTruncatedVarInt() {
        // Continuation bit set but no more bytes follow.
        byte[] truncated = { (byte) 0x80 };
        ByteReader reader = new ByteReader(truncated);
        assertThrows(ProtocolDecodeException.class, () -> VarInt.read(reader));
    }
}
