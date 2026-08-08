package dev.zsithious.socialcues.core.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** DESIGN.md §5 — S2C 0x83: `varint count, count × uuid` (players that dropped out of view). */
public record CueDrop(List<UUID> ids) implements S2CMessage {

    public static final int TYPE_ID = 0x83;

    private static final int ENTRY_WIRE_SIZE = 16;

    public CueDrop {
        Objects.requireNonNull(ids, "ids");
        ids = List.copyOf(ids);
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void encode(ByteWriter writer) {
        writer.writeVarInt(ids.size());
        for (UUID id : ids) {
            writer.writeUuid(id);
        }
    }

    public static CueDrop decode(ByteReader reader) {
        int count = reader.readVarInt();
        if (count < 0) {
            throw new ProtocolDecodeException("Negative CueDrop count: " + count);
        }
        long requiredBytes = (long) count * ENTRY_WIRE_SIZE;
        if (requiredBytes > reader.remaining()) {
            throw new ProtocolDecodeException(
                    "CueDrop declares " + count + " entries but only " + reader.remaining() + " bytes remain");
        }
        List<UUID> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(reader.readUuid());
        }
        return new CueDrop(ids);
    }
}
