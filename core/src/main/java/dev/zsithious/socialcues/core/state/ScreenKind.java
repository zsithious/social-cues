package dev.zsithious.socialcues.core.state;

/**
 * DESIGN.md §4 — detail for {@link Activity#IN_SCREEN}.
 * {@link #MODDED} covers screen types that could not be resolved from the
 * handler-type registry id; {@link #UNKNOWN} covers everything else
 * (including {@code shareScreenDetail=false}, see DESIGN.md §9).
 */
public enum ScreenKind {
    INVENTORY,
    CONTAINER,
    CRAFTING,
    FURNACE,
    ANVIL,
    ENCHANTING,
    BREWING,
    MERCHANT,
    BEACON,
    LOOM,
    SMITHING,
    STONECUTTER,
    CARTOGRAPHY,
    BOOK_READ,
    MAP_VIEW,
    ADVANCEMENTS,
    RECIPE_BOOK,
    PAUSE,
    SETTINGS,
    MODDED,
    UNKNOWN
}
