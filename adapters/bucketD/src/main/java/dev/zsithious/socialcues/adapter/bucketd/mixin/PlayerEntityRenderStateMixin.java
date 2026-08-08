package dev.zsithious.socialcues.adapter.bucketd.mixin;

import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import dev.zsithious.socialcues.adapter.bucketd.render.CueUuidHolder;

import net.minecraft.client.render.entity.state.PlayerEntityRenderState;

/**
 * DESIGN.md §7 P4b — adds the field {@link CueUuidHolder} declares, nothing
 * else. Paired with {@link PlayerEntityRendererMixin}, which is the one that
 * actually populates it every frame from the real {@code Entity}; see
 * {@link CueUuidHolder}'s Javadoc for why a render state needs this at all.
 *
 * <p>Mixed into {@code PlayerEntityRenderState} specifically (not the more
 * general {@code EntityRenderState} base) because only player (and mannequin)
 * render states are ever fed to
 * {@link dev.zsithious.socialcues.adapter.bucketd.render.CueBillboardFeatureRenderer} —
 * see that class and {@code adapter.bucketd.render.BucketDFeatureRendererBootstrap}
 * for how the registration is scoped to player renderers only.
 */
@Mixin(PlayerEntityRenderState.class)
public class PlayerEntityRenderStateMixin implements CueUuidHolder {

    @Unique
    private UUID socialcues$uuid;

    @Override
    public UUID socialcues$getUuid() {
        return this.socialcues$uuid;
    }

    @Override
    public void socialcues$setUuid(UUID uuid) {
        this.socialcues$uuid = uuid;
    }
}
