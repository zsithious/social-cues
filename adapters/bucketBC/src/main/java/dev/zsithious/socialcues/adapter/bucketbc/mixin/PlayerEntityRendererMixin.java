package dev.zsithious.socialcues.adapter.bucketbc.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.zsithious.socialcues.adapter.bucketbc.render.CueBillboardRenderer;
import dev.zsithious.socialcues.adapter.bucketbc.render.CueScreenPanelRenderer;
import dev.zsithious.socialcues.adapter.bucketbc.render.CueUuidHolder;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

/**
 * Bucket BC (1.21.5–1.21.8) counterpart of {@code
 * adapter.bucketd.mixin.PlayerEntityRendererMixin}. Same two additive hooks,
 * same reasoning — see that class's Javadoc, which is the canonical
 * explanation of <em>why</em> {@code renderLabelIfPresent}'s tail is the
 * correct hook and a {@code FeatureRenderer} is not (DESIGN.md §7's P4 hand
 * test). Only the two signatures differ, and both differences were measured
 * with {@code javap} on the 1.21.8 mapped jar rather than assumed:
 *
 * <ol>
 *   <li>{@code updateRenderState}'s entity parameter is {@link
 *       AbstractClientPlayerEntity}. {@code PlayerLikeEntity} — the supertype
 *       bucket D names, introduced alongside mannequins — does not exist in
 *       this range.</li>
 *   <li>{@code renderLabelIfPresent} is {@code (PlayerEntityRenderState, Text,
 *       MatrixStack, VertexConsumerProvider, int light)}. Bucket D's 1.21.9+
 *       {@code OrderedRenderCommandQueue}/{@code CameraRenderState} pair does
 *       not exist yet: drawing goes through a {@link VertexConsumerProvider},
 *       and the camera orientation has to be fetched rather than received (see
 *       {@link CueBillboardRenderer}). The {@code light} value bucket D reads
 *       off {@code state.light} arrives here as a parameter instead — {@code
 *       EntityRenderState} has no {@code light} field in this range — so it is
 *       threaded down into both renderers explicitly.</li>
 * </ol>
 *
 * <p><b>One behavioural difference worth recording,</b> also {@code javap
 * -c}-verified: in this range {@code EntityRenderer#render} calls {@code
 * renderLabelIfPresent} only when {@code state.displayName != null}, whereas
 * bucket D's hook runs for every rendered player. The outcome is identical
 * because both renderers already return early on a null {@code displayName}/
 * {@code nameLabelPos} (DESIGN.md §7: bind to the predicate that decides
 * whether vanilla would draw a name tag, rather than inventing a copy of it) —
 * the check is simply redundant here rather than load-bearing.
 *
 * <p>{@code PlayerEntityRenderer}'s override is balanced ({@code push()} …
 * {@code pop()}, {@code javap -c}-verified on 1.21.8 exactly as on 1.21.11),
 * so {@code @At("TAIL")} sees the caller's clean, world-axis-aligned,
 * camera-translated stack — the space {@code nameLabelPos} is expressed in,
 * which is the whole reason this hook was chosen.
 */
@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(
            method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;"
                    + "Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void socialcues$captureUuid(AbstractClientPlayerEntity entity, PlayerEntityRenderState state,
            float tickDelta, CallbackInfo ci) {
        ((CueUuidHolder) (Object) state).socialcues$setUuid(entity.getUuid());
    }

    @Inject(
            method = "renderLabelIfPresent(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;"
                    + "Lnet/minecraft/text/Text;"
                    + "Lnet/minecraft/client/util/math/MatrixStack;"
                    + "Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("TAIL"))
    private void socialcues$drawBillboard(PlayerEntityRenderState state, Text label, MatrixStack matrices,
            VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        CueBillboardRenderer.renderGuarded(state, matrices, vertexConsumers, light);
        // Independently guarded, exactly as in bucket D: a held-panel bug must
        // never take Layer 1's billboard icon down with it, or vice versa.
        CueScreenPanelRenderer.renderGuarded(state, matrices, vertexConsumers, light);
    }
}
