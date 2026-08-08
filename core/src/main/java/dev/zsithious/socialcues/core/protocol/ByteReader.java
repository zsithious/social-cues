package dev.zsithious.socialcues.core.protocol;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Counterpart to {@link ByteWriter}. Every read is bounds-checked against
 * the actual remaining bytes before any allocation happens, so a hostile or
 * truncated packet fails fast with {@link ProtocolDecodeException} instead
 * of throwing an unrelated {@link ArrayIndexOutOfBoundsException} or, worse,
 * causing an oversized allocation from an attacker-controlled length.
 */
public final class ByteReader {

    private final byte[] data;
    private int pos;

    public ByteReader(byte[] data) {
        this.data = data;
        this.pos = 0;
    }

    public boolean hasRemaining() {
        return pos < data.length;
    }

    public int remaining() {
        return data.length - pos;
    }

    public int readUnsignedByte() {
        requireRemaining(1);
        return data[pos++] & 0xFF;
    }

    public int readVarInt() {
        return VarInt.read(this);
    }

    public String readString(int maxLength) {
        int length = readVarInt();
        if (length < 0) {
            throw new ProtocolDecodeException("Negative string length: " + length);
        }
        if (length > remaining()) {
            throw new ProtocolDecodeException(
                    "String declares length " + length + " but only " + remaining() + " bytes remain");
        }
        byte[] bytes = new byte[length];
        System.arraycopy(data, pos, bytes, 0, length);
        pos += length;
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > maxLength) {
            throw new ProtocolDecodeException(
                    "Decoded string length " + value.length() + " exceeds max " + maxLength);
        }
        return value;
    }

    public UUID readUuid() {
        long msb = readLong();
        long lsb = readLong();
        return new UUID(msb, lsb);
    }

    private long readLong() {
        requireRemaining(8);
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (data[pos++] & 0xFFL);
        }
        return value;
    }

    private void requireRemaining(int n) {
        if (remaining() < n) {
            throw new ProtocolDecodeException(
                    "Unexpected end of packet: needed " + n + " byte(s), had " + remaining());
        }
    }
}
