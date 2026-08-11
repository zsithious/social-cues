package dev.zsithious.socialcues.adapter.bucketa.mixin;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.core.client.BillboardCueVisibility;
import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.PoseAnimator;
import dev.zsithious.socialcues.core.client.PoseBlend;
import dev.zsithious.socialcues.core.client.PoseFrame;
import dev.zsithious.socialcues.mcshared.client.PoseBlendDriver;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.LivingEntity;

/**
 * DESIGN.md §7 Katman 3, P5a — limb/head animation, the 1.21–1.21.1 spelling.
 * Adds {@code core.client.PoseAnimator}'s offsets on top of whatever vanilla
 * (and any earlier-priority animation mod) already computed for this frame's
 * arms/head/body, exactly like {@code
 * adapter.bucketbc.mixin.PlayerEntityModelMixin}, whose Javadoc remains the
 * canonical account of the priority choice, the guarded-never-throws stance,
 * and the P6 §4.3 {@code showOnSelf}/mute/distance rules applied through
 * {@link BillboardCueVisibility#passesSharedRules} (and never {@code
 * shouldRender}, which would fold Layer 1's own gate in).
 *
 * <p>Three things genuinely differ on these two rows. All three were measured
 * with {@code javap -c} on the 1.21 mapped jar.
 *
 * <h2>1. The target is the pre-render-state {@code setAngles}</h2>
 *
 * {@code setAngles(T entity, float limbAngle, float limbDistance, float
 * animationProgress, float headYaw, float headPitch)}, erased to {@code
 * (Lnet/minecraft/entity/LivingEntity;FFFFF)V}. As on the newer rows the full
 * descriptor is mandatory rather than stylistic: {@code PlayerEntityModel}
 * carries this override <em>and</em> a compiler-generated {@code
 * setAngles(Entity, float, float, float, float, float)} bridge from {@code
 * EntityModel<T extends Entity>}'s own erased parameter, so a bare name leaves
 * Mixin two candidates on one class and the wrong pick is a cast-and-forward
 * body that runs before the real angles exist.
 *
 * <p>A welcome side effect of this signature: {@code animationProgress}
 * <em>is</em> the value the newer buckets read as {@code state.age} — {@code
 * LivingEntityRenderer#getAnimationProgress} returns {@code entity.age +
 * tickDelta}, and {@code EntityRenderer#updateRenderState} fills {@code
 * state.age} with the same expression. So the pose clock arrives as a
 * parameter here instead of as a field, with no derivation needed.
 *
 * <h2>2. The overlay parts are copies, not children — so they are re-copied</h2>
 *
 * This is the one substantive behavioural difference in bucket A's Layer 3,
 * and getting it wrong is invisible at compile time and obvious in game (an
 * arm that rotates while its sleeve stays put). Bucket BC's Javadoc records,
 * correctly for <em>its</em> rows, that {@code hat}/{@code jacket}/{@code
 * sleeves}/{@code pants} need no extra work because they are children of
 * {@code head}/{@code body}/{@code arms}/{@code legs} and {@code
 * ModelPart#render} composes a child's transform inside its parent's. On 1.21
 * that is not how the player model is built:
 *
 * <ul>
 *   <li>{@code BipedEntityModel}'s constructor resolves {@code hat} as {@code
 *       root.getChild("hat")} — a <em>sibling</em> of {@code head}, not a
 *       child of it — and its {@code setAngles} ends with {@code
 *       this.hat.copyTransform(this.head)}.</li>
 *   <li>{@code PlayerEntityModel#setAngles} calls {@code super.setAngles}
 *       <em>first</em> and then copies {@code leftPants}←{@code leftLeg},
 *       {@code rightPants}←{@code rightLeg}, {@code leftSleeve}←{@code
 *       leftArm}, {@code rightSleeve}←{@code rightArm}, {@code jacket}←{@code
 *       body}.</li>
 * </ul>
 *
 * Every one of those copies has therefore already happened by the time this
 * injector runs at {@code TAIL}, using the angles as they were <em>before</em>
 * our offsets. So the four overlays whose base part this mixin actually moves
 * are copied again afterwards, in {@link #socialcues$syncOverlayParts}. The
 * legs are never touched by {@link PoseFrame}, so {@code leftPants}/{@code
 * rightPants} are deliberately left alone. ({@code ModelPart#copyTransform}
 * copies scale, pitch/yaw/roll and pivot — {@code javap -c}-verified — so
 * re-running it restores the exact tracking vanilla intended, rather than
 * approximating it.)
 *
 * <h2>3. Nothing resets the model between frames — so the offsets come back off</h2>
 *
 * The sibling of section 2, and the same root cause: on this row the player
 * model is not rebuilt from scratch each frame. From 1.21.2 on, {@code
 * EntityModel#setAngles(S)}'s entire body is {@code this.resetTransforms()},
 * so every part starts each frame at its default transform and buckets BC/D
 * can add offsets freely — whatever they add is gone by the next frame. 1.21
 * has no such call anywhere in the chain (measured: {@code javap -c} on {@code
 * EntityModel} shows an abstract {@code setAngles} and no reset; {@code
 * Model#resetTransforms} does not yet exist), so a part axis keeps last
 * frame's value unless {@code BipedEntityModel#setAngles} happens to assign
 * it.
 *
 * <p>Which axes it assigns was measured the same way, over that one method's
 * bytecode, separating unconditional writes from ones inside a branch:
 *
 * <ul>
 *   <li>assigned every frame, unconditionally: {@code head.yaw}, {@code
 *       body.yaw}, and all three axes of both arms;</li>
 *   <li>assigned only on some paths: {@code head.pitch};</li>
 *   <li>never assigned: {@code head.roll}, {@code body.pitch}, {@code
 *       body.roll}.</li>
 * </ul>
 *
 * A plain {@code +=} on that last group therefore accumulates. It is not
 * subtle in game: {@code PoseAnimator}'s AFK body sway is a ±0.045 rad sine,
 * but summing a 0.077 Hz sine once per frame at 60 fps integrates it to an
 * amplitude near 5.6 rad, so the torso swings through most of a full turn —
 * which is exactly how it was found, on the first 1.21 hand test.
 *
 * <p>The fix keeps the additive contract intact rather than reaching for
 * {@code ModelPart#resetTransform}: this mixin remembers the offset it applied
 * and subtracts exactly that again at {@code HEAD} of the next {@code
 * setAngles}, before vanilla recomputes. Restoring by assignment, or resetting
 * the parts outright, would also discard offsets some other animation mod left
 * on the model — this takes back only what it put there, which is the same
 * "play well with earlier-priority animation mods" stance the priority choice
 * above is making. The delta is recorded as an after-minus-before difference,
 * not as the values handed to {@link PoseFrame}, because {@code headAim} moves
 * the head by an absolute lerp rather than by an addition.
 *
 * <p>All four parts this mixin touches are undone, including the arms whose
 * axes vanilla was just measured to always assign. Making the undo depend on
 * that per-axis table would rebuild the very assumption that broke here, for a
 * saving of twelve float subtractions per rendered player per frame.
 *
 * <h2>4. The player check is an {@code instanceof}, not a smuggled id</h2>
 *
 * Buckets BC and D get "only ever a real player" for free: they read the id
 * from a {@code CueUuidHolder} that only {@code
 * PlayerEntityRenderer#updateRenderState} ever populates. Here the entity
 * arrives directly and its static type is {@code LivingEntity}, because that
 * is {@code T}'s erasure — so the same guarantee is restated as an explicit
 * {@code instanceof AbstractClientPlayerEntity}. It is not redundant: {@code
 * PlayerEntityModel} is a plain {@code BipedEntityModel} subclass, and a
 * third-party mod rendering some other {@code LivingEntity} with a player
 * model would otherwise reach a {@code PoseBlendDriver} lookup keyed by that
 * entity's id.
 */
@Mixin(value = PlayerEntityModel.class, priority = 2000)
public class PlayerEntityModelMixin {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** See the class Javadoc: one loud line, then quiet, never a per-frame log flood. */
    private static boolean socialcues$disabledByError;

    /**
     * The offsets this mixin added to {@code head}/{@code body}/{@code
     * leftArm}/{@code rightArm} (pitch, yaw, roll, in that order, three per
     * part) on the previous {@code setAngles} call on this model instance —
     * subtracted again before vanilla recomputes. See the class Javadoc's
     * section 3 for why this row needs it at all. Null until the first frame
     * that actually poses someone; allocated once, then reused.
     *
     * <p>Per-instance rather than per-player on purpose: one {@code
     * PlayerEntityModel} renders every player in turn, and what has to be
     * undone is always whatever the immediately preceding call left on this
     * object, whichever player that was.
     */
    @Unique
    private float[] socialcues$lastDelta;

    /**
     * Takes back the previous frame's offsets, before {@code super.setAngles}
     * computes this frame's angles on top. Deliberately not guarded by {@link
     * #socialcues$disabledByError}: if Layer 3 threw and switched itself off,
     * the offsets it left behind still have to come off once.
     */
    @Inject(
            method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At("HEAD"))
    private void socialcues$undoPose(LivingEntity entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        float[] delta = socialcues$lastDelta;
        if (delta == null) {
            return;
        }
        PlayerEntityModel<?> self = (PlayerEntityModel<?>) (Object) this;
        socialcues$subtract(self.head, delta, 0);
        socialcues$subtract(self.body, delta, 3);
        socialcues$subtract(self.leftArm, delta, 6);
        socialcues$subtract(self.rightArm, delta, 9);
        Arrays.fill(delta, 0f);
    }

    @Inject(
            method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL"))
    private void socialcues$applyPose(LivingEntity entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (socialcues$disabledByError) {
            return;
        }
        try {
            socialcues$apply(entity, animationProgress);
        } catch (Throwable t) {
            socialcues$disabledByError = true;
            if (socialcues$lastDelta != null) {
                // Might hold a half-finished snapshot (absolute angles, not a
                // delta); subtracting that next frame would be far worse than
                // the pose we are giving up on.
                Arrays.fill(socialcues$lastDelta, 0f);
            }
            LOGGER.log(Level.SEVERE, "socialcues: layer 3 (pose) rendering threw and has been disabled "
                    + "for this session. This is a bug — please report it.", t);
        }
    }

    private void socialcues$apply(LivingEntity entity, float animationProgress) {
        ClientConfigData config = ClientConfigState.get();
        if (!config.layer3Enabled()) {
            return; // Cheapest possible bail before any lookup (DESIGN.md §3.5 precedent: a disabled layer costs nothing).
        }

        if (!(entity instanceof AbstractClientPlayerEntity)) {
            return; // See the class Javadoc: this bucket's stand-in for "only render states we populated".
        }

        UUID id = entity.getUuid();
        Optional<PoseBlend.Blend> blendOpt = PoseBlendDriver.blendFor(id);
        if (blendOpt.isEmpty()) {
            return; // Nothing tracked for this player right now (no cue, or PoseBlendDriver hasn't ticked since a fresh join).
        }
        PoseBlend.Blend blend = blendOpt.get();

        // P6 §4.1: reducedMotion is the viewer's own setting, passed in rather
        // than read inside core -- see PoseAnimator's own Javadoc. animationProgress
        // is this row's spelling of state.age; see the class Javadoc.
        PoseFrame frame = PoseAnimator.frameFor(blend.cue(), animationProgress, blend.weight(), config.reducedMotion());
        if (frame.isIdentity()) {
            return; // weight rounded to nothing worth drawing, or the cue's activity has no Layer 3 pose (NORMAL/SPEAKING).
        }

        // P6 §4.3: showOnSelf, mute list, and max distance apply to the pose too.
        // Inputs gathered exactly like CueScreenPanelRenderer.render gathers them
        // for Layer 3's other half (the held panel); distance comes from the same
        // EntityRenderDispatcher the renderers use, since there is no render state
        // caching it on this row. passesSharedRules, not shouldRender.
        MinecraftClient client = MinecraftClient.getInstance();
        boolean isSelf = client.player != null && id.equals(client.player.getUuid());
        boolean thirdPerson = !client.options.getPerspective().isFirstPerson();
        double distance = Math.sqrt(client.getEntityRenderDispatcher().getSquaredDistanceToCamera(entity));
        String playerName = socialcues$resolvePlayerName(client, id);
        if (!BillboardCueVisibility.passesSharedRules(blend.cue(), isSelf, thirdPerson, distance, config, playerName)) {
            return;
        }

        // PoseFrame's offsets are meant to be *added* to whatever vanilla (and any
        // earlier-priority animation mod) already computed -- see PoseFrame's own
        // Javadoc for why.
        PlayerEntityModel<?> self = (PlayerEntityModel<?>) (Object) this;

        // Class Javadoc section 3: remember what this frame adds, so the next
        // one can take it off again. Holds the before-values until the very
        // end, where they are turned into after-minus-before.
        float[] delta = socialcues$lastDelta;
        if (delta == null) {
            delta = new float[12];
            socialcues$lastDelta = delta;
        }
        socialcues$readInto(self.head, delta, 0);
        socialcues$readInto(self.body, delta, 3);
        socialcues$readInto(self.leftArm, delta, 6);
        socialcues$readInto(self.rightArm, delta, 9);

        socialcues$add(self.rightArm, frame.rightArm());
        socialcues$add(self.leftArm, frame.leftArm());

        // DESIGN.md §7 P5 hand-test fix: headAim, when present, blends the head to
        // an *absolute* target FIRST (an additive offset can only nudge whatever
        // angle the head already had, which is what let it keep facing a stale
        // direction through an entire pose). The small additive nod/sway in
        // frame.head() is then layered on top.
        if (frame.headAim() > 0f) {
            self.head.pitch = socialcues$lerp(self.head.pitch, frame.headAimPitch(), frame.headAim());
            self.head.yaw = socialcues$lerp(self.head.yaw, frame.headAimYaw(), frame.headAim());
        }
        socialcues$add(self.head, frame.head());
        socialcues$add(self.body, frame.body());

        socialcues$syncOverlayParts(self);

        socialcues$diffInto(self.head, delta, 0);
        socialcues$diffInto(self.body, delta, 3);
        socialcues$diffInto(self.leftArm, delta, 6);
        socialcues$diffInto(self.rightArm, delta, 9);
    }

    private static void socialcues$readInto(ModelPart part, float[] delta, int i) {
        delta[i] = part.pitch;
        delta[i + 1] = part.yaw;
        delta[i + 2] = part.roll;
    }

    private static void socialcues$diffInto(ModelPart part, float[] delta, int i) {
        delta[i] = part.pitch - delta[i];
        delta[i + 1] = part.yaw - delta[i + 1];
        delta[i + 2] = part.roll - delta[i + 2];
    }

    private static void socialcues$subtract(ModelPart part, float[] delta, int i) {
        part.pitch -= delta[i];
        part.yaw -= delta[i + 1];
        part.roll -= delta[i + 2];
    }

    /**
     * Re-runs the four {@code copyTransform} calls vanilla already performed
     * earlier in this same {@code setAngles} invocation, now that the base
     * parts have moved — see the class Javadoc's section 2 for the measured
     * model-structure difference that makes this necessary here and pointless
     * on 1.21.2+.
     *
     * <p>Only the four whose base part this mixin actually changes: {@code
     * hat} follows {@code head}, {@code jacket} follows {@code body}, and the
     * two sleeves follow their arms. {@code leftPants}/{@code rightPants} are
     * omitted on purpose — {@link PoseFrame} has no leg terms at all, so
     * vanilla's own copy of the leg transforms is still exactly right, and
     * re-copying them would be a claim about this class that stops being true
     * the day legs are added without anyone noticing.
     */
    private static void socialcues$syncOverlayParts(PlayerEntityModel<?> model) {
        model.hat.copyTransform(model.head);
        model.jacket.copyTransform(model.body);
        model.leftSleeve.copyTransform(model.leftArm);
        model.rightSleeve.copyTransform(model.rightArm);
    }

    private static void socialcues$add(ModelPart part, PoseFrame.Limb limb) {
        part.pitch += limb.pitch();
        part.yaw += limb.yaw();
        part.roll += limb.roll();
    }

    /**
     * Same lookup, same fallback, as {@code CueBillboardRenderer}/{@code
     * CueScreenPanelRenderer}'s own {@code resolvePlayerName} — this codebase's
     * established pattern for this one-off glue: each render/mixin entry point
     * keeps its own small copy rather than sharing a utility for a three-line
     * lookup.
     */
    private static String socialcues$resolvePlayerName(MinecraftClient client, UUID id) {
        if (client.getNetworkHandler() == null) {
            return "";
        }
        PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(id);
        if (entry == null || entry.getProfile() == null) {
            return ""; // Not (yet) in the tab list; ClientConfigData.isMuted("") is simply never true.
        }
        // getName(), not name(): these rows ship authlib 6.x — see DESIGN.md §7's P7 note.
        return entry.getProfile().getName();
    }

    private static float socialcues$lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
