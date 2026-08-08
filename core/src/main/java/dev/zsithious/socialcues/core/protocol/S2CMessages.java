package dev.zsithious.socialcues.core.protocol;

/** DESIGN.md §5 — same first-byte-is-type-id shape as {@link C2SMessages}, S2C side. */
public final class S2CMessages {

    private S2CMessages() {
    }

    public static byte[] encode(S2CMessage message) {
        ByteWriter writer = new ByteWriter();
        writer.writeByte(message.typeId());
        message.encode(writer);
        return writer.toByteArray();
    }

    public static S2CMessage decode(byte[] data) {
        ByteReader reader = new ByteReader(data);
        int typeId = reader.readUnsignedByte();
        return switch (typeId) {
            case ServerHello.TYPE_ID -> ServerHello.decode(reader);
            case CueBatch.TYPE_ID -> CueBatch.decode(reader);
            case CueDrop.TYPE_ID -> CueDrop.decode(reader);
            default -> throw new ProtocolDecodeException(
                    "Unknown S2C message type: 0x" + Integer.toHexString(typeId));
        };
    }
}
