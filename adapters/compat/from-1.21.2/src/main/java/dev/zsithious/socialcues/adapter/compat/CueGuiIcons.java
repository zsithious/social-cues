package dev.zsithious.socialcues.adapter.compat;

import dev.zsithious.socialcues.core.client.CueIconAtlas;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/**
 * DESIGN.md §14 P7 — "draw one cell of our own icon sheet into a GUI", the
 * 1.21.2–1.21.5 spelling. See the {@code from-1.21} copy of this class for the
 * {@code javap} measurement of all three forms and for why this seam is a
 * compat generation rather than a bucket split.
 *
 * <p>1.21.2 made the caller supply the render layer as a
 * {@code Function<Identifier, RenderLayer>} and moved the destination size to
 * <em>after</em> the UV. {@code RenderLayer.getGuiTextured} — measured as
 * existing on exactly 1.21.2–1.21.5, i.e. exactly this generation — is the
 * factory vanilla's own GUI texture draws pass here.
 */
public final class CueGuiIcons {

    private CueGuiIcons() {
    }

    /**
     * Draws atlas cell {@code cell} of {@code texture} as a {@code size}×{@code
     * size} icon with its top-left corner at ({@code x}, {@code y}) in GUI
     * space. Contract identical to every other generation's copy; see the
     * {@code from-1.21} one.
     */
    public static void drawAtlasCell(DrawContext context, Identifier texture, int cell, int x, int y, int size) {
        context.drawTexture(
                RenderLayer::getGuiTextured,
                texture,
                x, y,
                CueIconAtlas.column(cell) * CueIconAtlas.CELL_PIXELS,
                CueIconAtlas.row(cell) * CueIconAtlas.CELL_PIXELS,
                size, size,
                CueIconAtlas.CELL_PIXELS, CueIconAtlas.CELL_PIXELS,
                CueIconAtlas.TEXTURE_WIDTH, CueIconAtlas.TEXTURE_HEIGHT);
    }
}
