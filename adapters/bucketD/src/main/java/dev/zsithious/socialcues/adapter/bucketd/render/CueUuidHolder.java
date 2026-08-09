package dev.zsithious.socialcues.adapter.bucketd.render;

import java.util.UUID;

/**
 * DESIGN.md §7 P4b — a "duck interface" mixed into {@code PlayerEntityRenderState}
 * by {@code adapter.bucketd.mixin.PlayerEntityRenderStateMixin}, populated by
 * {@code adapter.bucketd.mixin.PlayerEntityRendererMixin}, and read by
 * {@link CueBillboardRenderer}.
 *
 * <p><b>Why a render state needs this at all:</b> {@code javap -c}-verified
 * on the 1.21.11 mapped jar — {@code net.minecraft.client.render.entity.state.EntityRenderState}
 * (and its {@code PlayerEntityRenderState} subtype) carries {@code x}/{@code y}/
 * {@code z}/{@code displayName}/{@code nameLabelPos}/... but no player id at
 * all. This is intentional upstream: render states exist precisely so the
 * render thread never has to touch the live {@code Entity} (or its UUID)
 * again once the state snapshot is built — but Social Cues needs exactly
 * that id to look the player up in {@code core.client.RemoteCueStore} /
 * {@code mcshared.client.LocalCueState}, which are keyed by {@code UUID}, not
 * by anything a render state already carries. Rather than re-deriving a
 * lookup key from {@code displayName} (a {@code Text}, not reliably a plain
 * username — team prefixes/suffixes, custom name formatting mods, etc. can
 * all alter it), the id is captured once, straight from the source
 * {@code Entity}, at the one point a mixin still has both: {@code
 * PlayerEntityRenderer#updateRenderState}.
 *
 * <p>Named with the {@code socialcues$}-prefixed method convention this
 * project's existing mixin (none yet outside this bucket) would be expected
 * to follow, matching the wider Fabric modding convention for mixin-added
 * members: impossible to collide with a real Minecraft/other-mod member name.
 */
public interface CueUuidHolder {

    UUID socialcues$getUuid();

    void socialcues$setUuid(UUID uuid);
}
