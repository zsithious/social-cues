package dev.zsithious.socialcues.core.state;

/**
 * DESIGN.md §4 — detail for {@link Activity#IN_SCREEN}.
 * {@link #MODDED} covers screen types that could not be resolved from the
 * handler-type registry id; {@link #UNKNOWN} covers everything else
 * (including {@code shareScreenDetail=false}, see DESIGN.md §9).
 *
 * <p><b>Ordinal-coded on the wire</b> ({@code core.protocol.EnumCodec}
 * encodes/decodes this enum by {@code ordinal()}, not by name) — checked
 * before adding {@link #CONTAINER_SMALL}/{@link #HOPPER}/{@link #SHULKER}/
 * {@link #DISPENSER} below (DESIGN.md §7 P5 hand-test fix, HATA7): appending
 * new constants at the end never renumbers an existing one, so this is safe.
 * Inserting or reordering would not be (every ordinal after the change point
 * would silently decode as the wrong kind on any peer running older/newer
 * code) — protocol v1, mod not yet released (0.1.0), so there is no live
 * compatibility concern either way for this particular change, but "append
 * only" is the right habit for the day this enum stops being free to
 * renumber.
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
    UNKNOWN,
    /** A single chest (generic_9x1..9x3) — distinct from {@link #CONTAINER}'s double chest. DESIGN.md §7 P5 hand-test fix. */
    CONTAINER_SMALL,
    HOPPER,
    SHULKER,
    /** The dispenser/dropper 3x3 grid and the Crafter block — both a 3x3 item grid with no dedicated GUI of their own beyond dispenser's. */
    DISPENSER
}
