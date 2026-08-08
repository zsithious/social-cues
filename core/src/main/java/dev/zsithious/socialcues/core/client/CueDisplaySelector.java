package dev.zsithious.socialcues.core.client;

import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 P4b task note §3.2 — "textOnly / reducedMotion seçimi: hangi
 * durumda ikon, hangisinde metin anahtarı" resolved down to a single
 * concrete decision: which atlas cell and which translation key a given
 * {@link PlayerCue} maps to. {@link CueIconAtlas} and {@link CueLangKeys}
 * (P4a) only ever supply the raw building blocks — one cell/key per
 * {@link Activity}, plus one further reserved "sleepy" cell/key each — this
 * class is the actual selection logic their javadocs anticipated but
 * deliberately left open (see {@code CueIconAtlas}'s class Javadoc: "a
 * renderer substitutes it for {@code cellFor(Activity.AFK)} when that flag is
 * set"). P4b is that renderer's decision, now written once and unit tested,
 * so both render layers (billboard and tab list) call the same method
 * instead of re-deriving the AFK+SLEEPY special case twice.
 *
 * <p>Pure Java, no Minecraft/Bukkit: turning a translation key string into an
 * actual {@code Text.translatable(...)} call, or an atlas cell index into UV
 * coordinates (already {@link CueIconAtlas}'s job), is entirely the adapter's
 * concern.
 */
public final class CueDisplaySelector {

    private CueDisplaySelector() {
    }

    /**
     * The {@link CueIconAtlas} cell to draw for {@code cue}: the AFK cell's
     * dedicated {@link CueIconAtlas#SLEEPY_CELL} variant when
     * {@link CueFlags#SLEEPY} accompanies {@link Activity#AFK} (DESIGN.md
     * §4), otherwise {@link CueIconAtlas#cellFor(Activity)}.
     */
    public static int atlasCellFor(PlayerCue cue) {
        Objects.requireNonNull(cue, "cue");
        if (isSleepyAfk(cue)) {
            return CueIconAtlas.SLEEPY_CELL;
        }
        return CueIconAtlas.cellFor(cue.activity());
    }

    /**
     * The {@link CueLangKeys} translation key for {@code cue}'s
     * {@code textOnly}-mode label: the text-mode counterpart of
     * {@link #atlasCellFor}, same SLEEPY special case.
     */
    public static String langKeyFor(PlayerCue cue) {
        Objects.requireNonNull(cue, "cue");
        if (isSleepyAfk(cue)) {
            return CueLangKeys.SLEEPY_FLAG_KEY;
        }
        return CueLangKeys.keyFor(cue.activity());
    }

    private static boolean isSleepyAfk(PlayerCue cue) {
        return cue.activity() == Activity.AFK && cue.hasFlag(CueFlags.SLEEPY);
    }
}
