package dev.zsithious.socialcues.core.protocol;

/**
 * DESIGN.md §5 — S2C 0x81:
 * `varint protoVersion, varint policyBits, varint idleThresholdTicks, varint updateIntervalTicks, varint nearRadius`.
 */
public record ServerHello(int protoVersion, int policyBits, int idleThresholdTicks,
                           int updateIntervalTicks, int nearRadius) implements S2CMessage {

    public static final int TYPE_ID = 0x81;

    public ServerHello {
        WireChecks.requireNonNegative(protoVersion, "protoVersion");
        WireChecks.requireNonNegative(policyBits, "policyBits");
        WireChecks.requireNonNegative(idleThresholdTicks, "idleThresholdTicks");
        WireChecks.requireNonNegative(updateIntervalTicks, "updateIntervalTicks");
        WireChecks.requireNonNegative(nearRadius, "nearRadius");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void encode(ByteWriter writer) {
        writer.writeVarInt(protoVersion);
        writer.writeVarInt(policyBits);
        writer.writeVarInt(idleThresholdTicks);
        writer.writeVarInt(updateIntervalTicks);
        writer.writeVarInt(nearRadius);
    }

    public static ServerHello decode(ByteReader reader) {
        int protoVersion = reader.readVarInt();
        int policyBits = reader.readVarInt();
        int idleThresholdTicks = reader.readVarInt();
        int updateIntervalTicks = reader.readVarInt();
        int nearRadius = reader.readVarInt();
        return new ServerHello(protoVersion, policyBits, idleThresholdTicks, updateIntervalTicks, nearRadius);
    }
}
