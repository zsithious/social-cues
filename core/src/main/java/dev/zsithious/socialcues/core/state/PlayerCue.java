package dev.zsithious.socialcues.core.state;

import java.util.Objects;
import java.util.UUID;

/**
 * DESIGN.md §4 — full local view of one player's cue.
 * {@code lastChangeMs} is local bookkeeping only; it is never sent over the
 * wire (see core.protocol.CueBatch.Entry for the wire-shaped equivalent).
 */
public record PlayerCue(UUID id, Activity activity, ScreenKind screen,
                         int intensity, int flags, long lastChangeMs) {

    public PlayerCue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(screen, "screen");
        if (intensity < 0 || intensity > 255) {
            throw new IllegalArgumentException("intensity must be 0-255, was " + intensity);
        }
        if (flags < 0 || flags > 255) {
            throw new IllegalArgumentException("flags must be 0-255, was " + flags);
        }
    }

    public boolean hasFlag(int flag) {
        return (flags & flag) != 0;
    }
}
