package dev.zsithious.socialcues.adapter.bucketd.render;

import dev.zsithious.socialcues.mcshared.client.FeatureRendererBootstrap;

import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.render.entity.PlayerEntityRenderer;

/**
 * DESIGN.md §7 P4b — bucketD's {@link FeatureRendererBootstrap} provider (see
 * that interface's Javadoc for why this indirection exists at all instead of
 * a direct call from {@code mcshared.SocialCuesClientInitializer}), declared
 * as a {@link java.util.ServiceLoader} provider in
 * {@code META-INF/services/dev.zsithious.socialcues.mcshared.client.FeatureRendererBootstrap}.
 *
 * <p>Filters {@code LivingEntityFeatureRendererRegistrationCallback.EVENT}
 * down to {@link PlayerEntityRenderer} instances only — see
 * {@link CueBillboardFeatureRenderer}'s class Javadoc for the {@code javap -c}
 * confirmation that this event fires (twice — once per skin variant) for
 * exactly those, via a dedicated {@code createPlayerEntityRenderer}
 * wrap-operation in fabric-rendering-v1, not the generic per-{@code EntityType}
 * path other living entities use.
 */
public final class BucketDFeatureRendererBootstrap implements FeatureRendererBootstrap {

    @Override
    public void register() {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer<?> playerRenderer) {
                helper.register(new CueBillboardFeatureRenderer(playerRenderer));
            }
        });
    }
}
