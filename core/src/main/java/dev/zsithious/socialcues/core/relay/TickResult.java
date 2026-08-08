package dev.zsithious.socialcues.core.relay;

import java.util.Map;
import java.util.UUID;

import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueDrop;

/**
 * Output of one {@link CueRelay#tick} call: per-viewer, delta-only,
 * visibility-filtered, permission-masked messages for both DESIGN.md §5
 * tiers. A viewer with nothing new this tick is simply absent from the
 * relevant map — DESIGN.md §5: "Röle ... değişmeyeni tekrar göndermez" — so
 * the adapter's job is just "for each entry present here, send it", never
 * "send something to everyone every tick".
 */
public record TickResult(Map<UUID, CueBatch> nearBatches, Map<UUID, CueDrop> nearDrops,
                          Map<UUID, CueBatch> globalBatches, Map<UUID, CueDrop> globalDrops) {

    public TickResult {
        nearBatches = Map.copyOf(nearBatches);
        nearDrops = Map.copyOf(nearDrops);
        globalBatches = Map.copyOf(globalBatches);
        globalDrops = Map.copyOf(globalDrops);
    }
}
