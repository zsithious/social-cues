package dev.zsithious.socialcues.adapter.compat;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * DESIGN.md §14 P7 — the pre-1.21.11 spelling of the translucent entity render
 * layer.
 *
 * <p>Measured, not guessed ({@code javap} over all twelve mapped Minecraft
 * jars): {@code RenderLayer.getEntityTranslucent(Identifier)} exists on 1.21
 * through 1.21.10 and is <em>gone</em> on 1.21.11, where the factory moved to
 * {@code RenderLayers.entityTranslucent(Identifier)} — same parameters, same
 * return type, different owner and name. Nothing else about the call changed.
 *
 * <p>This one-method rename is the <em>only</em> reason bucket D's rows do not
 * all compile from one source set, and it is why the compat layer exists as an
 * axis separate from {@code bucket}: 1.21.9/1.21.10/1.21.11 share every render
 * API that matters (the {@code OrderedRenderCommandQueue}/{@code
 * CameraRenderState} generation) and differ only here. Forking ~1400 lines of
 * renderer over one method name would have been the alternative, and DESIGN.md
 * §2 is explicit that the target is the fewest adapters that actually work.
 */
public final class CueRenderLayers {

    private CueRenderLayers() {
    }

    /** The layer Layer 1's billboard and Layer 3's held panel both draw into. */
    public static RenderLayer entityTranslucent(Identifier texture) {
        return RenderLayer.getEntityTranslucent(texture);
    }
}
