package dev.zsithious.socialcues.paper.config;

import org.bukkit.configuration.file.FileConfiguration;

import dev.zsithious.socialcues.core.policy.AfkVisibility;
import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.relay.RelayConfig;
import dev.zsithious.socialcues.core.util.IdleTimer;

/**
 * DESIGN.md §8.7/§9: reads {@code config.yml} into the platform-independent
 * {@code core} types. Every one of DESIGN.md §5's 8 policy bits is backed by
 * its own named, readable key — never a raw bitmask number in the file:
 * six direct booleans under {@code policy:}, plus {@code afk-visibility}
 * (off|nearby|all), which alone decides bits 3 (IDLE) and 7 (GLOBAL_AFK) via
 * {@link AfkVisibility#applyTo} — a separate boolean for either of those two
 * would just be a second, conflicting way to say the same thing DESIGN.md §5
 * already defines as a single three-state choice.
 */
public final class PluginConfig {

    private final int policyBits;
    private final RelayConfig relayConfig;
    private final int idleThresholdTicks;
    private final int kickAfterViolations;

    private PluginConfig(int policyBits, RelayConfig relayConfig, int idleThresholdTicks, int kickAfterViolations) {
        this.policyBits = policyBits;
        this.relayConfig = relayConfig;
        this.idleThresholdTicks = idleThresholdTicks;
        this.kickAfterViolations = kickAfterViolations;
    }

    public static PluginConfig load(FileConfiguration yaml) {
        RelayConfig defaults = RelayConfig.defaults();

        boolean typing = yaml.getBoolean("policy.share-typing", true);
        boolean screens = yaml.getBoolean("policy.share-screens", true);
        boolean screenDetail = yaml.getBoolean("policy.share-screen-detail", true);
        boolean voice = yaml.getBoolean("policy.share-voice", true);
        boolean intensity = yaml.getBoolean("policy.share-intensity", true);
        boolean globalTier = yaml.getBoolean("policy.share-global-tier", true);
        AfkVisibility afkVisibility = AfkVisibility.fromConfigString(yaml.getString("afk-visibility", "nearby"));
        int policyBits = PolicyBits.of(typing, screens, screenDetail, voice, intensity, globalTier, afkVisibility);

        double nearRadius = yaml.getDouble("near-radius", defaults.nearRadius());
        int updateIntervalTicks = yaml.getInt("update-interval-ticks", defaults.updateIntervalTicks());
        int maxUpdatesPerSecond = yaml.getInt("rate-limit.max-updates-per-second", defaults.maxUpdatesPerSecond());
        long globalBroadcastMinIntervalMs =
                yaml.getLong("global-broadcast-min-interval-ms", defaults.globalBroadcastMinIntervalMs());
        RelayConfig relayConfig = new RelayConfig(
                nearRadius, updateIntervalTicks, maxUpdatesPerSecond,
                globalBroadcastMinIntervalMs, defaults.maxPacketSize());

        int idleThresholdSeconds = yaml.getInt(
                "idle-threshold-seconds", (int) (IdleTimer.DEFAULT_IDLE_THRESHOLD_TICKS / IdleTimer.MC_TICKS_PER_SECOND));
        int idleThresholdTicks = idleThresholdSeconds * IdleTimer.MC_TICKS_PER_SECOND;

        // 0 disables kicking outright; DESIGN.md §8.7 calls this the "kick eşiği" (kick threshold).
        int kickAfterViolations = yaml.getInt("rate-limit.kick-after-violations", 20);

        return new PluginConfig(policyBits, relayConfig, idleThresholdTicks, kickAfterViolations);
    }

    public int policyBits() {
        return policyBits;
    }

    public RelayConfig relayConfig() {
        return relayConfig;
    }

    public int idleThresholdTicks() {
        return idleThresholdTicks;
    }

    public int kickAfterViolations() {
        return kickAfterViolations;
    }
}
