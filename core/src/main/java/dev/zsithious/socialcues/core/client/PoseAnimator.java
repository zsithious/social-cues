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
 *
 * <p><b>Model-space sign rule (DESIGN.md §7 P5 hand-test lesson — read this
 * before touching any yaw/roll constant below):</b> {@code javap}-verified
 * against {@code ModelPart}: a limb's rotations compose as {@code Rz(roll) ·
 * Ry(yaw) · Rx(pitch)} and its long axis is model {@code +Y} (down). The
 * player faces model {@code -Z}; the player's own right is model {@code -X}.
 * On both arms, <b>positive yaw and positive roll swing the limb's tip toward
 * the player's right</b> — roll's effect additionally scales by
 * {@code cos(pitch)}. That means "tuck this hand in toward the body" is
 * <b>negative</b> yaw/roll on the {@code rightArm} and <b>positive</b>
 * yaw/roll on the {@code leftArm} — never the other way around, and never the
 * same sign on both arms. (Anchor: vanilla's own crossbow-hold animation gives
 * the right arm {@code yaw=-0.3} and the left arm {@code yaw=+0.6} to bring
 * both hands together at the chest.) A P5 hand test shipped every yaw/roll
 * constant below with exactly the opposite sign on both arms, which reads as
 * "hands flung out to the sides" instead of "hands tucked in" — see DESIGN.md
 * §7's "P5 el testi bulguları" section for the full account. Every pose below
 * now follows the rule as written; if you add a new one, follow it too.
 *
 * <p><b>{@code reducedMotion} (P6 §4.1, DESIGN.md §9 "animasyon yok, sadece
 * statik ikon"): "every time-varying term is removed; state changes still
 * happen."</b> Concretely, every {@code sine}/{@code noise}/{@code noiseAt}/
 * {@code reach}/{@code tapStream} call below feeds the per-tick jitter —
 * bob, tap, drift, reach, wobble, nod, sway, loll — that this class's own
 * Javadoc above spends several paragraphs justifying as "what makes it read
 * as a comic-strip snore" rather than "machinery". {@code reducedMotion}
 * zeroes exactly those terms, one call site at a time, and leaves everything
 * else — the base arm pose a hand rests at between taps, the panel's base
 * tilt, {@code busy}/{@code intensity}-driven amplitudes, {@code sleepy}'s
 * deeper droop and arm sag, and {@code headAim}/{@code weight}'s blend
 * ramps — untouched. The result is not "no pose": a reduced-motion typing
 * player still has their arms up and hands tucked in exactly where the full
 * animation would rest between taps, a reduced-motion {@code IN_SCREEN}
 * player still holds the panel up at its resting tilt with one arm at its
 * base "working" angle, and a reduced-motion {@code AFK}/{@code SLEEPY}
 * player's head still droops to the depth that state implies — all of it
 * held perfectly still, because {@code weight}'s blend-in/out is a state
 * change (a pose appearing or fading is not "motion" in the sense this
 * switch means) and the constant-valued rest pose underneath the jitter is
 * likewise state, not motion. See {@code PoseAnimatorTest}'s {@code
 * reducedMotion} cases for exactly which fields this zeroes per activity.
 */
public final class PoseAnimator {

    /** Ticks per second — the unit conversion between {@code ageTicks} and the frequencies below. */
    private static final float TICKS_PER_SECOND = 20f;

    // ------------------------------------------------- typing (a chat window)

    /**
     * Shoulder rotation for hands out on a keyboard (~60° forward). This is
     * deliberately shallower than the in-screen pose: a keyboard sits out in
     * front of you at desk height, not clasped up against your chest. At this
     * pitch the hand sits roughly 0.65 blocks in front of the shoulder, about
     * y≈1.00 — see {@code CueScreenPanelRenderer}'s panel placement, tuned to
     * sit just behind that point.
     */
    private static final float TYPING_ARM_PITCH = -1.05f;
    /**
     * Inward tuck at the elbow — DESIGN.md §7 P5 hand-test fix: applied with
     * the model-space sign rule documented on this class (negative on the
     * right arm, positive on the left), not the same sign on both, which is
     * what made the original pose fling the hands outward instead of tucking
     * them in.
     */
    private static final float TYPING_ARM_ROLL = 0.05f;
    /**
     * Inward yaw so both hands land close together at chest width instead of
     * shoulder width (~0.625 blocks apart at the shoulders down to ~0.47
     * blocks at the hands) — same sign-rule fix as {@link #TYPING_ARM_ROLL}.
     */
    private static final float TYPING_ARM_YAW = 0.12f;
    /** Head tips down to the keyboard and the window above it (~24°) — see {@link PoseFrame#headAimPitch()}. */
    private static final float TYPING_HEAD_AIM_PITCH = 0.42f;

    /**
     * Key taps per second at {@code intensity} 0 … 255. DESIGN.md §7 P5
     * hand-test fix: a tap is a wrist flick, not an arm swing, so this reads
     * faster and shallower (see {@link #TYPING_MIN_TAP}/{@link #TYPING_MAX_TAP})
     * than the original tuning.
     */
    private static final float TYPING_MIN_HZ = 3.5f;
    private static final float TYPING_MAX_HZ = 9.0f;
    /** How far a hand dips on a tap, likewise scaled by intensity — faster typing is visibly busier. */
    private static final float TYPING_MIN_TAP = 0.06f;
    private static final float TYPING_MAX_TAP = 0.11f;
    /** Sideways reach across the keys: hands do not stay on home row. */
    private static final float TYPING_REACH_YAW = 0.045f;
    /** Slow whole-body settle laid under the tapping, so the pose is never perfectly still. */
    private static final float TYPING_DRIFT_HZ = 0.37f;
    private static final float TYPING_DRIFT = 0.02f;
    /**
     * The sideways "dart" a hand gets on every tap, on top of the vertical dip
     * — DESIGN.md §7 P5 hand-test fix: without it, hands only bob up and down
     * in place, which does not read as moving between keys.
     */
    private static final float TYPING_TAP_DART_YAW = 0.03f;
    /**
     * DESIGN.md §7 P5 second hand-test fix ("iki kol aynı anda aynı yöne
     * hareket ediyor, titriyor gibi"): how far each hand's own tap rate is
     * allowed to drift from the shared {@code hz}, ±18%. See {@link
     * #tapStream}'s own Javadoc for why a per-hand rate (not just a per-hand
     * hash) is what actually breaks the two hands out of lockstep.
     */
    private static final float TYPING_RATE_JITTER = 0.18f;

    private static final float TYPING_SCREEN_TILT = 0.12f;
    private static final float TYPING_SCREEN_RISE = 0.88f;

    // -------------------------------------------- in a screen (a container GUI)

    /** The holding arm: up at reading height and held there. */
    private static final float SCREEN_HOLD_PITCH = -1.30f;
    /** Left arm (holds); model-space sign rule: inward tuck is POSITIVE roll on the left. */
    private static final float SCREEN_HOLD_ROLL = 0.16f;
    /** The working arm's resting point, before the reaching below moves it around the panel. */
    private static final float SCREEN_WORK_PITCH = -1.22f;
    /** Right arm (works); model-space sign rule: inward tuck is NEGATIVE roll on the right. */
    private static final float SCREEN_WORK_ROLL = 0.10f;

    /** How long the working hand takes to settle on a new slot, and how long it lingers there. */
    private static final float SCREEN_REACH_SECONDS = 0.62f;
    private static final float SCREEN_REACH_MOVE_FRACTION = 0.45f;
    /** Reach envelope: how far the working hand ranges over the panel. */
    private static final float SCREEN_REACH_PITCH = 0.30f;
    /** Kept inside the (now narrower, DESIGN.md §7 P5 hand-test fix) panel's own width. */
    private static final float SCREEN_REACH_YAW = 0.26f;
    private static final float SCREEN_REACH_ROLL = 0.14f;

    /** Head-aim pitch target for this pose — see {@link PoseFrame#headAimPitch()}, which now drives the actual look direction. */
    private static final float SCREEN_HEAD_AIM_PITCH = 0.32f;
    /** Resting lean of the panel's top edge toward the player (~13°) — DESIGN.md §7 P5 hand-test fix: was far too upright-reading before. */
    private static final float SCREEN_BASE_TILT = 0.22f;
    private static final float SCREEN_RISE = 0.95f;
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
     * @param cue           the player's current cue; its activity, flags and
     *                      intensity drive the motion, and its id seeds the
     *                      pseudo-randomness so two players never move
     *                      identically
     * @param ageTicks      the rendered entity's age in ticks, fractional
     * @param weight        0..1 blend weight; values outside are clamped
     * @param reducedMotion P6 §4.1: when {@code true}, every per-tick
     *                      oscillation term is zeroed — see this class's
     *                      Javadoc for exactly what that does and does not
     *                      change
     * @return never {@code null}; exactly {@link PoseFrame#NONE} at weight 0
     */
    public static PoseFrame frameFor(PlayerCue cue, float ageTicks, float weight, boolean reducedMotion) {
        Objects.requireNonNull(cue, "cue");
        float w = clamp01(weight);
        if (w <= 0f) {
            return PoseFrame.NONE;
        }
        int seed = cue.id().hashCode();
        return switch (cue.activity()) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK ->
                    typing(cue.intensity(), ageTicks, seed, w, reducedMotion);
            case IN_SCREEN -> inScreen(ageTicks, seed, w, reducedMotion);
            case AFK -> idle(cue.hasFlag(CueFlags.SLEEPY), ageTicks, seed, w, reducedMotion);
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
    private static PoseFrame typing(int intensity, float ageTicks, int seed, float w, boolean reducedMotion) {
        float busy = clamp01(intensity / 255f);
        float seconds = ageTicks / TICKS_PER_SECOND;

        float hz = lerp(TYPING_MIN_HZ, TYPING_MAX_HZ, busy);
        float tapAmount = lerp(TYPING_MIN_TAP, TYPING_MAX_TAP, busy);

        // DESIGN.md §7 P5 second hand-test fix: one shared `drift` added to
        // BOTH arms is common-mode motion -- exactly the kind of thing that
        // reads as "the two arms moving together" from outside, independent
        // of whatever the tap streams below are doing. Each arm now gets its
        // own phase-shifted copy instead (same frequency, different offset
        // into the sine, derived from the seed the same way every other
        // per-hand split in this method already is).
        //
        // P6 §4.1: every term below that is zeroed under reducedMotion is the
        // per-tick oscillation this class's own Javadoc calls out (drift/tap/
        // reach); what is left after all three are zero is exactly the
        // steady "hands up, tucked in, resting between taps" pose.
        float rightDrift = reducedMotion ? 0f : sine(seconds + noise(seed) * 3f, TYPING_DRIFT_HZ) * TYPING_DRIFT;
        float leftDrift = reducedMotion ? 0f
                : sine(seconds + noise(seed ^ 0x1b873593) * 3f, TYPING_DRIFT_HZ) * TYPING_DRIFT;

        // Each hand runs its own tap stream -- see tapStream's own Javadoc for
        // why that now has to mean its own time grid (rate + phase), not just
        // its own hash: two calls sharing `seconds`/`hz` land on the exact
        // same step/within every time, so only WHETHER a step taps differed
        // between hands, never WHEN -- read from outside as "both arms
        // twitching in sync" (DESIGN.md §7 P5 second hand-test finding).
        Tap rightTap = reducedMotion ? Tap.NONE : tapStream(seconds, hz, seed);
        Tap leftTap = reducedMotion ? Tap.NONE : tapStream(seconds, hz, seed ^ 0x5bf03635);
        // ...and its own slow wander across the keys.
        float rightReach = reducedMotion ? 0f : noiseAt(seconds, 1.1f, seed ^ 0x27d4eb2f) * TYPING_REACH_YAW;
        float leftReach = reducedMotion ? 0f : noiseAt(seconds, 1.1f, seed ^ 0x165667b1) * TYPING_REACH_YAW;

        // Model-space sign rule (see this class' Javadoc): tucking a hand IN
        // toward the body's centre is negative yaw/roll on the right arm,
        // positive on the left -- DESIGN.md §7 P5 hand-test fix, both arms
        // used to carry the same sign here and flung the hands outward instead.
        Limb right = new Limb(
                TYPING_ARM_PITCH + rightTap.dip() * tapAmount + rightDrift,
                -TYPING_ARM_YAW + rightReach + rightTap.dart(),
                -TYPING_ARM_ROLL);
        Limb left = new Limb(
                TYPING_ARM_PITCH + leftTap.dip() * tapAmount + leftDrift,
                TYPING_ARM_YAW + leftReach + leftTap.dart(),
                TYPING_ARM_ROLL);
        // Only the small living wobble here -- the actual "look down at the
        // keyboard" direction is now driven by the absolute headAim target
        // below (DESIGN.md §7 P5 hand-test fix: an additive-only offset can
        // never override wherever the head already happened to be facing).
        // Uses the average of both arms' drift, not either one alone, so the
        // head's tiny settle does not quietly favour whichever hand happens
        // to be computed first.
        float headDrift = (rightDrift + leftDrift) * 0.5f;
        Limb head = new Limb(headDrift * 0.5f, headDrift * 0.6f, 0f);

        // headAim ramps in at twice the rate of the rest of the pose (see
        // PoseFrame's Javadoc) so the head is already looking at the keyboard
        // by the time the arms have barely started rising -- "look, then
        // reach", the order a person actually does it in, and the fix for
        // the hand-test complaint that the head could stay facing wherever it
        // last was through an entire typing animation.
        float headAim = clamp01(w * 2f);
        return scale(right, left, head, Limb.ZERO,
                1f, TYPING_SCREEN_TILT, TYPING_SCREEN_RISE, w,
                TYPING_HEAD_AIM_PITCH, 0f, headAim);
    }

    /**
     * One hand holds the panel steady while the other works it. The asymmetry
     * is the whole point: two arms doing the same thing reads as "carrying a
     * box", one still and one moving reads as "using it".
     */
    private static PoseFrame inScreen(float ageTicks, int seed, float w, boolean reducedMotion) {
        float seconds = ageTicks / TICKS_PER_SECOND;
        // P6 §4.1: wobble/reach are this pose's per-tick oscillation terms;
        // zeroing them under reducedMotion leaves the panel at its resting
        // tilt with the working arm at its base (un-reached-to-a-slot) angle.
        float wobble = reducedMotion ? 0f : sine(seconds, SCREEN_WOBBLE_HZ);
        float wobble2 = reducedMotion ? 0f : sine(seconds, SCREEN_WOBBLE_HZ_2);

        // The holding arm gets only the panel's own wobble — it is holding, not
        // working. Left arm; model-space sign rule (see this class' Javadoc):
        // tucking IN toward the body is POSITIVE roll on the left arm.
        Limb hold = new Limb(
                SCREEN_HOLD_PITCH + wobble * SCREEN_WOBBLE_ARM,
                0f,
                SCREEN_HOLD_ROLL);

        // The working arm moves from slot to slot: a quick settle, then a pause,
        // then off to somewhere else — never a continuous sweep.
        float reachPitch = reducedMotion ? 0f : reach(seconds, seed) * SCREEN_REACH_PITCH;
        float reachYaw = reducedMotion ? 0f : reach(seconds, seed ^ 0x9e3779b9) * SCREEN_REACH_YAW;
        float reachRoll = reducedMotion ? 0f : reach(seconds, seed ^ 0x85ebca6b) * SCREEN_REACH_ROLL;
        // Right arm; model-space sign rule: tucking IN is NEGATIVE roll on the right.
        Limb work = new Limb(
                SCREEN_WORK_PITCH + reachPitch + wobble * SCREEN_WOBBLE_ARM,
                reachYaw,
                -SCREEN_WORK_ROLL + reachRoll);

        // Only the small living wobble here -- see typing()'s identical comment:
        // the actual look-at-the-panel direction is now the absolute headAim
        // target below, not this additive offset.
        Limb head = new Limb(wobble2 * 0.03f, wobble2 * 0.05f, 0f);
        float tilt = SCREEN_BASE_TILT + wobble * SCREEN_WOBBLE_TILT + wobble2 * (SCREEN_WOBBLE_TILT * 0.5f);

        // headAim ramps in at twice the rate of the rest of the pose -- see
        // typing()'s identical comment and PoseFrame's Javadoc.
        float headAim = clamp01(w * 2f);

        // The right arm works, the left holds — DESIGN.md pins neither, so this
        // simply follows the majority-handed reading of the demo material.
        return scale(work, hold, head, Limb.ZERO, 1f, tilt, SCREEN_RISE, w,
                SCREEN_HEAD_AIM_PITCH, 0f, headAim);
    }

    /**
     * Nodding off. The droop itself is carried by {@code weight} (PoseBlend
     * ramps idle in far more slowly than the other two, which is what makes the
     * head sink rather than snap), so this adds the living part: a slow nod, the
     * head lolling sideways, and the body shifting its weight underneath — three
     * motions on three frequencies that never line up.
     */
    private static PoseFrame idle(boolean sleepy, float ageTicks, int seed, float w, boolean reducedMotion) {
        float seconds = ageTicks / TICKS_PER_SECOND;
        float rate = sleepy ? SLEEPY_RATE_SCALE : 1f;
        float droop = sleepy ? SLEEPY_HEAD_PITCH : AFK_HEAD_PITCH;
        // A per-player phase offset, so a room full of idle players does not sway as one.
        float phase = noise(seed) * 10f;

        // P6 §4.1: nod/sway/loll/bodyPhase* are this pose's per-tick oscillation
        // terms; zeroing them under reducedMotion leaves exactly `droop` (and,
        // below, `armSag`) — the state droop/sag already carries for sleepy vs.
        // plain AFK, held still rather than nodding/swaying on top of it.
        float nod = reducedMotion ? 0f : sine(seconds + phase, AFK_NOD_HZ * rate) * AFK_NOD;
        float headSway = reducedMotion ? 0f : sine(seconds + phase, AFK_HEAD_SWAY_HZ * rate) * AFK_HEAD_SWAY;
        float loll = reducedMotion ? 0f
                : sine(seconds + phase * 1.3f, AFK_HEAD_SWAY_HZ * rate * 0.8f) * AFK_HEAD_LOLL;

        float bodyPhase = reducedMotion ? 0f : sine(seconds + phase * 0.7f, AFK_BODY_SWAY_HZ * rate);
        float bodyPhase2 = reducedMotion ? 0f : sine(seconds + phase * 1.9f, AFK_BODY_SWAY_HZ * rate * 1.37f);

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

    /** Used by {@code idle()}, which has no head-aim target: forwards to the full overload with everything zeroed. */
    private static PoseFrame scale(Limb right, Limb left, Limb head, Limb body,
            float screenWeight, float screenTilt, float screenRise, float w) {
        return scale(right, left, head, body, screenWeight, screenTilt, screenRise, w, 0f, 0f, 0f);
    }

    /**
     * @param headAimPitch  absolute head-aim pitch target, unscaled (see {@link PoseFrame}'s Javadoc)
     * @param headAimYaw    absolute head-aim yaw target, unscaled
     * @param headAim       0..1 blend strength, already a function of {@code w} (see callers) -- deliberately
     *                      NOT multiplied by {@code w} again here, the same way {@code screenRise} is a
     *                      position rather than an intensity and is left alone below
     */
    private static PoseFrame scale(Limb right, Limb left, Limb head, Limb body,
            float screenWeight, float screenTilt, float screenRise, float w,
            float headAimPitch, float headAimYaw, float headAim) {
        return new PoseFrame(
                right.scaled(w), left.scaled(w), head.scaled(w), body.scaled(w),
                screenWeight * w, screenTilt * w, screenRise,
                headAimPitch, headAimYaw, headAim);
    }

    // ------------------------------------------------------------- motion tools

    /**
     * One hand's tap this instant: the vertical dip (unitless envelope, 0..1-ish,
     * the caller scales it into radians by the intensity-driven tap amplitude)
     * and a small lateral "dart" (already in radians) that moves the hand toward
     * a different key rather than just bobbing it in place. Both come from the
     * same step so a tap's dart rises and falls with its own dip, instead of
     * the hand darting sideways during the pauses between taps too.
     */
    private record Tap(float dip, float dart) {
        private static final Tap NONE = new Tap(0f, 0f);
    }

    /**
     * A tap: mostly resting, with a quick dip when a key goes down. Modelled as
     * a sharp attack and slower release rather than a sine, because that is
     * what a finger actually does — and the tap only happens on steps the hash
     * selects, so the rhythm is uneven.
     *
     * <p><b>DESIGN.md §7 P5 second hand-test fix — "iki kol aynı anda aynı
     * yöne hareket ediyor, titriyor gibi".</b> {@code typing()} calls this
     * once per hand with the same {@code seconds}/{@code hz} and only a
     * different {@code seed}. That used to be enough to make the two calls
     * land on the exact same {@code step}/{@code within} every single time —
     * the seed only changes {@code noise(step * 31 + seed)} (whether THIS
     * step taps) and {@code noise(step * 17 + seed)} (how hard), never {@code
     * step} or {@code within} themselves, because those come from {@code t =
     * seconds * hz} alone. Giving two hands independent hashes but one shared
     * time grid is not independence: whenever the hash happened to select
     * both hands' step, both attacked and released in perfect lockstep,
     * which is exactly what read as "twitching together" from outside. The
     * fix has to change the grid itself, not just what rides on it: each
     * call now derives its own {@code rate} (±{@link #TYPING_RATE_JITTER}
     * around {@code hz}) and its own {@code phase} offset from {@code seed},
     * so the two hands' steps constantly drift relative to each other and
     * never stay locked for long.
     */
    private static Tap tapStream(float seconds, float hz, int seed) {
        float rate = hz * (1f + noise(seed ^ 0x7f4a7c15) * TYPING_RATE_JITTER);
        float phase = noise(seed ^ 0x2545f491) * 0.5f + 0.5f; // 0..1, in step units
        float t = seconds * rate + phase;
        int step = (int) Math.floor(t);
        float within = t - step;
        // DESIGN.md §7 P5 hand-test fix: most steps now carry a tap (only
        // noise values above 0.55, out of noise()'s roughly -1..1 range, skip
        // one) -- the original 0.4 threshold read as too sparse/hesitant for
        // "typing", the pauses that stop it from being a metronome are still
        // there, just rarer.
        if (noise(step * 31 + seed) > 0.55f) {
            return Tap.NONE;
        }
        // DESIGN.md §7 P5 second hand-test fix: a wider spread of tap
        // strengths (some taps now land barely visible) -- every tap landing
        // at roughly the same depth read as a uniform buzz rather than
        // distinct key presses.
        float strength = 0.35f + 0.65f * (noise(step * 17 + seed) * 0.5f + 0.5f);
        // Attack over the first ~15% of the step (was 20%): a sharper flick,
        // release over the rest -- DESIGN.md §7 P5 hand-test fix, part of
        // making taps read as quick key presses rather than slow swings.
        float shape = within < 0.15f ? (within / 0.15f) : (1f - (within - 0.15f) / 0.85f);
        float dip = shape * strength;
        // DESIGN.md §7 P5 hand-test fix: a small sideways kick on the same
        // step's hash, scaled by this tap's own envelope so it rises and
        // falls with the dip -- what makes the hand read as moving between
        // keys instead of just bobbing the same spot up and down.
        float dart = noise(step * 13 + seed) * TYPING_TAP_DART_YAW * dip;
        return new Tap(dip, dart);
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
