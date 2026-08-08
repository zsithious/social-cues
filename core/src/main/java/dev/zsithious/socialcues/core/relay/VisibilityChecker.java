package dev.zsithious.socialcues.core.relay;

import java.util.Optional;
import java.util.UUID;

/**
 * DESIGN.md §5/§10.3: "Görünürlük filtresi: {@code recipient.canSee(target)}
 * false ise durum hiç gönderilmez." {@link CueRelay} asks this interface,
 * never a platform API directly, so the vanish/spectator rule can be unit
 * tested with a fake implementation instead of a live Bukkit {@code Player}.
 *
 * <p>The Paper adapter implements this with {@code Player#canSee(Player)}
 * (which already accounts for vanish plugins that hook that call) and
 * {@code Location}/{@code World}; a future Fabric-server relay would answer
 * the same questions from vanilla tracking/visibility state.
 */
public interface VisibilityChecker {

    /**
     * Whether {@code viewer} is currently allowed to know {@code target}
     * exists at all — false for a vanished/spectator-hidden target. This is
     * the single most important method on this interface: DESIGN.md §10.3
     * calls skipping it "bu olmadan mod, vanish'li adminleri ifşa eden bir
     * araca dönüşür" (without it, the mod becomes a tool that outs vanished
     * admins).
     */
    boolean canSee(UUID viewer, UUID target);

    /** Empty when the player is offline/unknown or their position cannot be determined right now. */
    Optional<Position> positionOf(UUID id);

    /** Whether the two players are currently in the same world. Distance across worlds is meaningless. */
    boolean sameWorld(UUID a, UUID b);
}
