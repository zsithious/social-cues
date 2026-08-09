package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
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

        expected.forEach((kind, path) ->
                assertEquals(path, ScreenPanelTextures.forScreenKind(kind).path(), "wrong texture for " + kind));
    }

    @Test
    void everythingElseFallsBackToGeneric54() {
        ScreenKind[] fallbackKinds = {
                ScreenKind.MODDED, ScreenKind.UNKNOWN, ScreenKind.PAUSE, ScreenKind.SETTINGS,
                ScreenKind.BOOK_READ, ScreenKind.MAP_VIEW, ScreenKind.ADVANCEMENTS, ScreenKind.RECIPE_BOOK
        };
        for (ScreenKind kind : fallbackKinds) {
            assertEquals("textures/gui/container/generic_54.png", ScreenPanelTextures.forScreenKind(kind).path(),
                    "expected the generic_54 fallback for " + kind);
        }
    }

    @Test
    void everyExplicitlyListedKindGetsADistinctFileFromEveryOtherExplicitlyListedKind() {
        ScreenKind[] explicit = {
                ScreenKind.INVENTORY, ScreenKind.CONTAINER, ScreenKind.CRAFTING, ScreenKind.FURNACE,
                ScreenKind.ANVIL, ScreenKind.ENCHANTING, ScreenKind.BREWING, ScreenKind.MERCHANT,
                ScreenKind.BEACON, ScreenKind.LOOM, ScreenKind.SMITHING, ScreenKind.STONECUTTER,
                ScreenKind.CARTOGRAPHY
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

    @Test
    void containerAndFallbackShareTheSameGeneric54Texture() {
        assertEquals(ScreenPanelTextures.forScreenKind(ScreenKind.CONTAINER).path(),
                ScreenPanelTextures.forScreenKind(ScreenKind.UNKNOWN).path());
    }

    @Test
    void nullScreenKindRejected() {
        assertThrows(NullPointerException.class, () -> ScreenPanelTextures.forScreenKind(null));
    }
}
