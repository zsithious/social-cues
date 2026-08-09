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
 */
public record PoseFrame(
        Limb rightArm, Limb leftArm, Limb head, Limb body,
        float screenWeight, float screenTilt, float screenRise) {

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
            new PoseFrame(Limb.ZERO, Limb.ZERO, Limb.ZERO, Limb.ZERO, 0f, 0f, 0f);

    public PoseFrame {
        if (rightArm == null || leftArm == null || head == null || body == null) {
            throw new IllegalArgumentException("limbs must not be null; use Limb.ZERO");
        }
    }

    /**
     * True when this frame would not move anything and has no panel — lets a
     * renderer skip its work entirely.
     */
    public boolean isIdentity() {
        return screenWeight <= 0f
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
