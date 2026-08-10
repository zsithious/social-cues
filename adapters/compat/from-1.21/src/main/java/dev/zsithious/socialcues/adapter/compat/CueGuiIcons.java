package dev.zsithious.socialcues.adapter.compat;

import dev.zsithious.socialcues.core.client.CueIconAtlas;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * DESIGN.md §14 P7 — "draw one cell of our own icon sheet into a GUI", the
 * 1.21–1.21.1 spelling. This is Layer 2's (tab list) only platform call.
 *
 * <p><b>Measured, not guessed</b> ({@code javap -p -v} over all twelve mapped
 * Minecraft jars, reading the {@code LocalVariableTable} for the real parameter
 * names rather than inferring them from types). Drawing a <em>sub-rectangle</em>
 * of a texture, scaled — a 16×16 atlas cell into an 8×8 icon — has three
 * distinct spellings across the twelve rows, and this is the seam that made
 * DESIGN.md §2's hypothesised buckets B and C turn out to be one bucket with a
 * single divergent line:
 *
 * <table border="1">
 *   <caption>The three forms</caption>
 *   <tr><th>Rows</th><th>Signature</th></tr>
 *   <tr><td>1.21–1.21.1</td>
 *       <td>{@code drawTexture(Identifier, x, y, width, height, u, v,
 *           regionWidth, regionHeight, textureWidth, textureHeight)} — pixel UV</td></tr>
 *   <tr><td>1.21.2–1.21.5</td>
 *       <td>{@code drawTexture(Function<Identifier,RenderLayer>, Identifier,
 *           x, y, u, v, width, height, regionWidth, regionHeight,
 *           textureWidth, textureHeight)} — pixel UV, and the caller now
 *           supplies the render layer</td></tr>
 *   <tr><td>1.21.6–1.21.11</td>
 *       <td>{@code drawTexturedQuad(Identifier, x1, y1, x2, y2, u1, u2, v1, v2)}
 *           — normalised float UV</td></tr>
 * </table>
 *
 * <p>Note that the first two forms are <em>not</em> merely "the same call with
 * an extra argument": 1.21 orders the destination size before the UV, 1.21.2
 * after it. Passing one order to the other compiles fine on the arities that
 * happen to line up, which is exactly the kind of silent, off-screen rectangle
 * that cost P4 a hand test round (see {@code PlayerListHudMixin}'s note on the
 * corner-swap bug). Hence: one method, three bodies, each written against its
 * own measured parameter list.
 *
 * <p><b>Why the seam lives here and not in the bucket.</b> Measured on
 * 2026-08-10 by compiling bucket BC's ~1500 lines of render source against
 * 1.21.2, 1.21.4 and 1.21.5: the <em>only</em> compiler error on any of them
 * was this one call. Forking the whole bucket over a single line would have
 * contradicted DESIGN.md §2 ("hedef 4 değil, mümkün olan en az sayıda
 * adapter"), so the buckets merged and the line moved out here — the same
 * trade already made for {@link CueRenderLayers} inside bucket D.
 *
 * <p>Bucket D calls this too, even though its own form needs no shim, so that
 * every compat generation carries the same three classes and Layer 2's draw
 * has exactly one home (see this package's {@code package-info}).
 */
public final class CueGuiIcons {

    private CueGuiIcons() {
    }

    /**
     * Draws atlas cell {@code cell} of {@code texture} as a {@code size}×{@code
     * size} icon with its top-left corner at ({@code x}, {@code y}) in GUI
     * space. The cell geometry comes from {@link CueIconAtlas}, which is the
     * single source of truth for the sheet's layout; only the call underneath
     * differs between compat generations.
     */
    public static void drawAtlasCell(DrawContext context, Identifier texture, int cell, int x, int y, int size) {
        context.drawTexture(
                texture,
                x, y,
                size, size,
                CueIconAtlas.column(cell) * CueIconAtlas.CELL_PIXELS,
                CueIconAtlas.row(cell) * CueIconAtlas.CELL_PIXELS,
                CueIconAtlas.CELL_PIXELS, CueIconAtlas.CELL_PIXELS,
                CueIconAtlas.TEXTURE_WIDTH, CueIconAtlas.TEXTURE_HEIGHT);
    }
}
