package dev.zsithious.socialcues.adapter.bucketd.mixin;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.adapter.bucketd.render.CueUuidHolder;
import dev.zsithious.socialcues.core.client.PoseAnimator;
import dev.zsithious.socialcues.core.client.PoseBlend;
import dev.zsithious.socialcues.core.client.PoseFrame;
import dev.zsithious.socialcues.mcshared.client.PoseBlendDriver;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * DESIGN.md §7 Katman 3, P5a — limb/head animation. Adds {@code
 * core.client.PoseAnimator}'s offsets on top of whatever vanilla (and any
 * other animation mod running earlier — see the priority note below) already
 * computed for this frame's arms/head. Does not touch {@link
 * PoseFrame#screenWeight()}/{@link PoseFrame#screenTilt()}: the held panel
 * they describe is P5b, out of this task's scope by DESIGN.md's own P5a brief.
 *
 * <p><b>Target and why the full descriptor is mandatory:</b> {@code javap
 * -c}-verified on the 1.21.11 mapped jar, {@link PlayerEntityModel} declares
 * three overloads of {@code setAngles}: the real one, {@code
 * setAngles(PlayerEntityRenderState)}; a bridge {@code
 * setAngles(BipedEntityRenderState)} that {@code PlayerEntityModel} itself
 * introduces (its body just casts and forwards to the real one, satisfying
 * {@code BipedEntityModel<PlayerEntityRenderState>}'s erased supertype
 * contract); and a second bridge, {@code setAngles(Object)}, from {@code
 * EntityModel<T>}'s own erased type parameter. All three share the name
 * {@code setAngles}; without an exact bytecode descriptor in {@code method},
 * Mixin has three candidates to choose from on the same class, and picking
 * the wrong one (a bridge) would either inject into a method whose body is
 * just a cast-and-forward — running before the real angles are computed, not
 * after — or fail to apply at all. The exact descriptor removes the
 * ambiguity entirely: {@code
 * setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V}.
 *
 * <p><b>Priority (DESIGN.md §7: "geç uygulanır"):</b> a high {@link
 * Mixin#priority} means this injector's bytecode is woven in after
 * lower-priority mixins targeting the same method, so if another animation
 * mod (Not Enough Animations, Emotecraft, Epic Fight, ...) also injects at
 * {@code setAngles}'s {@code TAIL} with a normal priority, its changes to
 * {@code head}/{@code rightArm}/{@code leftArm} run first and this mixin's
 * {@code +=} adds on top of that result rather than the other way around —
 * matching {@code adapter.bucketd.mixin.PlayerListHudMixin}'s existing
 * precedent for "applied late" in this codebase.
 *
 * <p><b>Overlay parts (hat/jacket/sleeves/pants) need no extra work — two
 * separate {@code javap}-verified facts, both DESIGN.md's P5a task note
 * asked to "confirm, do not assume":</b>
 * <ol>
 *   <li>{@code hat} is a child of {@code head}, and {@code leftSleeve}/
 *       {@code rightSleeve} are children of {@code leftArm}/{@code rightArm}
 *       — not separate model roots. Verified two ways: {@code
 *       BipedEntityModel}'s constructor resolves {@code hat} via {@code
 *       head.getChild("hat")} (not {@code root.getChild(...)}), and {@code
 *       PlayerEntityModel}'s own constructor resolves {@code leftSleeve}/
 *       {@code rightSleeve} via {@code leftArm.getChild("left_sleeve")}/
 *       {@code rightArm.getChild("right_sleeve")}; separately, {@code
 *       PlayerEntityModel.getTexturedModelData} builds {@code left_sleeve}/
 *       {@code right_sleeve} by calling {@code addChild} on the {@code
 *       ModelPartData} already returned for {@code left_arm}/{@code
 *       right_arm}, not on the tree root.</li>
 *   <li>{@code ModelPart#render} (the only place {@code pitch}/{@code yaw}/
 *       {@code roll} are ever consumed) pushes the matrix stack, applies
 *       <em>this</em> part's own transform, renders its own cuboids, then —
 *       still inside that same pushed, already-transformed stack — recurses
 *       into every child part before popping. A child's rotation is
 *       therefore always relative to its parent's, automatically, with no
 *       separate copy step. This is also, independently, why {@code
 *       PlayerEntityModel#setAngles} itself never contains a {@code
 *       copyTransform}-shaped call for these parts (as DESIGN.md's task note
 *       suspected from reading the source): {@code javap -c} shows its body
 *       only flips the {@code ModelPart#visible} booleans for {@code hat}/
 *       {@code jacket}/{@code leftPants}/{@code rightPants}/{@code
 *       leftSleeve}/{@code rightSleeve} from the render state's matching
 *       flags, then delegates the actual angles to {@code
 *       BipedEntityModel#setAngles} — there is nothing else to copy.</li>
 * </ol>
 * Net effect: adding {@code frame.headPitch()}/{@code headYaw()} to {@code
 * this.head} moves the hat with it, and adding the arm offsets to {@code
 * this.rightArm}/{@code this.leftArm} moves the matching sleeve, with no
 * extra line of code required for either.
 *
 * <p><b>Never throws</b> — same guarded stance as every other Layer 1/2/3
 * hook in this codebase ({@code ClientCueCapture.tickGuarded}, {@code
 * CueBillboardRenderer.renderGuarded}, {@code PlayerListHudMixin}): one bad
 * frame disables Layer 3's limb animation for the rest of the session and
 * logs exactly once, at {@code SEVERE}, never {@code FINE} (DESIGN.md §7's
 * P4 hand-test note: a swallowed, invisible-by-default log hid a real bug
 * for an entire session once already).
 */
@Mixin(value = PlayerEntityModel.class, priority = 2000)
public class PlayerEntityModelMixin {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** See the class Javadoc: one loud line, then quiet, never a per-frame log flood. */
    private static boolean socialcues$disabledByError;

    @Inject(
            method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V",
            at = @At("TAIL"))
    private void socialcues$applyPose(PlayerEntityRenderState state, CallbackInfo ci) {
        if (socialcues$disabledByError) {
            return;
        }
        try {
            socialcues$apply(state);
        } catch (Throwable t) {
            socialcues$disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: layer 3 (pose) rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }

    private void socialcues$apply(PlayerEntityRenderState state) {
        if (!ClientConfigState.get().layer3Enabled()) {
            return; // Cheapest possible bail before any lookup (DESIGN.md §3.5 precedent: a disabled layer costs nothing).
        }

        UUID id = ((CueUuidHolder) (Object) state).socialcues$getUuid();
        if (id == null) {
            return; // Not captured for this state (e.g. a render state our PlayerEntityRendererMixin hook never saw).
        }

        Optional<PoseBlend.Blend> blendOpt = PoseBlendDriver.blendFor(id);
        if (blendOpt.isEmpty()) {
            return; // Nothing tracked for this player right now (no cue, or PoseBlendDriver hasn't ticked since a fresh join).
        }
        PoseBlend.Blend blend = blendOpt.get();

        PoseFrame frame = PoseAnimator.frameFor(blend.cue(), state.age, blend.weight());
        if (frame.isIdentity()) {
            return; // weight rounded to nothing worth drawing, or the cue's activity has no Layer 3 pose (NORMAL/SPEAKING).
        }

        // PoseFrame's offsets are meant to be *added* to whatever vanilla (and any
        // earlier-priority animation mod) already computed -- see PoseFrame's own
        // Javadoc for why. hat/jacket/sleeves/pants need no separate update; see
        // this class's Javadoc for the javap-verified parent/child proof.
        PlayerEntityModel self = (PlayerEntityModel) (Object) this;
        socialcues$add(self.rightArm, frame.rightArm());
        socialcues$add(self.leftArm, frame.leftArm());
        socialcues$add(self.head, frame.head());
        socialcues$add(self.body, frame.body());
    }

    private static void socialcues$add(ModelPart part, PoseFrame.Limb limb) {
        part.pitch += limb.pitch();
        part.yaw += limb.yaw();
        part.roll += limb.roll();
    }
}
