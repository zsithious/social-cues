package dev.zsithious.socialcues.core.relay;

/**
 * Platform-independent stand-in for a Bukkit {@code Location} / Minecraft
 * position, used only for the near-tier radius check in {@link CueRelay}.
 * Deliberately just three doubles — no world reference, since "same world"
 * is its own, separate question answered by {@link VisibilityChecker#sameWorld}.
 */
public record Position(double x, double y, double z) {

    public double distanceTo(Position other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
