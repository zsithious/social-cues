package dev.zsithious.socialcues.paper.integration;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import dev.zsithious.socialcues.core.policy.EffectivePolicy;
import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.relay.CueRelay;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;

/**
 * DESIGN.md §14 P8 — the optional PlaceholderAPI integration, and the only
 * class in this repository that names a {@code me.clip.placeholderapi.*} type.
 *
 * <p>It exists because the tab-list cue (Layer 2) is drawn by the <i>client</i>
 * mod, so it only ever appears for players who installed it. A server that
 * wants everyone — vanilla clients included — to see who is typing or idle
 * has to render that server-side, through whatever already owns its tab list
 * or scoreboard (TAB, Featherboard, and friends). Placeholders are the
 * lingua franca those plugins speak.
 *
 * <p><b>The privacy rule, and why it is not the obvious one.</b> Every other
 * consumer of cue state asks on behalf of a specific viewer, which is what
 * lets the relay apply the near-radius, the vanish check and the per-viewer
 * tiering (DESIGN.md §5). A placeholder has no viewer: PlaceholderAPI hands
 * over the subject and nothing else, and the result may be rendered into a
 * tab list that the whole server sees. So this expansion answers with the
 * <b>global tier</b> view and nothing more —
 * {@link EffectivePolicy#applyGlobalCoarse} over
 * {@link EffectivePolicy#applyNear} — which is exactly the projection the
 * design already defines as "safe to show everyone regardless of distance".
 *
 * <p>Two consequences follow, both deliberate:
 * <ul>
 *   <li>There is no screen-detail placeholder. The global tier forces
 *       {@code ScreenKind.UNKNOWN}, so "in a menu" is the most that can ever
 *       be said; a {@code %socialcues_screen%} would have to either lie or
 *       breach the tier.</li>
 *   <li>A player whose {@code GLOBAL_TIER} bit is off — server policy or
 *       their own preference — has no presence here at all, and every
 *       placeholder falls back to its empty value. Opting out of the global
 *       tier has to mean opting out of the thing that renders it globally,
 *       or it was never an opt-out.</li>
 * </ul>
 *
 * <p><b>Licensing</b> (DESIGN.md §13): PlaceholderAPI is GPL-3.0 and this
 * project is MIT. The dependency is {@code compileOnly} and PlaceholderAPI is
 * never shipped, bundled or shaded here — a server operator supplies their own
 * copy, which is how every expansion in the ecosystem works. Confining the
 * import to this one file keeps the boundary legible.
 */
public final class SocialCuesExpansion extends PlaceholderExpansion {

    /** What a placeholder resolves to when there is simply nothing to say. */
    private static final String EMPTY = "";

    private final Plugin plugin;
    private final CueRelay relay;

    public SocialCuesExpansion(Plugin plugin, CueRelay relay) {
        this.plugin = plugin;
        this.relay = relay;
    }

    @Override
    public String getIdentifier() {
        return "socialcues";
    }

    @Override
    public String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * The expansion is owned by this plugin rather than downloaded by
     * PlaceholderAPI's eCloud, so it must survive {@code /papi reload} instead
     * of being unregistered and looked for on the cloud (where it does not
     * exist).
     */
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) {
            return EMPTY;
        }
        UUID id = player.getUniqueId();

        // Not "does the relay know a cue for them" but "did their client ever
        // introduce itself": the honest answer to "can this player be read at
        // all", and the one a tab-list layout wants in order to fall back to
        // something else for vanilla clients.
        if (params.equalsIgnoreCase("installed")) {
            return bool(relay.isKnown(id));
        }

        Optional<Activity> activity = globalActivity(id);

        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "activity" -> activity.map(a -> a.name().toLowerCase(java.util.Locale.ROOT)).orElse(EMPTY);
            case "activity_name" -> activity.map(SocialCuesExpansion::displayName).orElse(EMPTY);
            case "typing" -> bool(activity.filter(SocialCuesExpansion::isTyping).isPresent());
            case "in_screen" -> bool(activity.filter(a -> a == Activity.IN_SCREEN).isPresent());
            case "afk" -> bool(activity.filter(a -> a == Activity.AFK).isPresent());
            case "speaking" -> bool(activity.filter(a -> a == Activity.SPEAKING).isPresent());
            // null, not EMPTY: PlaceholderAPI treats null as "not mine" and
            // leaves the text untouched, which is what makes a typo in a
            // server's tab-list config visible instead of silently blank.
            default -> null;
        };
    }

    /**
     * The subject's activity as the global tier would broadcast it, or empty
     * when they have no global presence. See the class Javadoc for why this,
     * and only this, is what a placeholder is allowed to see.
     */
    private Optional<Activity> globalActivity(UUID id) {
        Optional<PlayerCue> cue = relay.cueOf(id);
        if (cue.isEmpty()) {
            return Optional.empty();
        }
        int effectiveBits = relay.effectiveBits(id);
        CueBatch.Entry nearMasked = EffectivePolicy.applyNear(cue.get(), effectiveBits);
        return EffectivePolicy.applyGlobalCoarse(nearMasked, effectiveBits).map(CueBatch.Entry::activity);
    }

    private static boolean isTyping(Activity activity) {
        return activity == Activity.TYPING_CHAT
                || activity == Activity.TYPING_COMMAND
                || activity == Activity.TYPING_SIGN
                || activity == Activity.TYPING_BOOK;
    }

    /**
     * Plain English, deliberately not a translation key: this string is handed
     * to another plugin's layout engine, not to a Minecraft client that could
     * resolve a key against a language file. Server owners who want other
     * wording (or an icon) build it from {@code %socialcues_activity%} with
     * their own conditionals, which is how those plugins are used anyway.
     */
    private static String displayName(Activity activity) {
        return switch (activity) {
            case NORMAL -> "Active";
            case TYPING_CHAT -> "Typing";
            case TYPING_COMMAND -> "Typing a command";
            case TYPING_SIGN -> "Editing a sign";
            case TYPING_BOOK -> "Writing a book";
            case IN_SCREEN -> "In a menu";
            case AFK -> "Idle";
            case SPEAKING -> "Speaking";
        };
    }

    private static String bool(boolean value) {
        return value ? "true" : "false";
    }
}
