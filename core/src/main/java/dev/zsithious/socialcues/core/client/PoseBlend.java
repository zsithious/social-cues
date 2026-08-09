package dev.zsithious.socialcues.core.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §7 Katman 3 — how fast a player eases into and out of a pose.
 * Pure Java, no Minecraft, unit tested.
 *
 * <p><b>Why this is stateful at all.</b> A cue arrives as a step change:
 * one tick a player is {@code NORMAL}, the next they are {@code TYPING_CHAT}.
 * Applying {@link PoseAnimator}'s output directly would teleport the arms into
 * position, which looks broken and — worse for DESIGN.md §11 — collides
 * violently with any other animation mod mid-frame. This class turns that step
 * into a ramp, and it needs one float of memory per player to do it.
 *
 * <p><b>Why the ramp is per-activity.</b> Watching the three motions, they are
 * not equally fast in life and must not be equally fast here: hands land on a
 * keyboard quickly, a panel is raised at a deliberate speed, and a head that
 * droops as fast as either of those does not read as "nodding off" — it reads
 * as "neck broken". Idle therefore ramps in over more than a second, and it is
 * the only one of the three whose ease-in speed carries most of the effect.
 * Every pose leaves faster than it arrives: waking up is a snap, not a fade.
 *
 * <p><b>Why the pose is remembered while it fades.</b> The moment a player
 * stops typing their cue becomes {@code NORMAL}, which has no pose at all —
 * so fading out has to keep animating the pose that is <em>leaving</em>. This
 * class hands that back through {@link Blend#cue()}: while weight is falling,
 * that is the old cue, not the new one.
 *
 * <p>Not thread safe: the render thread is the only caller.
 */
public final class PoseBlend {

    /** Seconds to reach full strength, per pose family. See the class Javadoc for why they differ. */
    private static final float TYPING_EASE_IN_SECONDS = 0.22f;
    private static final float SCREEN_EASE_IN_SECONDS = 0.35f;
    private static final float IDLE_EASE_IN_SECONDS = 1.60f;
    /** Leaving is uniform and quick: any pose is gone within a quarter second of the cue that caused it. */
    private static final float EASE_OUT_SECONDS = 0.25f;

    /** Below this the pose is indistinguishable from vanilla, so the entry is dropped rather than kept at ~0 forever. */
    private static final float DEAD_ZONE = 0.001f;

    private final Map<UUID, State> states = new HashMap<>();

    /** One player's blended pose: the cue actually being animated, and how strongly. */
    public record Blend(PlayerCue cue, float weight) {

        public Blend {
            Objects.requireNonNull(cue, "cue");
        }
    }

    private static final class State {
        /** The pose currently being animated — kept while it fades, even after the cue that caused it is gone. */
        private PlayerCue posed;
        /** A pose of a <em>different</em> family waiting for {@link #posed} to fade out first. See {@link #update}. */
        private PlayerCue queued;
        private float weight;
    }

    /**
     * Advances {@code id}'s blend by {@code deltaSeconds} toward {@code target}
     * and returns what to draw this frame.
     *
     * @param id      the player
     * @param target  their current cue, or {@code null} if they have none
     *                (unknown player, muted, or simply {@code NORMAL})
     * @param deltaSeconds frame time; clamped to a sane maximum so that a
     *                     stutter, a debug pause or a long GC does not teleport
     *                     every pose in a single frame
     * @return {@code null} when there is nothing to draw for this player
     */
    public Blend update(UUID id, PlayerCue target, float deltaSeconds) {
        Objects.requireNonNull(id, "id");
        float dt = clampDelta(deltaSeconds);
        boolean wantsPose = target != null && hasPose(target.activity());

        State state = states.get(id);
        if (state == null) {
            if (!wantsPose) {
                return null; // Nothing to animate and nothing to remember: stay out of the map entirely.
            }
            state = new State();
            states.put(id, state);
        }

        if (!wantsPose) {
            state.queued = null;
        } else if (state.posed == null) {
            state.posed = target;
        } else if (samePoseFamily(state.posed, target)) {
            // Same motion, fresher numbers: chat -> sign is still "typing", so keep
            // the weight and just take the newer intensity/flags. Restarting the ramp
            // here would make a player who switches between chat and a sign visibly
            // drop their arms and raise them again for no reason.
            state.posed = target;
            state.queued = null;
        } else {
            // A different motion entirely (the realistic case being idle -> typing,
            // since the others can only reach each other through NORMAL). Swapping
            // the pose at full weight would teleport the limbs between two unrelated
            // positions in one frame. Instead the old pose is allowed to fade out
            // first and the new one is held here until it has; the head comes up,
            // then the arms go down onto the keyboard, in that order, which is also
            // the order a person does it in.
            state.queued = target;
        }

        boolean rising = wantsPose && state.queued == null;
        float rate = rising ? easeInSeconds(state.posed.activity()) : EASE_OUT_SECONDS;
        state.weight = approach(state.weight, rising ? 1f : 0f, dt / rate);

        if (state.weight <= DEAD_ZONE && !rising) {
            if (state.queued != null) {
                // The dip has bottomed out: adopt the waiting pose and ramp back up
                // from here on the next call.
                state.posed = state.queued;
                state.queued = null;
                state.weight = 0f;
                return new Blend(state.posed, 0f);
            }
            states.remove(id);
            return null;
        }

        return new Blend(state.posed, smoothstep(state.weight));
    }

    /** Drops a player's blend outright — used when they leave, or the layer is switched off. */
    public void forget(UUID id) {
        states.remove(id);
    }

    /** Drops every blend. DESIGN.md §5: a disconnect must leave nothing behind. */
    public void clear() {
        states.clear();
    }

    /**
     * Drops every tracked player that is not in {@code present}. A blend is
     * only ever retired by {@link #update} easing it to zero, so a player who
     * stopped being passed to {@code update} at all — because they left — would
     * otherwise be remembered for the rest of the session at whatever weight
     * they left with. The caller, which is the one that knows who is still
     * here, calls this once per tick after updating everyone.
     */
    public void retainOnly(Set<UUID> present) {
        Objects.requireNonNull(present, "present");
        states.keySet().retainAll(present);
    }

    /** Visible for tests: how many players are currently mid-animation. */
    public int trackedCount() {
        return states.size();
    }

    private static boolean hasPose(Activity activity) {
        return switch (activity) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK, IN_SCREEN, AFK -> true;
            case NORMAL, SPEAKING -> false;
        };
    }

    /**
     * Two cues animate "the same way" when they drive the same motion — all
     * four typing activities share one pose, so switching from chat to a sign
     * must not restart the ramp.
     */
    private static boolean samePoseFamily(PlayerCue a, PlayerCue b) {
        return family(a.activity()) == family(b.activity());
    }

    private static int family(Activity activity) {
        return switch (activity) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK -> 1;
            case IN_SCREEN -> 2;
            case AFK -> 3;
            case NORMAL, SPEAKING -> 0;
        };
    }

    private static float easeInSeconds(Activity activity) {
        return switch (activity) {
            case TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN, TYPING_BOOK -> TYPING_EASE_IN_SECONDS;
            case IN_SCREEN -> SCREEN_EASE_IN_SECONDS;
            case AFK -> IDLE_EASE_IN_SECONDS;
            case NORMAL, SPEAKING -> EASE_OUT_SECONDS; // unreachable via update(), kept exhaustive on purpose
        };
    }

    private static float approach(float current, float target, float step) {
        if (step <= 0f || Float.isNaN(step)) {
            return current;
        }
        if (current < target) {
            return Math.min(target, current + step);
        }
        return Math.max(target, current - step);
    }

    /**
     * The linear ramp is eased here rather than in {@link #approach} so the
     * stored weight stays a plain, testable 0..1 progress value while what the
     * renderer sees starts and stops gently.
     */
    private static float smoothstep(float t) {
        float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }

    private static float clampDelta(float deltaSeconds) {
        if (Float.isNaN(deltaSeconds) || deltaSeconds <= 0f) {
            return 0f;
        }
        return Math.min(deltaSeconds, 0.25f);
    }
}
