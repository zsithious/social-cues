package dev.zsithious.socialcues.core.client;

import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 Katman 1 — the small idle motion of the billboard icon above a
 * player's head.
 *
 * <p>Only the sleep icon moves. A "Zz" that hangs perfectly still over a
 * perfectly still player reads as a broken sprite; a slow bob and tilt is what
 * makes it read as a comic-strip snore. Every other cue's icon is a status
 * indicator, not a joke, and holds still on purpose — a chat bubble that
 * wobbled would just be noise on top of an already-animated body.
 *
 * <p><b>Deliberately independent of Layer 3.</b> This is Layer 1's own motion:
 * it must keep working for a player who has turned the pose layer off, and it
 * must not be reachable only through {@link PoseAnimator}. Hence a separate
 * entry point rather than two more fields on {@link PoseFrame}.
 *
 * <p><b>{@code reducedMotion} (P6 §4.1, DESIGN.md §9 "animasyon yok, sadece
 * statik ikon").</b> This class has exactly one time-varying term each
 * method — the sine — and nothing that counts as a state change the way
 * {@link PoseAnimator}'s pose-blend-in does: the sleep icon has no "resting
 * target" of its own to hold at, it is either bobbing or it is doing nothing.
 * So {@code reducedMotion} is the simplest possible instance of the general
 * P6 rule ("every time-varying term is removed"): both methods return
 * exactly {@code 0f} when it is set, same as they already do for every
 * activity but {@link Activity#AFK}.
 */
public final class CueIconMotion {

    /** Vertical bob of the sleep icon, in blocks, and how often it completes a cycle. */
    private static final float SLEEP_BOB_BLOCKS = 0.035f;
    private static final float SLEEP_BOB_HZ = 0.28f;
    /** Lazy tilt, radians, on a slower and unrelated frequency so bob and tilt never sync up. */
    private static final float SLEEP_TILT = 0.13f;
    private static final float SLEEP_TILT_HZ = 0.19f;

    private CueIconMotion() {
    }

    /**
     * How far above its anchor the icon should sit this frame, in blocks.
     *
     * @param reducedMotion P6 §4.1: when {@code true}, always {@code 0f} —
     *                      see this class's Javadoc for why there is no
     *                      "steady" non-zero bob to fall back to instead.
     */
    public static float bobBlocks(PlayerCue cue, float ageTicks, boolean reducedMotion) {
        if (!snoozing(cue)) {
            return 0f;
        }
        if (reducedMotion) {
            return 0f;
        }
        return sine(seconds(ageTicks) + phase(cue), SLEEP_BOB_HZ) * SLEEP_BOB_BLOCKS;
    }

    /**
     * How far the icon should be tilted this frame, radians, about the view axis.
     *
     * @param reducedMotion P6 §4.1: when {@code true}, always {@code 0f} —
     *                      see this class's Javadoc for why there is no
     *                      "steady" non-zero tilt to fall back to instead.
     */
    public static float tiltRadians(PlayerCue cue, float ageTicks, boolean reducedMotion) {
        if (!snoozing(cue)) {
            return 0f;
        }
        if (reducedMotion) {
            return 0f;
        }
        return sine(seconds(ageTicks) + phase(cue) * 1.7f, SLEEP_TILT_HZ) * SLEEP_TILT;
    }

    private static boolean snoozing(PlayerCue cue) {
        Objects.requireNonNull(cue, "cue");
        return cue.activity() == Activity.AFK;
    }

    /** Per-player offset, so a row of sleeping players does not bob in unison. */
    private static float phase(PlayerCue cue) {
        return Math.floorMod(cue.id().hashCode(), 1000) / 1000f * 12f;
    }

    private static float seconds(float ageTicks) {
        return ageTicks / 20f;
    }

    private static float sine(float seconds, float hz) {
        return (float) Math.sin(seconds * hz * 2.0 * Math.PI);
    }
}
