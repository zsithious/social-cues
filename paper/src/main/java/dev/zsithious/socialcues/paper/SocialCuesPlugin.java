package dev.zsithious.socialcues.paper;

import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.S2CMessage;
import dev.zsithious.socialcues.core.protocol.S2CMessages;
import dev.zsithious.socialcues.core.relay.CueRelay;
import dev.zsithious.socialcues.core.relay.TickResult;
import dev.zsithious.socialcues.paper.config.PluginConfig;
import dev.zsithious.socialcues.paper.integration.PlaceholderIntegration;
import dev.zsithious.socialcues.paper.network.PlayerLifecycleListener;
import dev.zsithious.socialcues.paper.network.SocialCuesMessageListener;
import dev.zsithious.socialcues.paper.relay.BukkitVisibilityChecker;
import dev.zsithious.socialcues.paper.scheduling.BukkitPluginScheduler;
import dev.zsithious.socialcues.paper.scheduling.PluginScheduler;

/**
 * DESIGN.md §8/§14 P2: the Paper adapter around {@code core.relay.CueRelay}.
 * Every actual decision — state storage, permission masking, delta
 * suppression, near/global tiering, rate limiting, visibility filtering —
 * lives in {@code core}; this class only wires Bukkit lifecycle events and
 * the {@code socialcues:v1} plugin-message channel to it and turns its
 * output into {@code Messenger} sends. See DESIGN.md §8's "röle mantığı iki
 * kez yazılmayacak".
 */
public final class SocialCuesPlugin extends JavaPlugin {

    private CueRelay relay;
    private PluginConfig config;
    private PluginScheduler scheduler;
    private SocialCuesMessageListener messageListener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = PluginConfig.load(getConfig());

        relay = new CueRelay(new BukkitVisibilityChecker(), config.relayConfig());
        relay.setPolicyBits(config.policyBits());

        getServer().getMessenger().registerOutgoingPluginChannel(this, ProtocolConstants.CHANNEL);
        messageListener = new SocialCuesMessageListener(this, relay, config);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProtocolConstants.CHANNEL, messageListener);
        getLogger().info("Registered " + ProtocolConstants.CHANNEL + " plugin channel (incoming="
                + getServer().getMessenger().isIncomingChannelRegistered(this, ProtocolConstants.CHANNEL)
                + ", outgoing=" + getServer().getMessenger().isOutgoingChannelRegistered(this, ProtocolConstants.CHANNEL) + ")");

        scheduler = new BukkitPluginScheduler(this);
        getServer().getPluginManager().registerEvents(
                new PlayerLifecycleListener(this, relay, messageListener, scheduler), this);

        // DESIGN.md §14 P8: optional, and silent when PlaceholderAPI is absent
        // (which is the common case) -- see PlaceholderIntegration's Javadoc for
        // why the expansion is never named directly from here.
        if (PlaceholderIntegration.registerIfPresent(this, relay)) {
            getLogger().info("PlaceholderAPI detected — registered the %socialcues_*% placeholders.");
        }

        long periodTicks = Math.max(1, config.relayConfig().updateIntervalTicks());
        scheduler.runRepeating(this::broadcastTick, periodTicks, periodTicks);

        // No development-phase label here: this line lands in the console of every
        // server that installs the plugin, and "P2" means nothing outside this repo.
        // It reports the two settings an admin might actually want to confirm.
        getLogger().info("Social Cues loaded (near-radius=" + config.relayConfig().nearRadius()
                + ", update-interval-ticks=" + periodTicks + ")");
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.cancelAll();
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
    }

    /** DESIGN.md §8.4: one relay tick per {@code updateIntervalTicks}, fanned out as {@code Messenger} sends. */
    private void broadcastTick() {
        TickResult result = relay.tick(System.currentTimeMillis());
        sendAll(result.nearBatches());
        sendAll(result.nearDrops());
        sendAll(result.globalBatches());
        sendAll(result.globalDrops());
    }

    private void sendAll(Map<UUID, ? extends S2CMessage> messagesByRecipient) {
        if (messagesByRecipient.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, ? extends S2CMessage> entry : messagesByRecipient.entrySet()) {
            Player recipient = Bukkit.getPlayer(entry.getKey());
            if (recipient == null) {
                continue;
            }
            // DESIGN.md §8.6: "socialcues.see" gates receiving anyone else's cues.
            if (!recipient.hasPermission("socialcues.see")) {
                continue;
            }
            if (!recipient.getListeningPluginChannels().contains(ProtocolConstants.CHANNEL)) {
                continue;
            }
            recipient.sendPluginMessage(this, ProtocolConstants.CHANNEL, S2CMessages.encode(entry.getValue()));
        }
    }
}
