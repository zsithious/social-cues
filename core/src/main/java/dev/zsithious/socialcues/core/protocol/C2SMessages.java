package dev.zsithious.socialcues.core.protocol;

/**
 * DESIGN.md §5 — "gövdenin ilk baytı mesaj tipi": the first byte of the
 * whole payload is the type id, the rest is the message-specific body.
 */
public final class C2SMessages {

    private C2SMessages() {
    }

    public static byte[] encode(C2SMessage message) {
        ByteWriter writer = new ByteWriter();
        writer.writeByte(message.typeId());
        message.encode(writer);
        return writer.toByteArray();
    }

    public static C2SMessage decode(byte[] data) {
        ByteReader reader = new ByteReader(data);
        int typeId = reader.readUnsignedByte();
        return switch (typeId) {
            case ClientHello.TYPE_ID -> ClientHello.decode(reader);
            case CueUpdate.TYPE_ID -> CueUpdate.decode(reader);
            case SharePrefs.TYPE_ID -> SharePrefs.decode(reader);
            default -> throw new ProtocolDecodeException(
                    "Unknown C2S message type: 0x" + Integer.toHexString(typeId));
        };
    }
}
