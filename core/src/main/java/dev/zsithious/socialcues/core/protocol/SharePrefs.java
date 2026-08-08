package dev.zsithious.socialcues.core.protocol;

/** DESIGN.md §5 — C2S 0x03: `byte prefBits` (typing / screens / idle / voice paylaşımı). */
public record SharePrefs(int prefBits) implements C2SMessage {

    public static final int TYPE_ID = 0x03;

    public SharePrefs {
        WireChecks.requireUnsignedByte(prefBits, "prefBits");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void encode(ByteWriter writer) {
        writer.writeByte(prefBits);
    }

    public static SharePrefs decode(ByteReader reader) {
        return new SharePrefs(reader.readUnsignedByte());
    }
}
