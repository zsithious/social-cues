package dev.zsithious.socialcues.mcshared.network;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import dev.zsithious.socialcues.core.protocol.C2SMessage;
import dev.zsithious.socialcues.core.protocol.C2SMessages;
import dev.zsithious.socialcues.core.protocol.ClientHello;
import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.ProtocolDecodeException;
import dev.zsithious.socialcues.core.protocol.S2CMessages;
import dev.zsithious.socialcues.core.protocol.ServerHello;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Fabric (dedicated or integrated) server side of DESIGN.md §5's handshake.
 * Deliberately handshake-only (P1 scope, DESIGN.md §14): no relay, no
 * CueBatch broadcasting, no player-state storage — that lands with the
 * actual {@code CueRelay} work (P2 for Paper; a later phase for a Fabric
 * server relay). Mirrors DESIGN.md §8.2's join-timer + immediate-reply
 * behaviour so a Fabric-hosted server looks identical, on the wire, to a
 * Paper-hosted one.
 *
 * <p>All mutable state here is only ever touched from the server thread —
 * Fabric fires connection and tick events on it — so plain
 * {@link HashMap}/{@link HashSet} are enough; no concurrency control needed.
 */
public final class ServerHandshake {

    /** DESIGN.md §8.2 / P1 task note: "oyuncu katılınca ~20 tick sonra ServerHello". */
    private static final int HELLO_DELAY_TICKS = 20;

    /** DESIGN.md §6: AFK threshold default is 5 minutes. Real config arrives in P2/P6. */
    private static final int DEFAULT_IDLE_THRESHOLD_TICKS = 6000;
    /** DESIGN.md §5: near-layer default update cadence. */
    private static final int DEFAULT_UPDATE_INTERVAL_TICKS = 4;
    /** Placeholder until server config exists (P2/P6); unused by anything in P1. */
    private static final int DEFAULT_NEAR_RADIUS = 48;

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** UUID -> ticks remaining until the scheduled ServerHello fires. */
    private static final Map<UUID, Integer> pendingHelloTicks = new HashMap<>();
    /** Players already greeted this session — keeps the log to one line per handshake, not one per resend. */
    private static final Set<UUID> greeted = new HashSet<>();

    private ServerHandshake() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                pendingHelloTicks.put(handler.getPlayer().getUuid(), HELLO_DELAY_TICKS));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUuid();
            pendingHelloTicks.remove(id);
            greeted.remove(id);
        });

        ServerTickEvents.END_SERVER_TICK.register(ServerHandshake::tickPendingHellos);

        ServerPlayNetworking.registerGlobalReceiver(SocialCuesPayload.ID, (payload, context) -> {
            C2SMessage message;
            try {
                message = C2SMessages.decode(payload.data());
            } catch (ProtocolDecodeException e) {
                return; // malformed packet, drop silently (mirrors DESIGN.md §8.5's rule for the relay)
            }
            if (message instanceof ClientHello clientHello) {
                ServerPlayerEntity player = context.player();
                if (clientHello.protoVersion() != ProtocolConstants.VERSION) {
                    LOGGER.info("socialcues: " + player.getGameProfile().name()
                            + " sent ClientHello with protocol version " + clientHello.protoVersion()
                            + " (server is " + ProtocolConstants.VERSION + ")");
                }
                pendingHelloTicks.remove(player.getUuid());
                sendServerHello(player, "ClientHello received");
            }
            // CueUpdate/SharePrefs: no relay exists yet (P1 scope), ignored.
        });
    }

    private static void tickPendingHellos(MinecraftServer server) {
        if (pendingHelloTicks.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = pendingHelloTicks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            int remaining = entry.getValue() - 1;
            if (remaining > 0) {
                entry.setValue(remaining);
                continue;
            }
            iterator.remove();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) {
                sendServerHello(player, "join timer");
            }
        }
    }

    private static void sendServerHello(ServerPlayerEntity player, String reason) {
        if (!ServerPlayNetworking.canSend(player, SocialCuesPayload.ID)) {
            return; // this player's client never announced the channel; nothing to talk to
        }
        ServerHello hello = new ServerHello(
                ProtocolConstants.VERSION,
                0,
                DEFAULT_IDLE_THRESHOLD_TICKS,
                DEFAULT_UPDATE_INTERVAL_TICKS,
                DEFAULT_NEAR_RADIUS);
        ServerPlayNetworking.send(player, new SocialCuesPayload(S2CMessages.encode(hello)));
        if (greeted.add(player.getUuid())) {
            LOGGER.info("socialcues: handshake with " + player.getGameProfile().name()
                    + " complete (" + reason + ")");
        }
    }
}
