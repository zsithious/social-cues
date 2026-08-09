package dev.zsithious.socialcues.core.client;

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
 * <p><b>Where the five numbers per entry came from:</b> not {@code javap} —
 * these are image assets, not bytecode. Every file was extracted from the
 * 1.21.11 client jar (verified present under {@code
 * assets/minecraft/textures/gui/container/}) and inspected with Pillow: the
 * <em>canvas</em> size is the PNG's own pixel dimensions, and the
 * <em>region</em> size is the bounding box of the opaque (non-transparent)
 * pixels anchored at the top-left corner — i.e. exactly the "GUI itself in
 * the top-left region" the P5b task brief describes, measured rather than
 * assumed. All thirteen files the brief names exist; none had to be dropped.
 * One assumption in the brief did not hold and is corrected here: {@link
 * ScreenKind#MERCHANT}'s {@code villager.png} is <b>512×256</b>, not 256×256
 * — the trade-list GUI is 276px wide, wider than a 256px canvas allows, so
 * Mojang shipped a wider sheet for this one texture. Every other file is
 * 256×256 exactly as assumed.
 *
 * <p>Layout mirrors {@link CueIconAtlas}: an exhaustive {@code switch} over
 * every {@link ScreenKind}, so a future constant added there without a case
 * here fails to <em>compile</em>, not silently falls back to
 * {@link ScreenKind#UNKNOWN}'s behaviour at render time.
 */
public final class ScreenPanelTextures {

    private static final String BASE_PATH = "textures/gui/container/";

    /**
     * One vanilla GUI texture. {@code path} is namespace-less (matching
     * {@link CueIconAtlas#TEXTURE_PATH}'s own convention) but does include
     * the {@code .png} extension, since these are direct textures, not
     * atlas sprites. {@code regionWidth}/{@code regionHeight} are the actual
     * GUI artwork's pixel size (top-left-anchored); {@code canvasWidth}/
     * {@code canvasHeight} are the full PNG's pixel size, needed to turn a
     * region into UV fractions (0..1) since the two are not always equal
     * proportions of each other — see {@link ScreenPanelTextures}'s class
     * Javadoc for the one texture where canvas and region aspect ratios
     * genuinely differ (villager.png).
     */
    public record Texture(String path, int regionWidth, int regionHeight, int canvasWidth, int canvasHeight) {

        public Texture {
            Objects.requireNonNull(path, "path");
            if (regionWidth <= 0 || regionHeight <= 0 || canvasWidth <= 0 || canvasHeight <= 0) {
                throw new IllegalArgumentException("all texture dimensions must be positive");
            }
            if (regionWidth > canvasWidth || regionHeight > canvasHeight) {
                throw new IllegalArgumentException("region cannot exceed its own canvas");
            }
        }
    }

    private static Texture squareCanvas(String file, int regionWidth, int regionHeight) {
        return new Texture(BASE_PATH + file, regionWidth, regionHeight, 256, 256);
    }

    private static final Texture INVENTORY_TEX = squareCanvas("inventory.png", 176, 166);
    /** Also {@link ScreenKind#CONTAINER}'s texture and every "everything else" fallback's — see {@link #forScreenKind}. */
    private static final Texture GENERIC_54_TEX = squareCanvas("generic_54.png", 176, 222);
    private static final Texture CRAFTING_TABLE_TEX = squareCanvas("crafting_table.png", 176, 166);
    private static final Texture FURNACE_TEX = squareCanvas("furnace.png", 176, 166);
    private static final Texture ANVIL_TEX = squareCanvas("anvil.png", 176, 166);
    private static final Texture ENCHANTING_TABLE_TEX = squareCanvas("enchanting_table.png", 176, 166);
    private static final Texture BREWING_STAND_TEX = squareCanvas("brewing_stand.png", 176, 166);
    /** DESIGN.md §7 P5b uygulama notu: the one texture whose canvas is 512×256, not 256×256 — see class Javadoc. */
    private static final Texture MERCHANT_TEX = new Texture(BASE_PATH + "villager.png", 276, 166, 512, 256);
    private static final Texture BEACON_TEX = squareCanvas("beacon.png", 230, 219);
    private static final Texture LOOM_TEX = squareCanvas("loom.png", 176, 166);
    private static final Texture SMITHING_TEX = squareCanvas("smithing.png", 176, 166);
    private static final Texture STONECUTTER_TEX = squareCanvas("stonecutter.png", 176, 166);
    private static final Texture CARTOGRAPHY_TABLE_TEX = squareCanvas("cartography_table.png", 176, 166);

    private ScreenPanelTextures() {
    }

    /**
     * The texture drawn on the held panel for {@code kind}. Every
     * {@link ScreenKind} the P5b task brief lists by name gets its own file;
     * everything else ({@link ScreenKind#MODDED}, {@link ScreenKind#UNKNOWN},
     * and the screen kinds with no dedicated container GUI at all —
     * {@code PAUSE}/{@code SETTINGS}/{@code BOOK_READ}/{@code MAP_VIEW}/
     * {@code ADVANCEMENTS}/{@code RECIPE_BOOK}) falls back to the same
     * {@code generic_54.png} {@link ScreenKind#CONTAINER} already uses, per
     * the brief's explicit instruction.
     */
    public static Texture forScreenKind(ScreenKind kind) {
        Objects.requireNonNull(kind, "kind");
        return switch (kind) {
            case INVENTORY -> INVENTORY_TEX;
            case CONTAINER -> GENERIC_54_TEX;
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
            case MODDED, UNKNOWN, PAUSE, SETTINGS, BOOK_READ, MAP_VIEW, ADVANCEMENTS, RECIPE_BOOK -> GENERIC_54_TEX;
        };
    }
}
