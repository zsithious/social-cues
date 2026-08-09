package dev.zsithious.socialcues.core.client;

/**
 * DESIGN.md §7 Katman 3 — one frame of pose animation for one player, as
 * <em>offsets in radians on top of whatever vanilla already computed</em>,
 * plus where the held panel sits.
 *
 * <p><b>Why offsets and not absolute angles:</b> DESIGN.md §7 requires that
 * turning Layer 3 off "iz bırakmıyor" (leaves no trace), and §11 requires
 * coexisting with other animation mods (Not Enough Animations, Emotecraft,
 * Epic Fight, first-person mods). A renderer that assigns absolute angles
 * fights every one of them and cannot be blended out; one that <em>adds</em>
 * to vanilla's result composes with them, and multiplying every field by a
 * weight of {@code 0} is exactly "vanilla, untouched". {@link PoseAnimator}
 * therefore already folds the blend weight into every number here — the
 * consumer just adds.
 *
 * <p><b>Units and signs</b> follow Minecraft's model convention, not maths
 * convention: angles are radians about the part's own pivot, {@code pitch} is
 * rotation about X where <em>positive tilts down/back and negative swings
 * forward-up</em>, {@code yaw} is about Y, {@code roll} is about Z (for an
 * arm, the "away from the body" axis). The two arms are mirror images, so a
 * symmetric pose gives them opposite {@code roll}/{@code yaw} signs.
 *
 * <p><b>The limbs are grouped rather than flattened</b> into a dozen loose
 * floats on purpose: every one of them is a {@code float} in radians, so a
 * flat constructor would accept any two of them swapped without a murmur from
 * the compiler, and a swapped arm is exactly the kind of bug that is invisible
 * in a diff and obvious only in game.
 *
 * <p><b>{@code headAimPitch}/{@code headAimYaw}/{@code headAim}</b> (added by
 * DESIGN.md §7's P5 hand-test fix for "the head can stay facing a stale
 * direction while the rest of the pose animates") are a second, independent
 * mechanism layered on top of {@code head}: instead of one more offset added
 * to whatever vanilla already computed, {@code headAimPitch}/{@code
 * headAimYaw} are <em>absolute</em>, model-relative target angles (0 means
 * "facing straight at whatever this pose orients the body/panel toward"), and
 * {@code headAim} is how strongly the consumer should blend the head's
 * current angle toward that target (0 = don't touch it at all, identical to
 * every other {@code Activity} that has no head aim; 1 = snap fully to the
 * target). A renderer is expected to {@code lerp} toward the target with
 * {@code headAim} *before* adding {@code head}'s own small nod/sway offsets on
 * top — see {@code PlayerEntityModelMixin.socialcues$apply} for the exact
 * order. This is deliberately not just another additive {@code Limb}: an
 * offset can only ever change an angle by some delta from whatever it already
 * was, which is exactly the bug being fixed (a player who turned to look
 * elsewhere keeps whatever stale head yaw they already had, offset by the
 * same small amount as everyone else). An absolute aim target is what lets
 * the pose say "look at the panel" regardless of where the head started.
 */
public record PoseFrame(
        Limb rightArm, Limb leftArm, Limb head, Limb body,
        float screenWeight, float screenTilt, float screenRise,
        float headAimPitch, float headAimYaw, float headAim) {

    /** Rotation offsets for one model part, radians. */
    public record Limb(float pitch, float yaw, float roll) {

        public static final Limb ZERO = new Limb(0f, 0f, 0f);

        public boolean isZero() {
            return pitch == 0f && yaw == 0f && roll == 0f;
        }

        /** Scales every axis — how {@link PoseAnimator} folds the blend weight in. */
        public Limb scaled(float factor) {
            return new Limb(pitch * factor, yaw * factor, roll * factor);
        }
    }

    /** The do-nothing frame: adding this to any pose leaves it byte-identical to vanilla. */
    public static final PoseFrame NONE =
            new PoseFrame(Limb.ZERO, Limb.ZERO, Limb.ZERO, Limb.ZERO, 0f, 0f, 0f, 0f, 0f, 0f);

    /** Below this, blending toward the aim target would be visually indistinguishable from not blending at all. */
    private static final float HEAD_AIM_EPSILON = 1e-4f;

    public PoseFrame {
        if (rightArm == null || leftArm == null || head == null || body == null) {
            throw new IllegalArgumentException("limbs must not be null; use Limb.ZERO");
        }
    }

    /**
     * True when this frame would not move anything and has no panel — lets a
     * renderer skip its work entirely. {@code headAim} counts too (see this
     * record's own Javadoc): a frame that only sets an absolute head target,
     * with every {@code Limb} still zero, is not vanilla-identical either.
     */
    public boolean isIdentity() {
        return screenWeight <= 0f && headAim <= HEAD_AIM_EPSILON
                && rightArm.isZero() && leftArm.isZero() && head.isZero() && body.isZero();
    }

    /**
     * True when a panel should be drawn at all. Both the typing pose (a chat
     * window) and the in-screen pose (a container GUI) have one; which texture
     * goes on it is the renderer's business, decided from the cue's own
     * activity and {@code ScreenKind}, not from here.
     */
    public boolean hasScreen() {
        return screenWeight > 0f;
    }
}
