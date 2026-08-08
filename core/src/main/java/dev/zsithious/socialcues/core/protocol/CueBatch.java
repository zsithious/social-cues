package dev.zsithious.socialcues.core.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §5 — S2C 0x82:
 * `varint count, count ×{ uuid, byte activity, byte screenKind, byte intensity, byte flags }`.
 */
public record CueBatch(List<Entry> entries) implements S2CMessage {

    public static final int TYPE_ID = 0x82;

    /** Fixed wire size of one entry: 16 (uuid) + 1 + 1 + 1 + 1 bytes. */
    private static final int ENTRY_WIRE_SIZE = 20;

    public CueBatch {
        Objects.requireNonNull(entries, "entries");
        entries = List.copyOf(entries);
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void encode(ByteWriter writer) {
        writer.writeVarInt(entries.size());
        for (Entry entry : entries) {
            writer.writeUuid(entry.id());
            writer.writeByte(EnumCodec.toWire(entry.activity()));
            writer.writeByte(EnumCodec.toWire(entry.screenKind()));
            writer.writeByte(entry.intensity());
            writer.writeByte(entry.flags());
        }
    }

    public static CueBatch decode(ByteReader reader) {
        int count = reader.readVarInt();
        if (count < 0) {
            throw new ProtocolDecodeException("Negative CueBatch entry count: " + count);
        }
        // Bound the declared count by what could possibly still be in the
        // packet, BEFORE allocating a list sized off attacker-controlled
        // input — otherwise a truncated packet claiming e.g. count=2^30
        // would blow up the heap before the per-entry read ever fails.
        long requiredBytes = (long) count * ENTRY_WIRE_SIZE;
        if (requiredBytes > reader.remaining()) {
            throw new ProtocolDecodeException(
                    "CueBatch declares " + count + " entries but only " + reader.remaining() + " bytes remain");
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            UUID id = reader.readUuid();
            Activity activity = EnumCodec.activityFromWire(reader.readUnsignedByte());
            ScreenKind screenKind = EnumCodec.screenKindFromWire(reader.readUnsignedByte());
            int intensity = reader.readUnsignedByte();
            int flags = reader.readUnsignedByte();
            entries.add(new Entry(id, activity, screenKind, intensity, flags));
        }
        return new CueBatch(entries);
    }

    /** One player's wire-shaped cue. Unlike {@code PlayerCue}, carries no local-only fields. */
    public record Entry(UUID id, Activity activity, ScreenKind screenKind, int intensity, int flags) {

        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(activity, "activity");
            Objects.requireNonNull(screenKind, "screenKind");
            WireChecks.requireUnsignedByte(intensity, "intensity");
            WireChecks.requireUnsignedByte(flags, "flags");
        }
    }
}
