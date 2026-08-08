package dev.zsithious.socialcues.paper;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * P0 stub: no channel registration, no listeners yet. DESIGN.md §8's actual
 * relay responsibilities (Messenger channel, ServerHello, delta CueBatch,
 * visibility filtering, config) start at P2.
 */
public final class SocialCuesPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Social Cues loaded (P0 stub, no features yet)");
    }
}
