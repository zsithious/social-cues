package dev.zsithious.socialcues.mcshared.network;

import java.util.logging.Logger;

import dev.zsithious.socialcues.core.handshake.ClientHandshake;
import dev.zsithious.socialcues.core.protocol.C2SMessages;
import dev.zsithious.socialcues.core.protocol.ClientHello;
import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.ProtocolDecodeException;
import dev.zsithious.socialcues.core.protocol.S2CMessage;
import dev.zsithious.socialcues.core.protocol.S2CMessages;
import dev.zsithious.socialcues.core.protocol.ServerHello;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Client side of DESIGN.md §5's handshake. Every transition decision lives
 * in {@link ClientHandshake} (pure Java, unit tested in {@code core}); this
 * class only wires Fabric's networking/tick events to it and performs the
 * actual send/decode. Only referenced from {@code SocialCuesClientInitializer}
 * (the {@code client} entrypoint) — never from the common entrypoint — so a
 * dedicated server never attempts to link client-only classes such as
 * {@link ClientPlayNetworking} or {@link net.minecraft.client.MinecraftClient}.
 */
public final class ClientHandshakeNetworking {

    /** Must match fabric.mod.json's "id". */
    private static final String MOD_ID = "socialcues";

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    private static final ClientHandshake HANDSHAKE = new ClientHandshake(
            System::currentTimeMillis, ClientHandshake.DEFAULT_TIMEOUT_MILLIS, LOGGER::info);

    private ClientHandshakeNetworking() {
    }

    /** For later phases (P3+) that need to know whether it's safe to send/render anything. Unused within P1 itself. */
    public static boolean isActive() {
        return HANDSHAKE.isActive();
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HANDSHAKE.reset();
            if (!ClientPlayNetworking.canSend(SocialCuesPayload.ID)) {
                // Pre-filter only (DESIGN.md's "ön filtre"): the true source of
                // truth is still whether a ServerHello ever arrives, handled
                // below in the receiver. This just avoids sending a doomed
                // ClientHello to a server we already know can't accept it.
                HANDSHAKE.onChannelNotAnnounced();
                return;
            }
            ClientHello hello = new ClientHello(ProtocolConstants.VERSION, modVersion(), 0);
            ClientPlayNetworking.send(new SocialCuesPayload(C2SMessages.encode(hello)));
            HANDSHAKE.onHelloSent();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HANDSHAKE.reset());

        ClientTickEvents.END_CLIENT_TICK.register(client -> HANDSHAKE.tick());

        ClientPlayNetworking.registerGlobalReceiver(SocialCuesPayload.ID, (payload, context) -> {
            S2CMessage message;
            try {
                message = S2CMessages.decode(payload.data());
            } catch (ProtocolDecodeException e) {
                return; // malformed packet, drop silently
            }
            if (message instanceof ServerHello hello) {
                HANDSHAKE.onServerHelloReceived(hello.protoVersion(), ProtocolConstants.VERSION);
            }
            // CueBatch/CueDrop: no relay/render consumer exists yet (P1 scope), ignored.
        });
    }

    private static String modVersion() {
        String version = FabricLoader.getInstance().getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        // Defensive clamp: ClientHello's compact constructor rejects anything
        // over ProtocolConstants.MAX_MOD_VERSION_LENGTH, and a version string
        // is not attacker-controlled but should never be able to crash the
        // handshake if some loader/repackaging tool ever produces a long one.
        int max = ProtocolConstants.MAX_MOD_VERSION_LENGTH;
        return version.length() > max ? version.substring(0, max) : version;
    }
}
