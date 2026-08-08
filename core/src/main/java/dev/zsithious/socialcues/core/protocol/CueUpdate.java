package dev.zsithious.socialcues.core.protocol;

import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.ScreenKind;

/** DESIGN.md §5 — C2S 0x02: `byte activity, byte screenKind, byte intensity, byte flags`. */
public record CueUpdate(Activity activity, ScreenKind screenKind, int intensity, int flags) implements C2SMessage {

    public static final int TYPE_ID = 0x02;

    public CueUpdate {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(screenKind, "screenKind");
        WireChecks.requireUnsignedByte(intensity, "intensity");
        WireChecks.requireUnsignedByte(flags, "flags");
    }

    @Override
    public int typeId() {
        return TYPE_ID;
    }

    @Override
    public void encode(ByteWriter writer) {
        writer.writeByte(EnumCodec.toWire(activity));
        writer.writeByte(EnumCodec.toWire(screenKind));
        writer.writeByte(intensity);
        writer.writeByte(flags);
    }

    public static CueUpdate decode(ByteReader reader) {
        Activity activity = EnumCodec.activityFromWire(reader.readUnsignedByte());
        ScreenKind screenKind = EnumCodec.screenKindFromWire(reader.readUnsignedByte());
        int intensity = reader.readUnsignedByte();
        int flags = reader.readUnsignedByte();
        return new CueUpdate(activity, screenKind, intensity, flags);
    }
}
