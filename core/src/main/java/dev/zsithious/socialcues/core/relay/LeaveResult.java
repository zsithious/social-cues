package dev.zsithious.socialcues.core.relay;

import java.util.Set;
import java.util.UUID;

/**
 * DESIGN.md §8.3: "{@code PlayerQuitEvent} → temizle + {@code CueDrop}
 * yayınla." {@link CueRelay#leave} returns exactly who needs that
 * {@code CueDrop}: every viewer who had previously received at least one
 * batch entry (near or global tier) mentioning the departing player. A
 * viewer who never saw them gets nothing — sending a drop for a player they
 * never knew about would be a no-op on the client anyway, but the adapter
 * shouldn't have to send it.
 */
public record LeaveResult(Set<UUID> recipientsToNotify) {

    public LeaveResult {
        recipientsToNotify = Set.copyOf(recipientsToNotify);
    }
}
