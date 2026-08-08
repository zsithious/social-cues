package dev.zsithious.socialcues.core.protocol;

/**
 * LEB128-style VarInt codec (Minecraft-protocol shaped), decoupled from any
 * particular buffer implementation — it only talks to {@link ByteWriter} /
 * {@link ByteReader} so it stays reusable and independently testable.
 */
public final class VarInt {

    /** A 32-bit int never needs more than 5 VarInt bytes. */
    public static final int MAX_BYTES = 5;

    private VarInt() {
    }

    public static void write(ByteWriter out, int value) {
        int remaining = value;
        for (int i = 0; i < MAX_BYTES; i++) {
            if ((remaining & ~0x7F) == 0) {
                out.writeByte(remaining);
                return;
            }
            out.writeByte((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        // Unreachable for a genuine 32-bit int, kept as a defensive guard.
        throw new IllegalStateException("VarInt encoding did not terminate within " + MAX_BYTES + " bytes");
    }

    public static int read(ByteReader in) {
        int value = 0;
        for (int i = 0; i < MAX_BYTES; i++) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << (i * 7);
            if ((b & 0x80) == 0) {
                return value;
            }
        }
        throw new ProtocolDecodeException("VarInt longer than " + MAX_BYTES + " bytes");
    }

    /** Encoded length in bytes for {@code value}, without actually encoding it. */
    public static int encodedSize(int value) {
        int size = 1;
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            remaining >>>= 7;
            size++;
        }
        return size;
    }
}
