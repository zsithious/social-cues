package dev.zsithious.socialcues.paper.network;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.S2CMessages;
import dev.zsithious.socialcues.core.protocol.ServerHello;
import dev.zsithious.socialcues.core.relay.CueRelay;
import dev.zsithious.socialcues.core.relay.IngestOutcome;
import dev.zsithious.socialcues.paper.config.PluginConfig;

/**
 * DESIGN.md §8.1/§8.2/§8.5: the Bukkit half of the {@code socialcues:v1}
 * wire. Every decision (what counts as a violation, when to kick, what
 * effective permission a player has) is made by {@code core.relay.CueRelay};
 * this class only turns raw bytes into an {@link IngestOutcome} and turns
 * that outcome into the one platform action it implies (send a
 * {@code ServerHello}, log, or kick).
 */
public final class SocialCuesMessageListener implements PluginMessageListener {

    private final Plugin plugin;
    private final CueRelay relay;
    private final PluginConfig config;

    /** Players already greeted this session — keeps the log to one line per handshake, mirrors P1's ServerHandshake. */
    private final Set<UUID> greeted = new HashSet<>();

    public SocialCuesMessageListener(Plugin plugin, CueRelay relay, PluginConfig config) {
        this.plugin = plugin;
        this.relay = relay;
        this.config = config;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!ProtocolConstants.CHANNEL.equals(channel)) {
            return;
        }
        UUID id = player.getUniqueId();
        IngestOutcome outcome = relay.ingest(id, message, System.currentTimeMillis());

        // DESIGN.md §8.6: "socialcues.share" must always win, even over a
        // SharePrefs the client just sent asking for more than that allows.
        if (!player.hasPermission("socialcues.share")) {
            relay.setPrefBits(id, PolicyBits.NONE);
        }

        switch (outcome.status()) {
            case HELLO_RECEIVED -> sendServerHello(player, "ClientHello received");
            case RATE_LIMITED, TOO_LARGE, MALFORMED -> handleViolation(player, outcome);
            case ACCEPTED, PREFS_UPDATED, UNKNOWN_SENDER -> { /* nothing further to do */ }
        }
    }

    /** Called by {@code PlayerLifecycleListener}'s join-timer, DESIGN.md §8.2's "20 tick sonra ServerHello". */
    public void sendServerHelloOnJoinTimer(Player player) {
        sendServerHello(player, "join timer");
    }

    public void forgetGreeting(UUID id) {
        greeted.remove(id);
    }

    private void sendServerHello(Player player, String reason) {
        // DESIGN.md §5: never send to a client that never announced the
        // channel — this is exactly the "Spigot kayıtsız kanalda uyarı
        // basar" trap the design calls out.
        if (!player.getListeningPluginChannels().contains(ProtocolConstants.CHANNEL)) {
            return;
        }
        ServerHello hello = new ServerHello(
                ProtocolConstants.VERSION,
                relay.policyBits(),
                config.idleThresholdTicks(),
                config.relayConfig().updateIntervalTicks(),
                (int) Math.round(config.relayConfig().nearRadius()));
        player.sendPluginMessage(plugin, ProtocolConstants.CHANNEL, S2CMessages.encode(hello));
        if (greeted.add(player.getUniqueId())) {
            plugin.getLogger().info("Handshake with " + player.getName() + " complete (" + reason + ")");
        }
    }

    private void handleViolation(Player player, IngestOutcome outcome) {
        plugin.getLogger().warning(player.getName() + ": " + outcome.status()
                + " (streak " + outcome.violationStreak() + ")");
        int threshold = config.kickAfterViolations();
        if (threshold > 0 && outcome.violationStreak() >= threshold) {
            player.kick(Component.text("Social Cues: protocol abuse detected"));
        }
    }
}
