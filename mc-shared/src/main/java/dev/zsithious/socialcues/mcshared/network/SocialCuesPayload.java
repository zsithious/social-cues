package dev.zsithious.socialcues.mcshared.network;

import dev.zsithious.socialcues.core.protocol.ProtocolConstants;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The single Fabric {@link CustomPayload} used for both C2S and S2C traffic
 * on the {@code socialcues:v1} channel (DESIGN.md §5). It is a pure
 * byte-array carrier: the *meaning* of the bytes (message type, fields) is
 * entirely owned by {@code core.protocol} ({@code C2SMessages}/
 * {@code S2CMessages}) — this class never inspects them.
 *
 * <p><b>Wire symmetry with the Paper plugin (critical — see P1 task notes
 * and DESIGN.md §5):</b> this codec must produce/consume exactly the bytes
 * that {@code C2SMessages.encode}/{@code S2CMessages.encode} produce, with
 * <em>no extra framing</em>. Bukkit's {@code Messenger.sendPluginMessage(
 * plugin, player, channel, byte[])} sends that {@code byte[]} verbatim as
 * the packet body — no length prefix, no wrapper. To stay byte-for-byte
 * compatible with that:
 * <ul>
 *   <li>encoding uses {@link PacketByteBuf#writeBytes(byte[])}, never
 *       {@code writeByteArray(byte[])} — the latter prepends a VarInt
 *       length that Bukkit's raw send never includes, which would corrupt
 *       the shared wire format the moment a Paper server and a Fabric
 *       client (or vice versa) talk to each other (P2);</li>
 *   <li>decoding drains {@link PacketByteBuf#readableBytes()} rather than
 *       assuming any fixed size, since {@code core.protocol} messages are
 *       variable-length (VarInts, strings, batch counts).</li>
 * </ul>
 */
public record SocialCuesPayload(byte[] data) implements CustomPayload {

    public static final CustomPayload.Id<SocialCuesPayload> ID =
            new CustomPayload.Id<>(Identifier.of(ProtocolConstants.CHANNEL));

    public static final PacketCodec<PacketByteBuf, SocialCuesPayload> CODEC = PacketCodec.of(
            (value, buf) -> buf.writeBytes(value.data()),
            buf -> {
                byte[] data = new byte[buf.readableBytes()];
                buf.readBytes(data);
                return new SocialCuesPayload(data);
            });

    @Override
    public CustomPayload.Id<SocialCuesPayload> getId() {
        return ID;
    }
}
