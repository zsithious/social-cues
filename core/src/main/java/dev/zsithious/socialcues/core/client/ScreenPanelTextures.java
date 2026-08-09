package dev.zsithious.socialcues.core.client;

import java.util.List;
import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §7 P5b — which vanilla GUI texture the held panel shows for
 * {@link Activity#IN_SCREEN}, keyed by {@link ScreenKind}. Deliberately pure
 * Java (a plain resource-path string, not an {@code Identifier}) so this
 * table stays {@code core}-testable and MC-free, exactly like
 * {@link CueIconAtlas} — the adapter turns the returned {@link Texture#path()}
 * into a real {@code net.minecraft.util.Identifier} with the {@code
 * minecraft} namespace.
 *
 * <p><b>Where the numbers per entry came from:</b> not {@code javap} — these
 * are image assets, not bytecode. Every file was extracted from the 1.21.11
 * client jar (verified present under {@code
 * assets/minecraft/textures/gui/container/}) and inspected with Pillow: the
 * <em>canvas</em> size is the PNG's own pixel dimensions, and a texture's
 * artwork is measured as the bounding box of the opaque (non-transparent)
 * pixels anchored at the top-left corner — i.e. exactly the "GUI itself in
 * the top-left region" the P5b task brief describes, measured rather than
 * assumed. All thirteen files the P5b brief names exist; none had to be
 * dropped. One assumption in that brief did not hold and is corrected here:
 * {@link ScreenKind#MERCHANT}'s {@code villager.png} is <b>512×256</b>, not
 * 256×256 — the trade-list GUI is 276px wide, wider than a 256px canvas
 * allows, so Mojang shipped a wider sheet for this one texture. Every other
 * file is 256×256 exactly as assumed.
 *
 * <p>Layout mirrors {@link CueIconAtlas}: an exhaustive {@code switch} over
 * every {@link ScreenKind}, so a future constant added there without a case
 * here fails to <em>compile</em>, not silently falls back to
 * {@link ScreenKind#UNKNOWN}'s behaviour at render time.
 *
 * <p><b>DESIGN.md §7 P5 hand-test fix (HATA7):</b> a live hand test found
 * every 9-wide container GUI (single chest through double chest), the hopper,
 * the shulker box, and the dispenser/dropper grid all collapsing into one
 * {@link ScreenKind#CONTAINER} — and, separately, every kind with no dedicated
 * texture at all falling back to that same {@code generic_54.png}, the full
 * double chest. Four new {@link ScreenKind} constants ({@link
 * ScreenKind#CONTAINER_SMALL}, {@link ScreenKind#HOPPER}, {@link
 * ScreenKind#SHULKER}, {@link ScreenKind#DISPENSER}) and three genuinely new
 * textures ({@code hopper.png}, {@code shulker_box.png}, {@code
 * dispenser.png} — all three measured the same Pillow way as the original
 * thirteen) fix the collapsing; {@link #CONTAINER_SMALL_TEX}'s own Javadoc
 * covers the fourth (a compact container texture that also replaces {@code
 * generic_54.png} as the catch-all fallback).
 *
 * <p><b>DESIGN.md §7 P5 hand-test follow-up (still HATA7): {@link Texture}
 * became multi-band.</b> The first cut of {@link #CONTAINER_SMALL_TEX} was a
 * single 176×71 crop (just the 3-row slot grid) — technically real pixels,
 * but a 2.48:1 sliver next to the double chest's 0.79:1, which read as a
 * broken render rather than "a smaller container". Vanilla itself never
 * draws a single chest that way either (see that constant's own Javadoc): it
 * composites two separate regions of {@code generic_54.png} on screen, one
 * right under the other. {@link Texture} now carries a {@link List} of
 * {@link Band}s instead of one implicit top-left region, so a texture can
 * faithfully reproduce that same two-piece composite instead of settling for
 * a single truncated crop.
 */
public final class ScreenPanelTextures {

    private static final String BASE_PATH = "textures/gui/container/";

    /**
     * One rectangular piece of a {@link Texture}'s canvas: {@code (u, v)} is
     * its top-left corner in texture pixels, {@code width}/{@code height} its
     * size, also in texture pixels. Multiple bands on one {@link Texture} are
     * stacked vertically, top to bottom, on the held panel — see {@link
     * dev.zsithious.socialcues.adapter.bucketd.render.CueScreenPanelRenderer
     * #drawContainerPanel} for exactly how.
     *
     * <p>Validated here only for what does not need the canvas size
     * ({@code width}/{@code height} must be positive — a zero- or
     * negative-area band is never meaningful; {@code u}/{@code v} must be
     * non-negative, but zero is a completely ordinary top-left offset, not an
     * error). Whether a band actually fits inside its texture's canvas is
     * checked by {@link Texture}'s own compact constructor, the one place
     * that actually has the canvas size to check it against.
     */
    public record Band(int u, int v, int width, int height) {

        public Band {
            if (u < 0 || v < 0) {
                throw new IllegalArgumentException("band u/v must be non-negative");
            }
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException("band width/height must be positive");
            }
        }
    }

    /**
     * One vanilla GUI texture, composed of one or more {@link Band}s stacked
     * vertically on the held panel. {@code path} is namespace-less (matching
     * {@link CueIconAtlas#TEXTURE_PATH}'s own convention) but does include
     * the {@code .png} extension, since these are direct textures, not atlas
     * sprites. {@code canvasWidth}/{@code canvasHeight} are the full PNG's
     * pixel size, needed to turn a band into UV fractions (0..1) since a
     * band's own size and its canvas's are not always equal proportions of
     * each other — see {@link ScreenPanelTextures}'s class Javadoc for the
     * one texture where canvas and artwork aspect ratios genuinely differ
     * (villager.png).
     *
     * <p>{@link #regionWidth()}/{@link #regionHeight()} are derived, not
     * stored: the overall artwork size a renderer sizes the panel quad by is
     * the widest band's width, and the <em>sum</em> of every band's height
     * (bands stack, they do not overlap) — for the common single-band case
     * this is exactly that one band's own size, so every existing call site
     * that only ever dealt with one region keeps working unchanged.
     */
    public record Texture(String path, int canvasWidth, int canvasHeight, List<Band> bands) {

        public Texture {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(bands, "bands");
            if (canvasWidth <= 0 || canvasHeight <= 0) {
                throw new IllegalArgumentException("canvas dimensions must be positive");
            }
            // Defensive copy (also rejects a null element outright) before this
            // becomes an immutable record's backing field.
            bands = List.copyOf(bands);
            if (bands.isEmpty()) {
                throw new IllegalArgumentException("a texture needs at least one band");
            }
            for (Band band : bands) {
                if (band.u() + band.width() > canvasWidth || band.v() + band.height() > canvasHeight) {
                    throw new IllegalArgumentException(
                            "band " + band + " exceeds its own " + canvasWidth + "x" + canvasHeight + " canvas");
                }
            }
        }

        /** The widest band — what a renderer sizes the panel quad's width by. Plain loop, not a stream: called on the render path. */
        public int regionWidth() {
            int max = 0;
            for (Band band : bands) {
                max = Math.max(max, band.width());
            }
            return max;
        }

        /** Every band's height, summed (bands stack vertically, they do not overlap) — what a renderer sizes the panel quad's height by. */
        public int regionHeight() {
            int sum = 0;
            for (Band band : bands) {
                sum += band.height();
            }
            return sum;
        }
    }

    /** The common case: a texture whose entire artwork is one top-left-anchored band. */
    private static Texture single(String path, int regionWidth, int regionHeight, int canvasWidth, int canvasHeight) {
        return new Texture(path, canvasWidth, canvasHeight, List.of(new Band(0, 0, regionWidth, regionHeight)));
    }

    private static Texture squareCanvas(String file, int regionWidth, int regionHeight) {
        return single(BASE_PATH + file, regionWidth, regionHeight, 256, 256);
    }

    private static final Texture INVENTORY_TEX = squareCanvas("inventory.png", 176, 166);
    /** Also {@link ScreenKind#CONTAINER}'s texture — no longer the "everything else" fallback's, see {@link #forScreenKind}. */
    private static final Texture GENERIC_54_TEX = squareCanvas("generic_54.png", 176, 222);

    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7): a compact, single-chest-sized
     * texture, both for {@link ScreenKind#CONTAINER_SMALL} and as the new
     * fallback (see {@link #forScreenKind}) — replacing {@link #GENERIC_54_TEX}
     * (the full double chest) in both roles, so a small/unrecognised container
     * no longer reads as a giant double chest.
     *
     * <p><b>Deviation from the task brief, reported per its own "measure,
     * don't guess" instruction:</b> the brief asked for a dedicated {@code
     * generic_27.png} file. No such file exists — checked directly against
     * the real 1.21.11 client jar's {@code assets/minecraft/textures/gui/
     * container/} (only {@code generic_54.png} is there) and confirmed by
     * {@code javap -c} on {@code net.minecraft.client.gui.screen.ingame
     * .GenericContainerScreen}: vanilla itself only ever has one container-grid
     * asset, and composes every row count — including a real single chest —
     * from it with <b>two separate blits</b>: the slot grid at {@code
     * (u=0,v=0)} sized {@code rows*18+17} tall (71px for 3 rows, 125px for 6),
     * then the player-inventory footer at a fixed {@code (u=0,v=126)}, 96px
     * tall, placed immediately underneath on screen.
     *
     * <p><b>First attempt (superseded) and why it was wrong:</b> this used to
     * be a single 176×71 {@link Band} — just the slot-grid blit, footer
     * dropped, on the reasoning that the old single-region {@code Texture}
     * shape had no way to place a second, non-contiguous piece of the source
     * PNG. That texture's aspect ratio was 176:71 ≈ 2.48:1 — next to the
     * double chest's 176:222 ≈ 0.79:1, a 0.58-block-wide panel came out
     * roughly 0.23 blocks tall, a thin letterbox strip that read as a broken
     * render rather than a smaller container (caught in review, not by a
     * hand test). {@link Texture} becoming multi-band (see the class
     * Javadoc) removed the actual constraint that forced that compromise:
     * this is now the real two-band composite vanilla itself draws --
     * {@code Band(0, 0, 176, 71)} (the slot grid) stacked directly on top of
     * {@code Band(0, 126, 176, 96)} (the footer, its own source {@code v}
     * unchanged from where vanilla samples it; only its <em>screen</em>
     * position moves, exactly as {@code GenericContainerScreen
     * .drawBackground} places it), giving {@link Texture#regionHeight()} =
     * 71 + 96 = 167 and an aspect ratio of 176:167 ≈ 1.05:1 — close to
     * square, essentially identical to {@code inventory.png}'s own 176:166,
     * and clearly, legibly smaller than the double chest's 176:222. This is
     * not a crop or an invention: every pixel comes from a real, measured
     * region of the one texture vanilla ships, arranged exactly the way
     * vanilla's own renderer already arranges them for a real single chest.
     */
    private static final Texture CONTAINER_SMALL_TEX = new Texture(BASE_PATH + "generic_54.png", 256, 256, List.of(
            new Band(0, 0, 176, 71),
            new Band(0, 126, 176, 96)));

    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7): a hopper's own GUI, canvas
     * 256×256, region measured with Pillow (opaque-pixel bounding box,
     * top-left anchored, same method as every other entry here) off the real
     * 1.21.11 client jar asset.
     */
    private static final Texture HOPPER_TEX = squareCanvas("hopper.png", 176, 133);
    /** DESIGN.md §7 P5 hand-test fix (HATA7): shulker box GUI, measured the same way as {@link #HOPPER_TEX}. */
    private static final Texture SHULKER_TEX = squareCanvas("shulker_box.png", 176, 166);
    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7): the dispenser/dropper 3x3-grid
     * GUI — confirmed via {@code javap -c} on {@code
     * net.minecraft.client.gui.screen.ingame.Generic3x3ContainerScreen} that
     * both {@code generic_3x3} (dispenser/dropper) and, per this fix's own
     * mapping choice, {@code crafter_3x3} share this texture; the Crafter
     * block actually ships its own dedicated {@code crafter.png} in vanilla
     * (found while verifying this one, not requested by the brief and not
     * wired in — a candidate for a future dedicated {@code ScreenKind} if this
     * project ever wants the Crafter to look distinct from a plain dispenser).
     */
    private static final Texture DISPENSER_TEX = squareCanvas("dispenser.png", 176, 166);

    private static final Texture CRAFTING_TABLE_TEX = squareCanvas("crafting_table.png", 176, 166);
    private static final Texture FURNACE_TEX = squareCanvas("furnace.png", 176, 166);
    private static final Texture ANVIL_TEX = squareCanvas("anvil.png", 176, 166);
    private static final Texture ENCHANTING_TABLE_TEX = squareCanvas("enchanting_table.png", 176, 166);
    private static final Texture BREWING_STAND_TEX = squareCanvas("brewing_stand.png", 176, 166);
    /** DESIGN.md §7 P5b uygulama notu: the one texture whose canvas is 512×256, not 256×256 — see class Javadoc. */
    private static final Texture MERCHANT_TEX = single(BASE_PATH + "villager.png", 276, 166, 512, 256);
    private static final Texture BEACON_TEX = squareCanvas("beacon.png", 230, 219);
    private static final Texture LOOM_TEX = squareCanvas("loom.png", 176, 166);
    private static final Texture SMITHING_TEX = squareCanvas("smithing.png", 176, 166);
    private static final Texture STONECUTTER_TEX = squareCanvas("stonecutter.png", 176, 166);
    private static final Texture CARTOGRAPHY_TABLE_TEX = squareCanvas("cartography_table.png", 176, 166);

    private ScreenPanelTextures() {
    }

    /**
     * The texture drawn on the held panel for {@code kind}. Every
     * {@link ScreenKind} the P5b task brief lists by name, plus the four
     * DESIGN.md §7 P5 hand-test fix added ({@link ScreenKind#CONTAINER_SMALL},
     * {@link ScreenKind#HOPPER}, {@link ScreenKind#SHULKER}, {@link
     * ScreenKind#DISPENSER}), gets its own texture entry; everything else
     * ({@link ScreenKind#MODDED}, {@link ScreenKind#UNKNOWN}, and the screen
     * kinds with no dedicated container GUI at all — {@code PAUSE}/
     * {@code SETTINGS}/{@code BOOK_READ}/{@code MAP_VIEW}/
     * {@code ADVANCEMENTS}/{@code RECIPE_BOOK}) falls back to {@link
     * #CONTAINER_SMALL_TEX} — DESIGN.md §7 P5 hand-test fix (HATA7): the
     * fallback used to be {@link #GENERIC_54_TEX}, the full double chest,
     * which meant every unrecognised/undetailed screen showed the single
     * biggest texture in this table. See {@link #CONTAINER_SMALL_TEX}'s own
     * Javadoc for why it is not literally {@code generic_27.png}, and for why
     * it is two {@link Band}s rather than one.
     */
    public static Texture forScreenKind(ScreenKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case INVENTORY -> INVENTORY_TEX;
            case CONTAINER -> GENERIC_54_TEX;
            case CONTAINER_SMALL -> CONTAINER_SMALL_TEX;
            case HOPPER -> HOPPER_TEX;
            case SHULKER -> SHULKER_TEX;
            case DISPENSER -> DISPENSER_TEX;
            case CRAFTING -> CRAFTING_TABLE_TEX;
            case FURNACE -> FURNACE_TEX;
            case ANVIL -> ANVIL_TEX;
            case ENCHANTING -> ENCHANTING_TABLE_TEX;
            case BREWING -> BREWING_STAND_TEX;
            case MERCHANT -> MERCHANT_TEX;
            case BEACON -> BEACON_TEX;
            case LOOM -> LOOM_TEX;
            case SMITHING -> SMITHING_TEX;
            case STONECUTTER -> STONECUTTER_TEX;
            case CARTOGRAPHY -> CARTOGRAPHY_TABLE_TEX;
            case MODDED, UNKNOWN, PAUSE, SETTINGS, BOOK_READ, MAP_VIEW, ADVANCEMENTS, RECIPE_BOOK -> CONTAINER_SMALL_TEX;
        };
    }
}
