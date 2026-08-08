package dev.zsithious.socialcues.paper.network;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import dev.zsithious.socialcues.core.protocol.CueDrop;
import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.S2CMessages;
import dev.zsithious.socialcues.core.relay.CueRelay;
import dev.zsithious.socialcues.core.relay.LeaveResult;
import dev.zsithious.socialcues.paper.scheduling.PluginScheduler;

/**
 * DESIGN.md §8.2/§8.3: join registers the player with the relay and schedules
 * the 20-tick fallback {@code ServerHello}; quit clears the relay's state for
 * that player and pushes a {@code CueDrop} to whoever had actually seen them.
 */
public final class PlayerLifecycleListener implements Listener {

    /** DESIGN.md §8.2: "oyuncu katılınca ~20 tick sonra ServerHello". */
    private static final long HELLO_DELAY_TICKS = 20L;

    private final Plugin plugin;
    private final CueRelay relay;
    private final SocialCuesMessageListener messageListener;
    private final PluginScheduler scheduler;

    public PlayerLifecycleListener(Plugin plugin, CueRelay relay, SocialCuesMessageListener messageListener,
                                    PluginScheduler scheduler) {
        this.plugin = plugin;
        this.relay = relay;
        this.messageListener = messageListener;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        relay.join(player.getUniqueId(), System.currentTimeMillis());
        scheduler.runDelayed(() -> {
            if (player.isOnline()) {
                messageListener.sendServerHelloOnJoinTimer(player);
            }
        }, HELLO_DELAY_TICKS);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        LeaveResult result = relay.leave(id);
        messageListener.forgetGreeting(id);
        if (result.recipientsToNotify().isEmpty()) {
            return;
        }
        byte[] encoded = S2CMessages.encode(new CueDrop(List.of(id)));
        for (UUID recipientId : result.recipientsToNotify()) {
            Player recipient = Bukkit.getPlayer(recipientId);
            if (recipient == null || !recipient.getListeningPluginChannels().contains(ProtocolConstants.CHANNEL)) {
                continue;
            }
            recipient.sendPluginMessage(plugin, ProtocolConstants.CHANNEL, encoded);
        }
    }
}
