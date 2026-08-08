package dev.zsithious.socialcues.core.client;

import java.util.Map;

import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §6 — pure lookup table from a Minecraft {@code ScreenHandlerType}
 * registry id string (e.g. {@code "minecraft:generic_9x3"}) to
 * {@link ScreenKind}. Deliberately keyed by the registry id string, not by
 * Java class name: DESIGN.md §6 explicitly rules out class-name matching
 * ("Sınıf adı string'i ile eşleme YAPILMAZ (kırılgan)") because obfuscation
 * and Yarn remapping make class names fragile across Minecraft versions,
 * while a vanilla registry id is a version-independent string literal baked
 * into {@code ScreenHandlerType}'s own static registration calls — verified
 * against the mapped 1.21.11 jar with {@code javap -c} (the ids match the
 * field names lower-cased, e.g. {@code GENERIC_9X3} registers
 * {@code "generic_9x3"}, auto-namespaced to {@code "minecraft:generic_9x3"}
 * by {@code Identifier.of(String)}).
 *
 * <p>The Minecraft-side glue's only job is: get the open screen's
 * {@code ScreenHandler}, look up its type in {@code Registries.SCREEN_HANDLER}
 * to get an {@code Identifier}, call {@code toString()} on it, and pass that
 * string to {@link #fromRegistryId}. One vanilla wrinkle is deliberately
 * <em>not</em> handled here: the player's own survival inventory screen
 * ({@code PlayerScreenHandler}) is constructed with a {@code null}
 * {@code ScreenHandlerType} (verified via {@code javap -c} on its
 * constructor — it passes {@code null} to the {@code ScreenHandler} super
 * constructor because it is the one handler that was never registered, being
 * always implicitly known rather than looked up). There is no registry id at
 * all in that case, so the Minecraft-side glue must special-case
 * {@code getType() == null} as {@link ScreenKind#INVENTORY} itself, before
 * ever calling this class.
 *
 * <p>Two enum values are intentionally unreachable through this table:
 * {@link ScreenKind#RECIPE_BOOK} and {@link ScreenKind#MAP_VIEW} do not
 * correspond to any {@code ScreenHandlerType} — the recipe book is a toggle
 * widget layered on top of an existing {@code HandledScreen} (e.g.
 * {@code InventoryScreen} extends the shared {@code RecipeBookScreen} base
 * that provides it), not a screen of its own, and the map view is rendered
 * as a held item, not a {@code Screen} at all. Detecting either requires
 * inspecting widget/render state beyond a registry id, which is out of scope
 * for DESIGN.md's P3 (client capture) and left for a later phase.
 */
public final class ScreenKindMapper {

    private static final Map<String, ScreenKind> BY_REGISTRY_ID = Map.ofEntries(
            // Plain chests / double chests / hoppers / shulker boxes / the
            // dropper-dispenser 3x3 grid / the Crafter block: all generic
            // item-storage grids with no dedicated ScreenKind of their own.
            Map.entry("minecraft:generic_9x1", ScreenKind.CONTAINER),
            Map.entry("minecraft:generic_9x2", ScreenKind.CONTAINER),
            Map.entry("minecraft:generic_9x3", ScreenKind.CONTAINER),
            Map.entry("minecraft:generic_9x4", ScreenKind.CONTAINER),
            Map.entry("minecraft:generic_9x5", ScreenKind.CONTAINER),
            Map.entry("minecraft:generic_9x6", ScreenKind.CONTAINER),
            Map.entry("minecraft:generic_3x3", ScreenKind.CONTAINER),
            Map.entry("minecraft:crafter_3x3", ScreenKind.CONTAINER),
            Map.entry("minecraft:hopper", ScreenKind.CONTAINER),
            Map.entry("minecraft:shulker_box", ScreenKind.CONTAINER),
            // Grindstone has no dedicated ScreenKind; it is a container-like
            // repair UI, closest in kind to a generic container screen.
            Map.entry("minecraft:grindstone", ScreenKind.CONTAINER),

            Map.entry("minecraft:crafting", ScreenKind.CRAFTING),
            Map.entry("minecraft:furnace", ScreenKind.FURNACE),
            Map.entry("minecraft:blast_furnace", ScreenKind.FURNACE),
            Map.entry("minecraft:smoker", ScreenKind.FURNACE),
            Map.entry("minecraft:anvil", ScreenKind.ANVIL),
            Map.entry("minecraft:enchantment", ScreenKind.ENCHANTING),
            Map.entry("minecraft:brewing_stand", ScreenKind.BREWING),
            Map.entry("minecraft:merchant", ScreenKind.MERCHANT),
            Map.entry("minecraft:beacon", ScreenKind.BEACON),
            Map.entry("minecraft:loom", ScreenKind.LOOM),
            Map.entry("minecraft:smithing", ScreenKind.SMITHING),
            Map.entry("minecraft:stonecutter", ScreenKind.STONECUTTER),
            Map.entry("minecraft:cartography_table", ScreenKind.CARTOGRAPHY),
            // The lectern's screen is specifically for reading a book placed
            // in it; DESIGN.md's ScreenKind has no separate LECTERN value.
            Map.entry("minecraft:lectern", ScreenKind.BOOK_READ));

    private ScreenKindMapper() {
    }

    /**
     * @param registryId e.g. {@code "minecraft:generic_9x3"}. {@code null} or
     *                   any id not in the vanilla table (a modded screen
     *                   handler) yields {@link ScreenKind#MODDED}, per
     *                   DESIGN.md §6: "Tanınmayan id → MODDED."
     */
    public static ScreenKind fromRegistryId(String registryId) {
        if (registryId == null) {
            return ScreenKind.MODDED;
        }
        return BY_REGISTRY_ID.getOrDefault(registryId, ScreenKind.MODDED);
    }
}
