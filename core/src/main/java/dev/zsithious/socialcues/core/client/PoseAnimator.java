package dev.zsithious.socialcues.core.client;

import java.util.Objects;

import dev.zsithious.socialcues.core.client.PoseFrame.Limb;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 Katman 3 — the pose animation itself: pure maths, no Minecraft,
 * fully unit tested. Given a player's cue and a clock, it produces the
 * {@link PoseFrame} a renderer adds to vanilla's pose.
 *
 * <p><b>Where the movement comes from (CLEANROOM.md):</b> the motions below
 * are written from watching WATUT's four <em>public demo GIFs</em> on its
 * Modrinth page and describing what a body does — the one derivation source
 * CLEANROOM.md explicitly allows ("Sadece herkese açık davranış tarifi/GIF'ten
 * türetilir"). No WATUT source was read, and not one number here came from
 * anywhere: every angle, frequency and easing curve was chosen by this project
 * and tuned by eye in game. What was observed is behaviour only — a player
 * typing puts their hands out on a keyboard and looks down at it; a player in
 * a container holds a panel up in one hand and works it with the other; an
 * idle player's head sinks forward and they sway on their feet.
 *
 * <p><b>Why the motion is pseudo-random rather than sinusoidal.</b> Hands do
 * not tap a keyboard on a metronome and nobody moves items between slots at a
 * constant rate. A pure sine reads as machinery within about two seconds of
 * watching it. Every "random" number below therefore comes from
 * {@link #noise}, a plain integer hash of a time step — deterministic (the
 * same player at the same instant looks the same on every client that can see
 * them, with no RNG state to keep or synchronise), free of allocation, and
 * seeded per player so that two people typing side by side are not in lockstep.
 *
 * <p><b>Everything scales by {@code weight}</b> (see {@link PoseBlend}), which
 * is folded into every returned number, so a caller never has to remember to
 * interpolate: weight {@code 0} returns {@link PoseFrame#NONE} exactly.
 *
 * <p><b>Time base:</b> {@code ageTicks} is the rendered entity's own age in
 * ticks (fractional, i.e. already including the frame's partial tick), so the
 * motion is smooth between ticks and every player has a different phase
 * instead of the whole server bobbing in unison.
 */
public final class PoseAnimator {

    /** Ticks per second — the unit conversion between {@code ageTicks} and the frequencies below. */
    private static final float TICKS_PER_SECOND = 20f;

    // ------------------------------------------------- typing (a chat window)

    /**
     * Shoulder rotation for hands out on a keyboard (~60° forward). This is
     * deliberately shallower than the in-screen pose: a keyboard sits out in
     * front of you at desk height, not clasped up against your chest.
     */
    private static final float TYPING_ARM_PITCH = -1.05f;
    /** Barely any inward tuck — hands rest about shoulder-width apart on the keys, they do not meet. */
    private static final float TYPING_ARM_ROLL = 0.06f;
    private static final float TYPING_ARM_YAW = 0.10f;
    /** Head tips down to the keyboard and the window above it (~26°). */
    private static final float TYPING_HEAD_PITCH = 0.45f;

    /** Key taps per second at {@code intensity} 0 … 255. */
    private static final float TYPING_MIN_HZ = 2.2f;
    private static final float TYPING_MAX_HZ = 7.0f;
    /** How far a hand dips on a tap, likewise scaled by intensity — faster typing is visibly busier. */
    private static final float TYPING_MIN_TAP = 0.11f;
    private static final float TYPING_MAX_TAP = 0.20f;
    /** Sideways reach across the keys: hands do not stay on home row. */
    private static final float TYPING_REACH_YAW = 0.09f;
    /** Slow whole-body settle laid under the tapping, so the pose is never perfectly still. */
    private static final float TYPING_DRIFT_HZ = 0.37f;
    private static final float TYPING_DRIFT = 0.035f;

    private static final float TYPING_SCREEN_TILT = 0.18f;
    private static final float TYPING_SCREEN_RISE = 0.62f;

    // -------------------------------------------- in a screen (a container GUI)

    /** The holding arm: up at reading height and held there. */
    private static final float SCREEN_HOLD_PITCH = -1.40f;
    private static final float SCREEN_HOLD_ROLL = 0.22f;
    /** The working arm's resting point, before the reaching below moves it around the panel. */
    private static final float SCREEN_WORK_PITCH = -1.28f;
    private static final float SCREEN_WORK_ROLL = 0.10f;

    /** How long the working hand takes to settle on a new slot, and how long it lingers there. */
    private static final float SCREEN_REACH_SECONDS = 0.62f;
    private static final float SCREEN_REACH_MOVE_FRACTION = 0.45f;
    /** Reach envelope: how far the working hand ranges over the panel. */
    private static final float SCREEN_REACH_PITCH = 0.30f;
    private static final float SCREEN_REACH_YAW = 0.36f;
    private static final float SCREEN_REACH_ROLL = 0.14f;

    private static final float SCREEN_HEAD_PITCH = 0.30f;
    /** Resting lean of the panel's top edge toward the player (~26°). */
    private static final float SCREEN_BASE_TILT = 0.45f;
    private static final float SCREEN_RISE = 0.78f;
    /** The hand-held wobble: slow, small, split across two incommensurate frequencies so it never repeats. */
    private static final float SCREEN_WOBBLE_HZ = 0.33f;
    private static final float SCREEN_WOBBLE_HZ_2 = 0.21f;
    private static final float SCREEN_WOBBLE_TILT = 0.055f;
    private static final float SCREEN_WOBBLE_ARM = 0.030f;

    // -------------------------------------------------------------- idle / AFK

    /** Head droop when idle (~63°) and once {@code SLEEPY} is reached (~77°, chin fully down). */
    private static final float AFK_HEAD_PITCH = 1.10f;
    private static final float SLEEPY_HEAD_PITCH = 1.35f;
    /** The slow nod, and the head lolling to one side, of someone dozing off. */
    private static final float AFK_NOD_HZ = 0.16f;
    private static final float AFK_NOD = 0.055f;
    private static final float AFK_HEAD_SWAY_HZ = 0.11f;
    private static final float AFK_HEAD_SWAY = 0.10f;
    private static final float AFK_HEAD_LOLL = 0.07f;
    /**
     * Weight shifting from foot to foot. Slower and smaller than the head
     * motion — a standing body sways a little, a dozing head sways a lot — and
     * on its own frequency so head and body are never in step.
     */
    private static final float AFK_BODY_SWAY_HZ = 0.077f;
    private static final float AFK_BODY_ROLL = 0.045f;
    private static final float AFK_BODY_YAW = 0.035f;
    private static final float AFK_BODY_PITCH = 0.020f;
    /** Everything slows down once the player is properly asleep rather than merely idle. */
    private static final float SLEEPY_RATE_SCALE = 0.65f;
    /** Sleeping arms hang a touch heavier than standing ones. */
    private static final float SLEEPY_ARM_SAG = 0.10f;

    private PoseAnimator() {
    }

    /**
     * The pose offsets for {@code cue} at this instant, already multiplied by
     * {@code weight}.
     *
     * @param cue      the player's current cue; its activity, flags and
     *                 intensity drive the motion, and its id seeds the
     *                 pseudo-randomness so two players never move identically
     * @param ageTicks the rendered entity's age in ticks, fractional
     * @param weight   0..1 blend weight; values outside are clamped
     * @return never {@code null}; exactly {@link PoseFrame#NONE} at weight 0
     */
    public static PoseFrame frameFor(PlayerCue cue, float ageTicks, float weight) {
        Objects.requireNonNull(cue, "cue");
        float w = clamp01(weight);
        if (w <= 0f) {
            return PoseFrame.NONE;
        }
        int seed = cue.id().hashCode();
        return switch (cue.activity()) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK -> typing(cue.intensity(), ageTicks, seed, w);
            case IN_SCREEN -> inScreen(ageTicks, seed, w);
            case AFK -> idle(cue.hasFlag(CueFlags.SLEEPY), ageTicks, seed, w);
            // NORMAL has nothing to show, and SPEAKING is Layer 1/2 only: DESIGN.md §7
            // gives Layer 3 three motions, and inventing a fourth for voice would move
            // the mod's own goalposts rather than implement them.
            case NORMAL, SPEAKING -> PoseFrame.NONE;
        };
    }

    /**
     * Hands out on a keyboard, tapping. The two hands are given independent
     * pseudo-random tap streams rather than one stream in opposite phase: real
     * typing is not a perfect left-right alternation, and one shared stream is
     * exactly what makes an animation look mechanical.
     */
    private static PoseFrame typing(int intensity, float ageTicks, int seed, float w) {
        float busy = clamp01(intensity / 255f);
        float seconds = ageTicks / TICKS_PER_SECOND;

        float hz = lerp(TYPING_MIN_HZ, TYPING_MAX_HZ, busy);
        float tap = lerp(TYPING_MIN_TAP, TYPING_MAX_TAP, busy);
        float drift = sine(seconds, TYPING_DRIFT_HZ) * TYPING_DRIFT;

        // Each hand runs its own tap stream, offset in the hash space so they are
        // uncorrelated: sometimes they land together, mostly they do not.
        float rightTap = tapStream(seconds, hz, seed) * tap;
        float leftTap = tapStream(seconds, hz, seed ^ 0x5bf03635) * tap;
        // ...and its own slow wander across the keys.
        float rightReach = noiseAt(seconds, 1.1f, seed ^ 0x27d4eb2f) * TYPING_REACH_YAW;
        float leftReach = noiseAt(seconds, 1.1f, seed ^ 0x165667b1) * TYPING_REACH_YAW;

        Limb right = new Limb(
                TYPING_ARM_PITCH + rightTap + drift,
                TYPING_ARM_YAW + rightReach,
                TYPING_ARM_ROLL);
        Limb left = new Limb(
                TYPING_ARM_PITCH + leftTap + drift,
                -TYPING_ARM_YAW + leftReach,
                -TYPING_ARM_ROLL);
        Limb head = new Limb(TYPING_HEAD_PITCH + drift * 0.5f, drift * 0.6f, 0f);

        return scale(right, left, head, Limb.ZERO,
                1f, TYPING_SCREEN_TILT, TYPING_SCREEN_RISE, w);
    }

    /**
     * One hand holds the panel steady while the other works it. The asymmetry
     * is the whole point: two arms doing the same thing reads as "carrying a
     * box", one still and one moving reads as "using it".
     */
    private static PoseFrame inScreen(float ageTicks, int seed, float w) {
        float seconds = ageTicks / TICKS_PER_SECOND;
        float wobble = sine(seconds, SCREEN_WOBBLE_HZ);
        float wobble2 = sine(seconds, SCREEN_WOBBLE_HZ_2);

        // The holding arm gets only the panel's own wobble — it is holding, not working.
        Limb hold = new Limb(
                SCREEN_HOLD_PITCH + wobble * SCREEN_WOBBLE_ARM,
                0f,
                -SCREEN_HOLD_ROLL);

        // The working arm moves from slot to slot: a quick settle, then a pause,
        // then off to somewhere else — never a continuous sweep.
        float reachPitch = reach(seconds, seed) * SCREEN_REACH_PITCH;
        float reachYaw = reach(seconds, seed ^ 0x9e3779b9) * SCREEN_REACH_YAW;
        float reachRoll = reach(seconds, seed ^ 0x85ebca6b) * SCREEN_REACH_ROLL;
        Limb work = new Limb(
                SCREEN_WORK_PITCH + reachPitch + wobble * SCREEN_WOBBLE_ARM,
                reachYaw,
                SCREEN_WORK_ROLL + reachRoll);

        Limb head = new Limb(SCREEN_HEAD_PITCH + wobble2 * 0.03f, wobble2 * 0.05f, 0f);
        float tilt = SCREEN_BASE_TILT + wobble * SCREEN_WOBBLE_TILT + wobble2 * (SCREEN_WOBBLE_TILT * 0.5f);

        // The right arm works, the left holds — DESIGN.md pins neither, so this
        // simply follows the majority-handed reading of the demo material.
        return scale(work, hold, head, Limb.ZERO, 1f, tilt, SCREEN_RISE, w);
    }

    /**
     * Nodding off. The droop itself is carried by {@code weight} (PoseBlend
     * ramps idle in far more slowly than the other two, which is what makes the
     * head sink rather than snap), so this adds the living part: a slow nod, the
     * head lolling sideways, and the body shifting its weight underneath — three
     * motions on three frequencies that never line up.
     */
    private static PoseFrame idle(boolean sleepy, float ageTicks, int seed, float w) {
        float seconds = ageTicks / TICKS_PER_SECOND;
        float rate = sleepy ? SLEEPY_RATE_SCALE : 1f;
        float droop = sleepy ? SLEEPY_HEAD_PITCH : AFK_HEAD_PITCH;
        // A per-player phase offset, so a room full of idle players does not sway as one.
        float phase = noise(seed) * 10f;

        float nod = sine(seconds + phase, AFK_NOD_HZ * rate) * AFK_NOD;
        float headSway = sine(seconds + phase, AFK_HEAD_SWAY_HZ * rate) * AFK_HEAD_SWAY;
        float loll = sine(seconds + phase * 1.3f, AFK_HEAD_SWAY_HZ * rate * 0.8f) * AFK_HEAD_LOLL;

        float bodyPhase = sine(seconds + phase * 0.7f, AFK_BODY_SWAY_HZ * rate);
        float bodyPhase2 = sine(seconds + phase * 1.9f, AFK_BODY_SWAY_HZ * rate * 1.37f);

        Limb head = new Limb(droop + nod, headSway, loll);
        Limb body = new Limb(
                bodyPhase2 * AFK_BODY_PITCH,
                bodyPhase2 * AFK_BODY_YAW,
                bodyPhase * AFK_BODY_ROLL);
        // The body leans, so the arms hanging off it lean with it, plus a little extra sag when asleep.
        float armSag = sleepy ? SLEEPY_ARM_SAG : 0f;
        Limb right = new Limb(armSag, 0f, bodyPhase * AFK_BODY_ROLL * 0.5f);
        Limb left = new Limb(armSag, 0f, bodyPhase * AFK_BODY_ROLL * 0.5f);

        return scale(right, left, head, body, 0f, 0f, 0f, w);
    }

    private static PoseFrame scale(Limb right, Limb left, Limb head, Limb body,
            float screenWeight, float screenTilt, float screenRise, float w) {
        return new PoseFrame(
                right.scaled(w), left.scaled(w), head.scaled(w), body.scaled(w),
                screenWeight * w, screenTilt * w, screenRise);
    }

    // ------------------------------------------------------------- motion tools

    /**
     * A tap: mostly resting, with a quick dip when a key goes down. Modelled as
     * a sharp attack and slower release rather than a sine, because that is
     * what a finger actually does — and the tap only happens on steps the hash
     * selects, so the rhythm is uneven.
     */
    private static float tapStream(float seconds, float hz, int seed) {
        float t = seconds * hz;
        int step = (int) Math.floor(t);
        float within = t - step;
        // Roughly seven steps in ten carry a tap; the rest are the pauses that
        // stop the rhythm from being a metronome.
        if (noise(step * 31 + seed) > 0.4f) {
            return 0f;
        }
        float strength = 0.6f + 0.4f * (noise(step * 17 + seed) * 0.5f + 0.5f);
        // Attack over the first fifth of the step, release over the rest.
        float shape = within < 0.2f ? (within / 0.2f) : (1f - (within - 0.2f) / 0.8f);
        return shape * strength;
    }

    /**
     * A hand moving between slots: hold, then ease to a fresh target, then hold
     * again. {@link #smoothstep} over only the first part of each step is what
     * produces the pause.
     */
    private static float reach(float seconds, int seed) {
        float t = seconds / SCREEN_REACH_SECONDS;
        int step = (int) Math.floor(t);
        float within = t - step;
        float from = noise(step + seed);
        float to = noise(step + 1 + seed);
        float k = smoothstep(clamp01(within / SCREEN_REACH_MOVE_FRACTION));
        return from + (to - from) * k;
    }

    /** Smoothly varying pseudo-random value in -1..1, changing {@code hz} times a second. */
    private static float noiseAt(float seconds, float hz, int seed) {
        float t = seconds * hz;
        int step = (int) Math.floor(t);
        float k = smoothstep(t - step);
        float from = noise(step + seed);
        float to = noise(step + 1 + seed);
        return from + (to - from) * k;
    }

    /**
     * Deterministic hash to -1..1. An integer avalanche (the finaliser shape
     * used by every modern non-cryptographic hash) rather than a
     * {@code java.util.Random}: no state to keep, no allocation on the render
     * path, and the same player always looks the same on every client.
     */
    private static float noise(int value) {
        int x = value * 0x9e3779b9;
        x ^= x >>> 16;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        x ^= x >>> 16;
        return (x >>> 8) / (float) (1 << 23) - 1f;
    }

    private static float sine(float seconds, float hz) {
        return (float) Math.sin(seconds * hz * 2.0 * Math.PI);
    }

    private static float smoothstep(float t) {
        float x = clamp01(t);
        return x * x * (3f - 2f * x);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clamp01(float value) {
        if (Float.isNaN(value)) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, value));
    }
}
