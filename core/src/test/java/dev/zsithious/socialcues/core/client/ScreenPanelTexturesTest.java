package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void everyScreenKindMapsToAWellFormedTexture() {
        for (ScreenKind kind : ScreenKind.values()) {
            ScreenPanelTextures.Texture texture = ScreenPanelTextures.forScreenKind(kind);
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
                assertEquals(path, ScreenPanelTextures.forScreenKind(kind).path(), "wrong texture for " + kind));
    }

    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7): the fallback used to be
     * generic_54.png (the full double chest); it is now the same compact
     * texture {@link ScreenKind#CONTAINER_SMALL} uses, which the task brief
     * called {@code generic_27.png} -- see {@code ScreenPanelTextures
     * .CONTAINER_SMALL_TEX}'s Javadoc for why this project could not literally
     * serve that filename (it does not exist in vanilla's own assets).
     */
    @Test
    void everythingElseFallsBackToTheCompactContainerTexture() {
        ScreenKind[] fallbackKinds = {
                ScreenKind.MODDED, ScreenKind.UNKNOWN, ScreenKind.PAUSE, ScreenKind.SETTINGS,
                ScreenKind.BOOK_READ, ScreenKind.MAP_VIEW, ScreenKind.ADVANCEMENTS, ScreenKind.RECIPE_BOOK
        };
        ScreenPanelTextures.Texture expected = ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER_SMALL);
        for (ScreenKind kind : fallbackKinds) {
            assertEquals(expected, ScreenPanelTextures.forScreenKind(kind),
                    "expected the compact-container fallback for " + kind);
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

        ScreenPanelTextures.Texture small = ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER_SMALL);
        ScreenPanelTextures.Texture doubleChest = ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER);

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
            String path = ScreenPanelTextures.forScreenKind(kind).path();
            ScreenKind previous = seen.put(path, kind);
            assertTrue(previous == null, path + " assigned to both " + previous + " and " + kind);
        }
    }

    /** DESIGN.md §7 P5b uygulama notu: the one filename that breaks the "all textures are 256x256" assumption. */
    @Test
    void merchantCanvasIsWiderThanSquareBecauseTheTradeListGuiIsWide() {
        ScreenPanelTextures.Texture villager = ScreenPanelTextures.forScreenKind(ScreenKind.MERCHANT);
        assertEquals(512, villager.canvasWidth());
        assertEquals(256, villager.canvasHeight());
        assertEquals(276, villager.regionWidth());
        assertEquals(166, villager.regionHeight());
    }

    /**
     * DESIGN.md §7 P5 hand-test fix (HATA7): this used to be true (both
     * shared generic_54.png, the full double chest) and was exactly the bug --
     * an unrecognised screen showed the single biggest texture in the table.
     * Inverted to lock the fix in: the fallback is now the compact texture,
     * strictly smaller than the double chest's.
     */
    @Test
    void fallbackNoLongerSharesContainersFullDoubleChestTexture() {
        ScreenPanelTextures.Texture container = ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER);
        ScreenPanelTextures.Texture fallback = ScreenPanelTextures.forScreenKind(ScreenKind.UNKNOWN);

        assertTrue(fallback.regionHeight() < container.regionHeight(),
                "expected the fallback to be visibly smaller than the double chest: "
                        + "fallback=" + fallback.regionHeight() + " container=" + container.regionHeight());
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
            ScreenPanelTextures.Texture texture = ScreenPanelTextures.forScreenKind(kind);
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
            ScreenPanelTextures.Texture texture = ScreenPanelTextures.forScreenKind(kind);
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
        ScreenPanelTextures.Texture texture = ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER_SMALL);

        assertEquals(
                List.of(new ScreenPanelTextures.Band(0, 0, 176, 71), new ScreenPanelTextures.Band(0, 126, 176, 96)),
                texture.bands());
        assertEquals(176, texture.regionWidth());
        assertEquals(167, texture.regionHeight());
    }

    /**
     * DESIGN.md §7 P5 hand-test follow-up: every OTHER texture in the table
     * is still the single-band common case -- the two-band composite is one
     * deliberate exception (reached both directly via {@link
     * ScreenKind#CONTAINER_SMALL} and via every kind that falls back to it,
     * see {@link #everythingElseFallsBackToTheCompactContainerTexture}), not
     * the start of a pattern. Compared by the resolved {@code Texture} itself
     * (not by {@code ScreenKind} identity), since several kinds resolve to
     * that exact same two-band instance through the fallback.
     */
    @Test
    void everyTextureOtherThanTheTwoBandCompositeHasExactlyOneBand() {
        ScreenPanelTextures.Texture twoBandComposite = ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER_SMALL);
        for (ScreenKind kind : ScreenKind.values()) {
            ScreenPanelTextures.Texture texture = ScreenPanelTextures.forScreenKind(kind);
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
