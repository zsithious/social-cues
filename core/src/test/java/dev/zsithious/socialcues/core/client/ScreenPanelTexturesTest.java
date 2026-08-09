package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §7 P5b — {@link ScreenPanelTextures}'s pure-Java mapping. The
 * exhaustive {@code switch} in {@link ScreenPanelTextures#forScreenKind}
 * already gives a compile-time guarantee that every {@link ScreenKind} has a
 * case; this is the second line of defense (same pattern as
 * {@code CueIconAtlasTest}), asserting the actual resulting table's shape.
 */
class ScreenPanelTexturesTest {

    /**
     * The kinds {@link ScreenPanelTextures#forScreenKind} deliberately returns
     * {@link Optional#empty()} for — "a screen is open, but naming a container
     * would be a lie" (protocol-tier bugfix, 2026-08-09; see that method's own
     * Javadoc). Listed once here because several tests need to either skip
     * them or assert exactly this set.
     */
    private static final List<ScreenKind> NEUTRAL_KINDS = List.of(
            ScreenKind.MODDED, ScreenKind.UNKNOWN, ScreenKind.PAUSE, ScreenKind.SETTINGS,
            ScreenKind.BOOK_READ, ScreenKind.MAP_VIEW, ScreenKind.ADVANCEMENTS, ScreenKind.RECIPE_BOOK);

    private static ScreenPanelTextures.Texture texture(ScreenKind kind) {
        return ScreenPanelTextures.forScreenKind(kind)
                .orElseThrow(() -> new AssertionError("expected a texture for " + kind));
    }

    @Test
    void everyTextureBearingScreenKindMapsToAWellFormedTexture() {
        for (ScreenKind kind : ScreenKind.values()) {
            Optional<ScreenPanelTextures.Texture> textureOpt = ScreenPanelTextures.forScreenKind(kind);
            if (NEUTRAL_KINDS.contains(kind)) {
                assertTrue(textureOpt.isEmpty(), "expected no texture for the neutral kind " + kind);
                continue;
            }
            ScreenPanelTextures.Texture texture = textureOpt.orElse(null);
            assertNotNull(texture, "no texture for " + kind);
            assertFalse(texture.path().isBlank(), "blank path for " + kind);
            assertTrue(texture.path().startsWith("textures/gui/container/"), "unexpected path for " + kind);
            assertTrue(texture.path().endsWith(".png"), "path should include the extension for " + kind);
            assertTrue(texture.regionWidth() > 0 && texture.regionHeight() > 0, "non-positive region for " + kind);
            assertTrue(texture.canvasWidth() >= texture.regionWidth(), "canvas narrower than region for " + kind);
            assertTrue(texture.canvasHeight() >= texture.regionHeight(), "canvas shorter than region for " + kind);
        }
    }

    @Test
    void explicitlyListedScreenKindsGetTheirOwnFilename() {
        Map<ScreenKind, String> expected = new HashMap<>();
        expected.put(ScreenKind.INVENTORY, "textures/gui/container/inventory.png");
        expected.put(ScreenKind.CONTAINER, "textures/gui/container/generic_54.png");
        expected.put(ScreenKind.CRAFTING, "textures/gui/container/crafting_table.png");
        expected.put(ScreenKind.FURNACE, "textures/gui/container/furnace.png");
        expected.put(ScreenKind.ANVIL, "textures/gui/container/anvil.png");
        expected.put(ScreenKind.ENCHANTING, "textures/gui/container/enchanting_table.png");
        expected.put(ScreenKind.BREWING, "textures/gui/container/brewing_stand.png");
        expected.put(ScreenKind.MERCHANT, "textures/gui/container/villager.png");
        expected.put(ScreenKind.BEACON, "textures/gui/container/beacon.png");
        expected.put(ScreenKind.LOOM, "textures/gui/container/loom.png");
        expected.put(ScreenKind.SMITHING, "textures/gui/container/smithing.png");
        expected.put(ScreenKind.STONECUTTER, "textures/gui/container/stonecutter.png");
        expected.put(ScreenKind.CARTOGRAPHY, "textures/gui/container/cartography_table.png");
        // DESIGN.md §7 P5 hand-test fix (HATA7). CONTAINER_SMALL deliberately
        // shares generic_54.png's path (see ScreenPanelTextures.CONTAINER_SMALL_TEX's
        // own Javadoc for why no dedicated file exists) -- a Map<ScreenKind,String>
        // has no trouble with two keys sharing one value, so it belongs here even
        // though it is intentionally excluded from the "every file is distinct"
        // check below.
        expected.put(ScreenKind.CONTAINER_SMALL, "textures/gui/container/generic_54.png");
        expected.put(ScreenKind.HOPPER, "textures/gui/container/hopper.png");
        expected.put(ScreenKind.SHULKER, "textures/gui/container/shulker_box.png");
        expected.put(ScreenKind.DISPENSER, "textures/gui/container/dispenser.png");

        expected.forEach((kind, path) ->
                assertEquals(path, texture(kind).path(), "wrong texture for " + kind));
    }

    /**
     * Protocol-tier bugfix, 2026-08-09: there is no catch-all fallback texture
     * anymore. It used to be {@code generic_54.png} (the full double chest),
     * then — DESIGN.md §7 P5 hand-test fix (HATA7) — the compact
     * {@link ScreenKind#CONTAINER_SMALL} texture; both told the same lie, just
     * at different sizes, since "we don't know what screen this is" is not the
     * fact "a chest of some size". These kinds now render no container texture
     * at all and the adapter draws a neutral flat panel instead (see
     * {@code CueScreenPanelRenderer#drawNeutralPanel}).
     */
    @Test
    void neutralScreenKindsHaveNoTextureAtAll() {
        for (ScreenKind kind : NEUTRAL_KINDS) {
            assertTrue(ScreenPanelTextures.forScreenKind(kind).isEmpty(),
                    "expected no texture for " + kind);
        }
    }

    /**
     * The complement of {@link #neutralScreenKindsHaveNoTextureAtAll}: no kind
     * outside {@link #NEUTRAL_KINDS} may quietly become textureless. Without
     * this, adding a {@code ScreenKind} and wiring it to
     * {@code Optional.empty()} in the exhaustive switch would compile, pass
     * every other test here, and silently render as a blank panel forever.
     */
    @Test
    void everyOtherScreenKindStillHasARealTexture() {
        for (ScreenKind kind : ScreenKind.values()) {
            if (NEUTRAL_KINDS.contains(kind)) {
                continue;
            }
            assertTrue(ScreenPanelTextures.forScreenKind(kind).isPresent(),
                    "expected a real texture for " + kind);
        }
    }

    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7), the user-reported bug this whole
     * fix chases: a single chest ({@code generic_9x3}) used to render as a
     * double chest. Locks in the full chain: registry id -> ScreenKind ->
     * texture, and that the texture is visibly smaller (shorter region) than
     * the real double-chest one, not merely a differently-named copy of it.
     */
    @Test
    void singleChestRendersSmallerThanADoubleChest() {
        ScreenKind singleChest = ScreenKindMapper.fromRegistryId("minecraft:generic_9x3");
        assertEquals(ScreenKind.CONTAINER_SMALL, singleChest);

        ScreenPanelTextures.Texture small = texture(ScreenKind.CONTAINER_SMALL);
        ScreenPanelTextures.Texture doubleChest = texture(ScreenKind.CONTAINER);

        assertTrue(small.regionHeight() < doubleChest.regionHeight(),
                "expected the single-chest texture's region to be shorter than the double chest's: "
                        + "small=" + small.regionHeight() + " double=" + doubleChest.regionHeight());
    }

    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7): {@link ScreenKind#CONTAINER_SMALL}
     * is deliberately NOT in this list -- it intentionally shares {@code
     * generic_54.png}'s path with {@link ScreenKind#CONTAINER} (a smaller
     * region of the same real file, since no dedicated file exists; see
     * {@code ScreenPanelTextures.CONTAINER_SMALL_TEX}'s Javadoc), so it would
     * fail this specific "every file is its own" check by design, not by bug.
     */
    @Test
    void everyExplicitlyListedKindGetsADistinctFileFromEveryOtherExplicitlyListedKind() {
        ScreenKind[] explicit = {
                ScreenKind.INVENTORY, ScreenKind.CONTAINER, ScreenKind.CRAFTING, ScreenKind.FURNACE,
                ScreenKind.ANVIL, ScreenKind.ENCHANTING, ScreenKind.BREWING, ScreenKind.MERCHANT,
                ScreenKind.BEACON, ScreenKind.LOOM, ScreenKind.SMITHING, ScreenKind.STONECUTTER,
                ScreenKind.CARTOGRAPHY, ScreenKind.HOPPER, ScreenKind.SHULKER, ScreenKind.DISPENSER
        };
        Map<String, ScreenKind> seen = new HashMap<>();
        for (ScreenKind kind : explicit) {
            String path = texture(kind).path();
            ScreenKind previous = seen.put(path, kind);
            assertTrue(previous == null, path + " assigned to both " + previous + " and " + kind);
        }
    }

    /** DESIGN.md §7 P5b uygulama notu: the one filename that breaks the "all textures are 256x256" assumption. */
    @Test
    void merchantCanvasIsWiderThanSquareBecauseTheTradeListGuiIsWide() {
        ScreenPanelTextures.Texture villager = texture(ScreenKind.MERCHANT);
        assertEquals(512, villager.canvasWidth());
        assertEquals(256, villager.canvasHeight());
        assertEquals(276, villager.regionWidth());
        assertEquals(166, villager.regionHeight());
    }

    @Test
    void nullScreenKindRejected() {
        assertThrows(NullPointerException.class, () -> ScreenPanelTextures.forScreenKind(null));
    }

    // ----------------------------------------------------- bands (DESIGN.md §7 P5 hand-test follow-up)

    /**
     * Second line of defense, same spirit as {@link #everyScreenKindMapsToAWellFormedTexture}:
     * {@link ScreenPanelTextures.Texture}'s compact constructor already
     * rejects an out-of-canvas band at construction time (see {@link
     * #textureRejectsABandThatExceedsItsCanvas}), so every band reachable
     * through {@link ScreenPanelTextures#forScreenKind} is guaranteed to fit
     * already -- this re-checks the same property from the outside, the way
     * this file already re-checks region/canvas sizing for every kind.
     */
    @Test
    void everyTextureBandFitsInsideItsOwnCanvas() {
        for (ScreenKind kind : ScreenKind.values()) {
            if (NEUTRAL_KINDS.contains(kind)) {
                continue;
            }
            ScreenPanelTextures.Texture texture = texture(kind);
            for (ScreenPanelTextures.Band band : texture.bands()) {
                assertTrue(band.u() + band.width() <= texture.canvasWidth(),
                        "band " + band + " overruns canvas width for " + kind);
                assertTrue(band.v() + band.height() <= texture.canvasHeight(),
                        "band " + band + " overruns canvas height for " + kind);
            }
        }
    }

    @Test
    void regionWidthIsTheWidestBandAndRegionHeightIsTheSumOfBandHeights() {
        for (ScreenKind kind : ScreenKind.values()) {
            if (NEUTRAL_KINDS.contains(kind)) {
                continue;
            }
            ScreenPanelTextures.Texture texture = texture(kind);
            int expectedWidth = 0;
            int expectedHeight = 0;
            for (ScreenPanelTextures.Band band : texture.bands()) {
                expectedWidth = Math.max(expectedWidth, band.width());
                expectedHeight += band.height();
            }
            assertEquals(expectedWidth, texture.regionWidth(), "regionWidth mismatch for " + kind);
            assertEquals(expectedHeight, texture.regionHeight(), "regionHeight mismatch for " + kind);
        }
    }

    /**
     * DESIGN.md §7 P5 hand-test follow-up: locks in the exact two-band
     * composite {@link ScreenKind#CONTAINER_SMALL} is documented to
     * reproduce -- the 3-row slot grid directly on top of the player-
     * inventory footer, both real regions of {@code generic_54.png} (see
     * {@code ScreenPanelTextures.CONTAINER_SMALL_TEX}'s own Javadoc for the
     * {@code rows*18+17}/{@code v=126,h=96} derivation) -- and that the
     * total height is exactly 167, not the single-band 71 the first attempt
     * at this fix shipped.
     */
    @Test
    void containerSmallIsTheDocumentedTwoBandComposite() {
        ScreenPanelTextures.Texture texture = texture(ScreenKind.CONTAINER_SMALL);

        assertEquals(
                List.of(new ScreenPanelTextures.Band(0, 0, 176, 71), new ScreenPanelTextures.Band(0, 126, 176, 96)),
                texture.bands());
        assertEquals(176, texture.regionWidth());
        assertEquals(167, texture.regionHeight());
    }

    /**
     * DESIGN.md §7 P5 hand-test follow-up: every OTHER texture in the table
     * is still the single-band common case -- the two-band composite is one
     * deliberate exception, not the start of a pattern. Since the 2026-08-09
     * protocol-tier bugfix removed the catch-all fallback, {@link
     * ScreenKind#CONTAINER_SMALL} is the only kind that resolves to it at all
     * (before, every unmapped kind did too), so the comparison is still made
     * against the resolved {@code Texture} rather than by {@code ScreenKind}
     * identity -- cheap insurance if another kind is ever pointed at it.
     */
    @Test
    void everyTextureOtherThanTheTwoBandCompositeHasExactlyOneBand() {
        ScreenPanelTextures.Texture twoBandComposite = texture(ScreenKind.CONTAINER_SMALL);
        for (ScreenKind kind : ScreenKind.values()) {
            if (NEUTRAL_KINDS.contains(kind)) {
                continue;
            }
            ScreenPanelTextures.Texture texture = texture(kind);
            if (texture.equals(twoBandComposite)) {
                continue;
            }
            assertEquals(1, texture.bands().size(), "expected exactly one band for " + kind);
        }
    }

    @Test
    void textureRejectsABandThatExceedsItsCanvas() {
        ScreenPanelTextures.Band tooWide = new ScreenPanelTextures.Band(100, 0, 200, 50);
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenPanelTextures.Texture("textures/gui/container/x.png", 256, 256, List.of(tooWide)));

        ScreenPanelTextures.Band tooTall = new ScreenPanelTextures.Band(0, 100, 50, 200);
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenPanelTextures.Texture("textures/gui/container/x.png", 256, 256, List.of(tooTall)));
    }

    @Test
    void textureRejectsAnEmptyBandList() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenPanelTextures.Texture("textures/gui/container/x.png", 256, 256, List.of()));
    }

    @Test
    void bandRejectsNonPositiveWidthOrHeight() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenPanelTextures.Band(0, 0, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new ScreenPanelTextures.Band(0, 0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> new ScreenPanelTextures.Band(0, 0, -1, 10));
    }

    @Test
    void bandRejectsNegativeOffsets() {
        assertThrows(IllegalArgumentException.class, () -> new ScreenPanelTextures.Band(-1, 0, 10, 10));
        assertThrows(IllegalArgumentException.class, () -> new ScreenPanelTextures.Band(0, -1, 10, 10));
    }

    /** A zero offset is completely ordinary (every single-band texture's one band starts at (0,0)) -- must NOT be rejected. */
    @Test
    void bandAcceptsZeroOffset() {
        ScreenPanelTextures.Band band = new ScreenPanelTextures.Band(0, 0, 10, 10);
        assertEquals(0, band.u());
        assertEquals(0, band.v());
    }
}
