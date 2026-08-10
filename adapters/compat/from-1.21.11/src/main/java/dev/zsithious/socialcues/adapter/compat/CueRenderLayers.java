package dev.zsithious.socialcues.adapter.compat;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Identifier;

/**
 * DESIGN.md §14 P7 — the 1.21.11 spelling of the translucent entity render
 * layer. See the {@code from-1.21} copy of this class for the {@code javap}
 * measurement of the seam and for why one method name is worth a compat layer
 * of its own; only the single call below differs between the two copies.
 *
 * <p>1.21.11 moved the factory off {@code RenderLayer} onto {@code
 * RenderLayers} and dropped the {@code get} prefix. Parameters and return type
 * are unchanged, so every caller is identical either side of the seam.
 */
public final class CueRenderLayers {

    private CueRenderLayers() {
    }

    /** The layer Layer 1's billboard and Layer 3's held panel both draw into. */
    public static RenderLayer entityTranslucent(Identifier texture) {
        return RenderLayers.entityTranslucent(texture);
    }
}
