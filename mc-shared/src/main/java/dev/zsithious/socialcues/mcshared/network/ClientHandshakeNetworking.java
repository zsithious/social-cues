package dev.zsithious.socialcues.mcshared.network;

import java.util.logging.Logger;

import dev.zsithious.socialcues.core.handshake.ClientHandshake;
import dev.zsithious.socialcues.core.protocol.C2SMessages;
import dev.zsithious.socialcues.core.protocol.ClientHello;
import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueDrop;
import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.ProtocolDecodeException;
import dev.zsithious.socialcues.core.protocol.S2CMessage;
import dev.zsithious.socialcues.core.protocol.S2CMessages;
import dev.zsithious.socialcues.core.protocol.ServerHello;
import dev.zsithious.socialcues.mcshared.client.ClientCueCapture;
import dev.zsithious.socialcues.mcshared.client.PoseBlendDriver;
import dev.zsithious.socialcues.mcshared.client.RemoteCueStoreHolder;
import dev.zsithious.socialcues.mcshared.client.ServerPolicyState;

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
 *
 * <p>DESIGN.md §14 P4a: the receiver below is also the one place incoming
 * {@code CueBatch}/{@code CueDrop} messages get applied to {@link
 * RemoteCueStoreHolder}'s store — until P4a this comment used to read "no
 * relay/render consumer exists yet"; P4b's render code is that consumer now,
 * reading through {@link RemoteCueStoreHolder#get()}.
 */
public final class ClientHandshakeNetworking {

    /** Must match fabric.mod.json's "id". */
    private static final String MOD_ID = "socialcues";

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    private static final ClientHandshake HANDSHAKE = new ClientHandshake(
            System::currentTimeMillis, ClientHandshake.DEFAULT_TIMEOUT_MILLIS, LOGGER::info);

    private ClientHandshakeNetworking() {
    }

    /**
     * Whether it's safe to send/render anything. Unused within P1 itself;
     * {@code ClientCueCapture} (P3, DESIGN.md §14) is the first real caller —
     * it refuses to send a single {@code CueUpdate} while this is false.
     */
    public static boolean isActive() {
        return HANDSHAKE.isActive();
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            HANDSHAKE.reset();
            // P3 (DESIGN.md §14): a fresh join might be a different server
            // with different policy bits — never let P3's capture state
            // carry over from whatever the previous session last sent/saw.
            ServerPolicyState.reset();
            ClientCueCapture.reset();
            // P4a (DESIGN.md §14): same reasoning for what P4b's render code
            // will read — a fresh join must not still be showing cues left
            // over from a previous server (possibly a completely different
            // player set, or the same UUID meaning someone else entirely).
            RemoteCueStoreHolder.get().clear();
            // P5a (DESIGN.md §7 Katman 3): same reasoning again for Layer 3's
            // pose blends — a mid-fade pose from a previous server must not
            // survive into a new session either.
            PoseBlendDriver.reset();
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

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            HANDSHAKE.reset();
            ServerPolicyState.reset();
            ClientCueCapture.reset();
            RemoteCueStoreHolder.get().clear();
            PoseBlendDriver.reset();
        });

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
                if (HANDSHAKE.isActive()) {
                    // P3 (DESIGN.md §14): remember policyBits/idleThresholdTicks
                    // for ClientCueCapture. A version-mismatched ServerHello
                    // left the handshake DORMANT above, so this never stores
                    // policy from a ServerHello the client actually rejected.
                    ServerPolicyState.update(hello.policyBits(), hello.idleThresholdTicks());
                }
            } else if (message instanceof CueBatch batch) {
                // P4a (DESIGN.md §14): fed straight to the store P4b's render
                // code reads. Guarded by isActive() defensively — a
                // well-behaved relay only ever sends these after its own
                // ServerHello was accepted, but a dormant/mismatched client
                // must never start accumulating state for a session it
                // considers inactive (see RemoteCueStore's Javadoc: it trusts
                // nothing beyond what the wire types themselves guarantee).
                if (HANDSHAKE.isActive()) {
                    RemoteCueStoreHolder.get().applyBatch(batch, System.currentTimeMillis());
                }
            } else if (message instanceof CueDrop drop) {
                if (HANDSHAKE.isActive()) {
                    RemoteCueStoreHolder.get().applyDrop(drop);
                }
            }
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
