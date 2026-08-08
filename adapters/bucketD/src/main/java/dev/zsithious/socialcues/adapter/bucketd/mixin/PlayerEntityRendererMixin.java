package dev.zsithious.socialcues.adapter.bucketd.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.adapter.bucketd.render.CueUuidHolder;

import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.PlayerLikeEntity;

/**
 * DESIGN.md §7 P4b — captures the id {@link CueUuidHolder} exists to carry,
 * straight from the live {@code Entity}, once per frame per rendered player.
 *
 * <p><b>{@code javap -c}-verified target (1.21.11):</b>
 * {@code PlayerEntityRenderer<AvatarlikeEntity extends PlayerLikeEntity & ClientPlayerLikeEntity>}
 * declares {@code updateRenderState(AvatarlikeEntity, PlayerEntityRenderState, float)},
 * which — because {@code AvatarlikeEntity}'s leftmost bound is
 * {@code PlayerLikeEntity} — erases to the real bytecode descriptor
 * {@code (Lnet/minecraft/entity/PlayerLikeEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V}.
 * The compiler additionally emits <em>two</em> synthetic bridge overrides on
 * this same class ({@code (LivingEntity, LivingEntityRenderState, float)}
 * and {@code (Entity, EntityRenderState, float)}, both just forwarding calls)
 * — {@link Inject}'s {@code method} is given the exact descriptor string
 * above so Mixin can never accidentally resolve to one of the bridges
 * instead of the real method.
 *
 * <p><b>Why {@code PlayerLikeEntity}, not a player-specific type, has
 * {@code getUuid()}:</b> {@code PlayerLikeEntity} (base for both real
 * players and 1.21.9+'s new mannequin entities) extends {@code LivingEntity}
 * extends {@code Entity} — every entity has a UUID, so this works uniformly
 * for both, with no {@code instanceof} needed. A mannequin's id simply never
 * matches anything in {@code core.client.RemoteCueStore} or
 * {@code mcshared.client.LocalCueState}, so {@link
 * dev.zsithious.socialcues.adapter.bucketd.render.CueBillboardFeatureRenderer}
 * naturally renders nothing for one — no special-casing required there
 * either.
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
}
