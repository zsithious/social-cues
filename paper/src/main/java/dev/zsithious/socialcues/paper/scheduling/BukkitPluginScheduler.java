package dev.zsithious.socialcues.paper.scheduling;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * The only implementation of {@link PluginScheduler} for now: plain
 * {@code BukkitScheduler}, which runs everything on the main thread — exactly
 * what {@code core.relay.CueRelay} assumes (DESIGN.md §8/§14: "Not
 * thread-safe... a single platform main thread").
 */
public final class BukkitPluginScheduler implements PluginScheduler {

    private final Plugin plugin;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public BukkitPluginScheduler(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public void runDelayed(Runnable task, long delayTicks) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks));
    }

    @Override
    public void runRepeating(Runnable task, long delayTicks, long periodTicks) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks));
    }

    @Override
    public void cancelAll() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
}
