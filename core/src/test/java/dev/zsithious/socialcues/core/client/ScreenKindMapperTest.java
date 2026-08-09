package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §6: registry-id-string-to-{@link ScreenKind} table. The ids
 * exercised here are exactly the 25 {@code ScreenHandlerType} registrations
 * found in 1.21.11 via {@code javap -c} on the mapped jar (every
 * {@code ldc} string right before a {@code register(...)} call in
 * {@code ScreenHandlerType}'s static initializer) — not guessed.
 */
class ScreenKindMapperTest {

    @ParameterizedTest
    @CsvSource({
            // DESIGN.md §7 P5 hand-test fix (HATA7): 9x1..9x3 are now the
            // dedicated "small container" kind, not lumped in with the double
            // chest -- see ScreenKindMapper's own comment on this table.
            "minecraft:generic_9x1, CONTAINER_SMALL",
            "minecraft:generic_9x2, CONTAINER_SMALL",
            "minecraft:generic_9x3, CONTAINER_SMALL",
            "minecraft:generic_9x4, CONTAINER",
            "minecraft:generic_9x5, CONTAINER",
            "minecraft:generic_9x6, CONTAINER",
            "minecraft:generic_3x3, DISPENSER",
            "minecraft:crafter_3x3, DISPENSER",
            "minecraft:hopper, HOPPER",
            "minecraft:shulker_box, SHULKER",
            "minecraft:grindstone, CONTAINER",
            "minecraft:crafting, CRAFTING",
            "minecraft:furnace, FURNACE",
            "minecraft:blast_furnace, FURNACE",
            "minecraft:smoker, FURNACE",
            "minecraft:anvil, ANVIL",
            "minecraft:enchantment, ENCHANTING",
            "minecraft:brewing_stand, BREWING",
            "minecraft:merchant, MERCHANT",
            "minecraft:beacon, BEACON",
            "minecraft:loom, LOOM",
            "minecraft:smithing, SMITHING",
            "minecraft:stonecutter, STONECUTTER",
            "minecraft:cartography_table, CARTOGRAPHY",
            "minecraft:lectern, BOOK_READ",
    })
    void mapsEveryVanillaRegistryId(String registryId, ScreenKind expected) {
        assertEquals(expected, ScreenKindMapper.fromRegistryId(registryId));
    }

    @Test
    void unrecognizedIdMapsToModded() {
        assertEquals(ScreenKind.MODDED, ScreenKindMapper.fromRegistryId("examplemod:custom_screen"));
    }

    @Test
    void nullIdMapsToModded() {
        assertEquals(ScreenKind.MODDED, ScreenKindMapper.fromRegistryId(null));
    }

    @Test
    void unnamespacedGarbageMapsToModded() {
        assertEquals(ScreenKind.MODDED, ScreenKindMapper.fromRegistryId("not_a_real_id"));
    }

    @Test
    void tableNeverReturnsNull() {
        for (String id : new String[] {"minecraft:generic_9x3", "minecraft:anvil", "junk", null}) {
            assertNotNull(ScreenKindMapper.fromRegistryId(id));
        }
    }
}
