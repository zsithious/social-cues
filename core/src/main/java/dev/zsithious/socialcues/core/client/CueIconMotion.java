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

    /** How far above its anchor the icon should sit this frame, in blocks. */
    public static float bobBlocks(PlayerCue cue, float ageTicks) {
        if (!snoozing(cue)) {
            return 0f;
        }
        return sine(seconds(ageTicks) + phase(cue), SLEEP_BOB_HZ) * SLEEP_BOB_BLOCKS;
    }

    /** How far the icon should be tilted this frame, radians, about the view axis. */
    public static float tiltRadians(PlayerCue cue, float ageTicks) {
        if (!snoozing(cue)) {
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
