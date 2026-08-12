package dev.zsithious.socialcues.paper.integration;

import java.util.logging.Level;

import org.bukkit.plugin.Plugin;

import dev.zsithious.socialcues.core.relay.CueRelay;

/**
 * DESIGN.md §14 P8 — the load-bearing wall between {@code SocialCuesPlugin}
 * and {@link SocialCuesExpansion}.
 *
 * <p>PlaceholderAPI is a soft dependency, so on most servers its classes do
 * not exist. {@link SocialCuesExpansion} extends one of them, which means the
 * JVM cannot even <i>load</i> that class there — resolving a supertype is not
 * lazy. Naming it from a branch inside {@code onEnable} would still be safe in
 * practice (resolution happens when the instruction executes, not when the
 * method is entered), but that safety would rest on a subtlety of constant
 * pool resolution that no future edit is obliged to preserve.
 *
 * <p>This class makes it structural instead: {@code SocialCuesPlugin} names
 * only this one, whose own supertypes are all ordinary, and the expansion is
 * named exclusively from inside a method that is only ever called after the
 * plugin has been confirmed present.
 */
public final class PlaceholderIntegration {

    private PlaceholderIntegration() {
    }

    /**
     * Registers the expansion when PlaceholderAPI is installed, and does
     * nothing at all when it is not.
     *
     * @return whether the expansion was registered
     */
    public static boolean registerIfPresent(Plugin plugin, CueRelay relay) {
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return false;
        }
        try {
            return new SocialCuesExpansion(plugin, relay).register();
        } catch (Throwable t) {
            // Same principle as the client's capture guard: an optional
            // cosmetic integration must never take the plugin down with it.
            // A PlaceholderAPI major version that moved the API surface would
            // surface here as a LinkageError, and the correct outcome is a
            // server that runs fine without placeholders.
            plugin.getLogger().log(Level.WARNING,
                    "PlaceholderAPI was found but the Social Cues expansion could not be registered; "
                            + "placeholders will be unavailable. Everything else is unaffected.", t);
            return false;
        }
    }
}
