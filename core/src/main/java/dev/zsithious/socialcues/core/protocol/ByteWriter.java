package dev.zsithious.socialcues.core.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Growable little writer for protocol v1 messages. Deliberately not backed
 * by Netty or any Minecraft buffer type — see DESIGN.md §5 ("Netty/Minecraft
 * buffer'ına bağımlı değil") — so the exact same class works for the Fabric
 * side (wrapped into a CustomPayload) and the Bukkit side (raw byte[]).
 */
public final class ByteWriter {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    public ByteWriter writeByte(int value) {
        buffer.write(value & 0xFF);
        return this;
    }

    public ByteWriter writeVarInt(int value) {
        VarInt.write(this, value);
        return this;
    }

    public ByteWriter writeString(String value, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(
                    "String length " + value.length() + " exceeds max " + maxLength);
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        buffer.write(bytes, 0, bytes.length);
        return this;
    }

    public ByteWriter writeUuid(UUID value) {
        writeLong(value.getMostSignificantBits());
        writeLong(value.getLeastSignificantBits());
        return this;
    }

    private void writeLong(long value) {
        for (int shift = 56; shift >= 0; shift -= 8) {
            buffer.write((int) (value >>> shift) & 0xFF);
        }
    }

    public byte[] toByteArray() {
        return buffer.toByteArray();
    }

    public int size() {
        return buffer.size();
    }
}
