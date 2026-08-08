package dev.zsithious.socialcues.paper.scheduling;

/**
 * DESIGN.md §8.9: "zamanlayıcı GlobalRegionScheduler'a taşınabilir olacak
 * şekilde tek yerde soyutlanır; ilk sürümde Folia hedef değil, kapı açık
 * bırakılır." Every scheduled task in this plugin — the repeating
 * {@code updateIntervalTicks} broadcast and the one-shot join-hello delay —
 * goes through this single interface instead of calling
 * {@code Bukkit.getScheduler()} directly in more than one place, so a future
 * Folia port only has to replace {@link BukkitPluginScheduler}.
 */
public interface PluginScheduler {

    /** Runs {@code task} once, {@code delayTicks} from now. */
    void runDelayed(Runnable task, long delayTicks);

    /** Runs {@code task} every {@code periodTicks}, starting {@code delayTicks} from now. */
    void runRepeating(Runnable task, long delayTicks, long periodTicks);

    /** Cancels every task previously scheduled through this instance. Called from {@code onDisable}. */
    void cancelAll();
}
