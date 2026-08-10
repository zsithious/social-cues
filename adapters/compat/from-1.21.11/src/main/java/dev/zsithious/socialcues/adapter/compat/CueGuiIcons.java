package dev.zsithious.socialcues.adapter.compat;

import dev.zsithious.socialcues.core.client.CueIconAtlas;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * DESIGN.md §14 P7 — "draw one cell of our own icon sheet into a GUI", the
 * 1.21.6-and-later spelling. See the {@code from-1.21} copy of this class for
 * the {@code javap} measurement of all three forms and for why this seam is a
 * compat generation rather than a bucket split.
 *
 * <p>1.21.6 replaced the pixel-UV {@code drawTexture} overloads with
 * {@code drawTexturedQuad}, which takes an explicit destination rectangle and
 * normalised float UVs — so scaling a 16×16 cell down to an 8×8 icon needs no
 * separate "region size" arguments at all. This body is byte-for-byte the same
 * in the {@code from-1.21.9} and {@code from-1.21.11} copies: the seam that
 * separates <em>those</em> generations is {@link CueRenderLayers}, not this.
 *
 * <p><b>Argument order</b> is {@code (x1, y1, x2, y2)}, not
 * {@code (x1, x2, y1, y2)} — {@code javap -c} traced through both private
 * overloads. P4b assumed the inner order and drew a corner-swapped, off-screen
 * rectangle: no icon, no error, one wasted hand-test round.
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
        context.drawTexturedQuad(
                texture,
                x, y, x + size, y + size,
                CueIconAtlas.minU(cell), CueIconAtlas.maxU(cell),
                CueIconAtlas.minV(cell), CueIconAtlas.maxV(cell));
    }
}
