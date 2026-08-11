package dev.zsithious.socialcues.adapter.bucketa.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.adapter.bucketa.render.CueBillboardRenderer;
import dev.zsithious.socialcues.adapter.bucketa.render.CueScreenPanelRenderer;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * Bucket A (1.21–1.21.1) counterpart of {@code
 * adapter.bucketbc.mixin.PlayerEntityRendererMixin}. Same hook, same two
 * additive calls, same reasoning — {@code adapter.bucketd.mixin
 * .PlayerEntityRendererMixin}'s Javadoc remains the canonical explanation of
 * why {@code renderLabelIfPresent}'s tail is the correct hook and a {@code
 * FeatureRenderer} is not (DESIGN.md §7's P4 hand test).
 *
 * <p><b>Two differences from bucket BC, both measured on the 1.21 mapped jar
 * rather than assumed:</b>
 *
 * <ol>
 *   <li><b>There is no {@code updateRenderState} hook, and no {@code
 *       CueUuidHolder}.</b> The {@code net.minecraft.client.render.entity
 *       .state} package does not exist before 1.21.2, so buckets BC and D's
 *       whole reason for those two extra mixins — a render state carries no
 *       player id, so one has to be smuggled onto it from the last place a
 *       mixin still holds the live {@code Entity} — simply does not arise
 *       here. The renderer <em>is</em> handed the entity, so {@code
 *       entity.getUuid()} is read at the point of use. Bucket A therefore
 *       ships three mixins where the other two ship four.</li>
 *   <li><b>The hook's signature ends in a {@code float tickDelta}</b>:
 *       {@code renderLabelIfPresent(AbstractClientPlayerEntity, Text,
 *       MatrixStack, VertexConsumerProvider, int light, float tickDelta)}. It
 *       is not decoration — both renderers need it, because every value bucket
 *       BC reads off a pre-interpolated render state (the name-tag attachment
 *       point, the entity age, the body yaw) has to be interpolated here
 *       instead, and this is the frame's own partial tick. It is threaded
 *       through to both.</li>
 * </ol>
 *
 * <p><b>The full descriptor in {@code method} is mandatory,</b> for exactly the
 * reason {@code PlayerEntityModelMixin} documents at greater length: {@code
 * javap} on this row shows {@code PlayerEntityRenderer} carrying both the real
 * {@code renderLabelIfPresent(AbstractClientPlayerEntity, ...)} and a compiler-
 * generated bridge {@code renderLabelIfPresent(Entity, ...)} from {@code
 * EntityRenderer<T>}'s erased type parameter. A bare method name would leave
 * Mixin two candidates on the same class.
 *
 * <p><b>The tail is a clean stack,</b> {@code javap -c}-verified on this row
 * the same way it was on 1.21.8 and 1.21.11: {@code
 * LivingEntityRenderer#render} pops the model transform before calling {@code
 * super.render}, which is what reaches {@code renderLabelIfPresent}; and this
 * method's own override is balanced ({@code push()} at offset 10, {@code pop()}
 * at 141, {@code return} at 145), so {@code @At("TAIL")} sees the caller's
 * world-axis-aligned, camera-translated stack — the space the name-tag
 * attachment offset is expressed in.
 *
 * <p><b>One consequence of that balance</b> is worth stating: the {@code
 * EntityRenderer#renderLabelIfPresent} this method delegates to can return
 * early (past 64 blocks, or with no name-tag attachment point), while this
 * method always runs on to its own {@code pop()} — so the tail fires either
 * way. In practice the distance half is unreachable, because {@code
 * LivingEntityRenderer#hasLabel} already gated the whole call at the same 64
 * blocks; the null-attachment half is real and {@code CueBillboardRenderer}
 * bails on it. Both bounds are restated there rather than assumed here — see
 * its {@code MAX_LABEL_DISTANCE_SQUARED} and its anchor null check.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/network/AbstractClientPlayerEntity;"
                    + "Lnet/minecraft/text/Text;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;IF)V",
            at = @At("TAIL"))
    private void socialcues$drawBillboard(AbstractClientPlayerEntity entity, Text label, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, float tickDelta, CallbackInfo ci) {
        CueBillboardRenderer.renderGuarded(entity, matrices, vertexConsumers, light, tickDelta);
        // Independently guarded, exactly as in buckets BC and D: a held-panel bug
        // must never take Layer 1's billboard icon down with it, or vice versa.
        CueScreenPanelRenderer.renderGuarded(entity, matrices, vertexConsumers, light, tickDelta);
    }
}
