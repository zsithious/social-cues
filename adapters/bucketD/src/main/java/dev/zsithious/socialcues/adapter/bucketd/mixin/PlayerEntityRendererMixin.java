package dev.zsithious.socialcues.adapter.bucketd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.adapter.bucketd.render.CueBillboardRenderer;
import dev.zsithious.socialcues.adapter.bucketd.render.CueUuidHolder;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.PlayerLikeEntity;

/**
 * Two additive hooks on the player renderer, both of them things
 * {@link PlayerEntityRenderState} does not carry on its own.
 *
 * <p><b>1. The player's {@link java.util.UUID}.</b> Vanilla feature renderers
 * never need one — they are constructed alongside, and know the concrete type
 * of, their owning renderer — so the render state records only what vanilla's
 * own drawing consumes ({@code id}, an entity network id, is not a
 * {@code UUID} and is not what the cue store is keyed by). This copies it
 * across at {@code updateRenderState} time into the field
 * {@code PlayerEntityRenderStateMixin} adds; see {@link CueUuidHolder}.
 *
 * <p><b>2. Katman 1's draw call.</b> {@code @At("TAIL")} of
 * {@code renderLabelIfPresent} is the one player-rendering hook that runs with
 * the matrix stack still in the space {@code nameLabelPos} is expressed in —
 * see {@link CueBillboardRenderer}'s Javadoc for the {@code javap}-verified
 * reasoning, and for why P4b's original {@code FeatureRenderer} hook (which
 * receives the flipped, yaw-rotated <em>model</em> stack instead) could not
 * place the icon correctly. Vanilla's own override here is balanced
 * ({@code push()} … {@code pop()}), so TAIL sees the caller's stack unchanged;
 * drawing after it also puts our icon in front of the labels in submission
 * order rather than fighting them.
 *
 * <p>Both hooks are plain additive {@code @Inject}s that read and cancel
 * nothing, so they coexist with any other mod's hooks on the same methods.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/entity/PlayerLikeEntity;"
                    + "Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void socialcues$captureUuid(PlayerLikeEntity entity, PlayerEntityRenderState state, float tickDelta,
            CallbackInfo ci) {
        ((CueUuidHolder) (Object) state).socialcues$setUuid(entity.getUuid());
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;"
                    + "Lnet/minecraft/client/render/state/CameraRenderState;)V",
            at = @At("TAIL"))
    private void socialcues$drawBillboard(PlayerEntityRenderState state, MatrixStack matrices,
            OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        CueBillboardRenderer.renderGuarded(state, matrices, queue, camera);
    }
}
