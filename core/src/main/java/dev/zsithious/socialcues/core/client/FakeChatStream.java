package dev.zsithious.socialcues.core.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 Katman 3 — the text that scrolls on another player's chat panel
 * while they type.
 *
 * <p><b>It is deliberately meaningless, and it could not be otherwise.</b>
 * DESIGN.md §6/§10.1 forbid this mod from ever reading what someone is
 * actually typing, and the build enforces it: {@code mc.gradle.kts}'s
 * {@code checkNoTextAccess} task fails the build if any source file under
 * {@code mc-shared} or an adapter so much as calls a text accessor. The wire
 * protocol carries an {@code Activity} and a typing <em>cadence</em>, never a
 * character. So the panel cannot show real text — and showing convincing
 * <em>fake</em> text is exactly the right answer: a watcher learns "they are
 * typing, at about this speed", which is the entire point of the mod, and
 * learns nothing whatever about what is being said.
 *
 * <p>For the same reason the alphabet below is letters and spaces only. No
 * digits, no punctuation, no word list: nothing that could be mistaken at a
 * glance for a readable message, a name, or a number.
 *
 * <p><b>Deterministic, allocation-light, no state.</b> Every character is a
 * function of (player seed, line index, column) through the same integer
 * avalanche {@link PoseAnimator} uses, so two clients watching the same player
 * see the same gibberish, nothing has to be synchronised, and there is no RNG
 * to keep alive per player.
 *
 * <p><b>{@code reducedMotion} (P6 §4.1, DESIGN.md §9 "animasyon yok, sadece
 * statik ikon").</b> Both public methods take how many characters have been
 * "typed" and derive everything else (which line, which column, the caret
 * position) from that one count — see {@link #typedCount}. Normally that
 * count grows with {@code seconds}, which is exactly the time-varying term
 * P6 asks for removed: {@code reducedMotion} makes it a fixed constant
 * instead ({@link #REDUCED_MOTION_TYPED}), independent of {@code seconds}
 * entirely, so every call for the same {@code cue} returns byte-identical
 * output no matter when it is asked. Deliberately not zero: freezing at the
 * very first frame would show an empty panel with nothing on it, which reads
 * as broken chrome rather than as "typing, held still" — a fixed
 * mid-line count keeps a snapshot of gibberish on the in-progress line (its
 * content still varies per player, only its length stops moving) the same
 * way {@link PoseAnimator}'s reduced-motion pose still visibly holds a pose
 * instead of going fully neutral. There is no blend-in/out state here the
 * way {@link PoseAnimator} has via {@code weight} — a typing/screen cue
 * either has a panel or it does not — so unlike that class there is no
 * "state change" left to preserve once the per-tick term is gone; this is
 * simply the stable single frame P6 §4.1 asks for.
 */
public final class FakeChatStream {

    /** Lines shown on the panel: one in progress at the bottom, the rest already "sent" above it. */
    public static final int VISIBLE_LINES = 4;

    /**
     * Longest a line gets before it is treated as sent and a new one starts.
     * DESIGN.md §7 P5 hand-test fix: cut down from 22, in step with the held
     * chat panel shrinking from 1.1 to 0.62 blocks wide ({@code
     * CueScreenPanelRenderer.CHAT_WIDTH_BLOCKS}) — a line this long is what
     * lets {@code CueScreenPanelRenderer.drawChatText}'s height-derived scale
     * stay the one usually in effect (see that method's own Javadoc on why it
     * measures the real text instead of a fixed worst-case budget): a
     * comfortably-sized full-length line already fits the panel's width, so
     * the width constraint only ever binds — and only ever visibly changes the
     * scale — on an unusually wide line, not on every keystroke.
     */
    public static final int MAX_LINE_LENGTH = 13;

    /**
     * {@code reducedMotion}'s fixed stand-in for a real, time-derived typed
     * count (see this class's Javadoc). Deliberately mid-line rather than 0
     * or {@code MAX_LINE_LENGTH - 1}: a snapshot that reads as "part-way
     * through a word", not as "just started" or "about to wrap".
     */
    private static final int REDUCED_MOTION_TYPED = MAX_LINE_LENGTH / 2;

    private static final char[] ALPHABET = "abcdefghijklmnopqrstuvwxyz     ".toCharArray();

    /** Characters per second at {@code intensity} 0 … 255 — the same cadence the tap animation uses. */
    private static final float MIN_CPS = 2.2f;
    private static final float MAX_CPS = 7.0f;

    private FakeChatStream() {
    }

    /**
     * The panel's visible lines for {@code cue} at {@code seconds} of animation
     * time, oldest first. The last entry is the line currently being "typed"
     * and grows over time; it may be empty just after a line wraps.
     *
     * @param reducedMotion P6 §4.1: when {@code true}, {@code seconds} is
     *                      ignored and a single fixed frame is returned
     *                      instead — see this class's Javadoc
     * @return a list of exactly {@link #VISIBLE_LINES} strings, never null
     */
    public static List<String> lines(PlayerCue cue, float seconds, boolean reducedMotion) {
        Objects.requireNonNull(cue, "cue");
        int seed = cue.id().hashCode();
        int typed = typedCount(cue, seconds, reducedMotion);

        // Which line we are on, and how far into it, if every line runs to MAX_LINE_LENGTH.
        int lineIndex = typed / MAX_LINE_LENGTH;
        int column = typed % MAX_LINE_LENGTH;

        List<String> out = new ArrayList<>(VISIBLE_LINES);
        for (int i = VISIBLE_LINES - 1; i >= 1; i--) {
            int index = lineIndex - i;
            out.add(index < 0 ? "" : line(seed, index, MAX_LINE_LENGTH));
        }
        out.add(line(seed, lineIndex, column));
        return out;
    }

    /**
     * Where the caret sits on the in-progress line — a renderer draws a
     * blinking cursor here (or, per P6 §4.1, a caret that does not blink,
     * when the renderer itself honours {@code reducedMotion} — that toggle
     * is renderer chrome outside this class, see {@code
     * CueScreenPanelRenderer}).
     *
     * @param reducedMotion P6 §4.1: when {@code true}, {@code seconds} is
     *                      ignored and a single fixed column is returned
     *                      instead — see this class's Javadoc
     */
    public static int caretColumn(PlayerCue cue, float seconds, boolean reducedMotion) {
        Objects.requireNonNull(cue, "cue");
        return typedCount(cue, seconds, reducedMotion) % MAX_LINE_LENGTH;
    }

    /**
     * How many characters {@code cue} has "typed" as of {@code seconds} —
     * the one quantity {@link #lines} and {@link #caretColumn} both derive
     * everything else from, so {@code reducedMotion}'s override only has to
     * live in one place.
     */
    private static int typedCount(PlayerCue cue, float seconds, boolean reducedMotion) {
        if (reducedMotion) {
            return REDUCED_MOTION_TYPED;
        }
        float cps = MIN_CPS + (MAX_CPS - MIN_CPS) * clamp01(cue.intensity() / 255f);
        return (int) Math.max(0, seconds * cps);
    }

    private static String line(int seed, int lineIndex, int length) {
        if (length <= 0) {
            return "";
        }
        StringBuilder text = new StringBuilder(length);
        for (int column = 0; column < length; column++) {
            int h = hash(seed ^ (lineIndex * 0x9e3779b9) ^ (column * 0x85ebca6b));
            char c = ALPHABET[Math.floorMod(h, ALPHABET.length)];
            // Never start or end a line on a space, and never double one up: it
            // reads as a dropped frame rather than as text.
            if (c == ' ' && (column == 0 || column == length - 1
                    || text.charAt(text.length() - 1) == ' ')) {
                c = ALPHABET[Math.floorMod(h >>> 7, 26)];
            }
            text.append(c);
        }
        return text.toString();
    }

    private static int hash(int value) {
        int x = value * 0x9e3779b9;
        x ^= x >>> 16;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        x ^= x >>> 16;
        return x;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
