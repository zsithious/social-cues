package dev.zsithious.socialcues.core.protocol;

import java.util.Objects;

/** DESIGN.md §5 — C2S 0x01: `varint protoVersion, string modVersion (≤32), varint featureBits`. */
public record ClientHello(int protoVersion, String modVersion, int featureBits) implements C2SMessage {

    public static final int TYPE_ID = 0x01;

    public ClientHello {
        Objects.requireNonNull(modVersion, "modVersion");
        if (modVersion.length() > ProtocolConstants.MAX_MOD_VERSION_LENGTH) {
            throw new IllegalArgumentException(
                    "modVersion exceeds " + ProtocolConstants.MAX_MOD_VERSION_LENGTH + " chars");
        }
        WireChecks.requireNonNegative(protoVersion, "protoVersion");
        WireChecks.requireNonNegative(featureBits, "featureBits");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void encode(ByteWriter writer) {
        writer.writeVarInt(protoVersion);
        writer.writeString(modVersion, ProtocolConstants.MAX_MOD_VERSION_LENGTH);
        writer.writeVarInt(featureBits);
    }

    public static ClientHello decode(ByteReader reader) {
        int protoVersion = reader.readVarInt();
        String modVersion = reader.readString(ProtocolConstants.MAX_MOD_VERSION_LENGTH);
        int featureBits = reader.readVarInt();
        return new ClientHello(protoVersion, modVersion, featureBits);
    }
}
