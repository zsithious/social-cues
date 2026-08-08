package dev.zsithious.socialcues.paper.relay;

import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import dev.zsithious.socialcues.core.relay.Position;
import dev.zsithious.socialcues.core.relay.VisibilityChecker;

/**
 * DESIGN.md §10.3's vanish/spectator rule, implemented as a thin Bukkit
 * adapter: {@link #canSee} delegates straight to {@code Player#canSee},
 * which is exactly the call vanish plugins (Essentials, CMI, SuperVanish...)
 * hook to make a vanished player invisible to specific viewers. This class
 * deliberately contains no vanish logic of its own — DESIGN.md §5 already
 * names this the boundary: "Bukkit'in {@code Player.canSee} çağrısı
 * adaptörde kalsın."
 */
public final class BukkitVisibilityChecker implements VisibilityChecker {

    @Override
    public boolean canSee(UUID viewer, UUID target) {
        Player viewerPlayer = Bukkit.getPlayer(viewer);
        Player targetPlayer = Bukkit.getPlayer(target);
        if (viewerPlayer == null || targetPlayer == null) {
            return false;
        }
        return viewerPlayer.canSee(targetPlayer);
    }

    @Override
    public Optional<Position> positionOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null) {
            return Optional.empty();
        }
        Location location = player.getLocation();
        return Optional.of(new Position(location.getX(), location.getY(), location.getZ()));
    }

    @Override
    public boolean sameWorld(UUID a, UUID b) {
        Player playerA = Bukkit.getPlayer(a);
        Player playerB = Bukkit.getPlayer(b);
        if (playerA == null || playerB == null) {
            return false;
        }
        return playerA.getWorld().equals(playerB.getWorld());
    }
}
