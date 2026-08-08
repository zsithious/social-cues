package dev.zsithious.socialcues.core.relay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import dev.zsithious.socialcues.core.policy.EffectivePolicy;
import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.protocol.C2SMessage;
import dev.zsithious.socialcues.core.protocol.C2SMessages;
import dev.zsithious.socialcues.core.protocol.ClientHello;
import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueDrop;
import dev.zsithious.socialcues.core.protocol.CueUpdate;
import dev.zsithious.socialcues.core.protocol.ProtocolDecodeException;
import dev.zsithious.socialcues.core.protocol.SharePrefs;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §8's platform-independent relay: "röle mantığı iki kez
 * yazılmayacak." Everything a server needs to do with player cues — state
 * storage, join/leave, permission masking, delta suppression, the near/global
 * tier split, rate limiting, and packet-size/malformed-input policing — lives
 * here, in pure Java, so it is fully exercisable with JUnit. The Paper plugin
 * (and any future Fabric-server relay) is expected to be a thin adapter: feed
 * it lifecycle events and raw bytes, take its {@link TickResult}/{@link
 * IngestOutcome}/{@link LeaveResult} and turn them into actual network I/O.
 *
 * <p>Not thread-safe. Every method is expected to be called from a single
 * platform "main thread" (Bukkit's main thread, or the Fabric server thread),
 * exactly like {@code mcshared.network.ServerHandshake} already assumes for
 * P1's handshake state.
 */
public final class CueRelay {

    private final VisibilityChecker visibility;
    private final RelayConfig config;

    private final Map<UUID, PlayerCue> cues = new LinkedHashMap<>();
    private final Map<UUID, Integer> prefBits = new LinkedHashMap<>();
    private int policyBits = PolicyBits.ALL;

    private final Map<UUID, Deque<Long>> recentUpdateTimestamps = new LinkedHashMap<>();
    private final Map<UUID, Integer> violationStreak = new LinkedHashMap<>();

    /** viewer -> target -> last entry actually sent to that viewer, near tier. */
    private final Map<UUID, Map<UUID, CueBatch.Entry>> lastSentNear = new LinkedHashMap<>();
    /** viewer -> target -> last entry actually sent to that viewer, global tier. */
    private final Map<UUID, Map<UUID, CueBatch.Entry>> lastSentGlobal = new LinkedHashMap<>();

    /** target -> the coarse value currently "in effect" server-wide for the global tier's ≤1/s throttle. */
    private final Map<UUID, CueBatch.Entry> globalBroadcastValue = new LinkedHashMap<>();
    /** target -> when {@link #globalBroadcastValue} last actually changed. */
    private final Map<UUID, Long> globalBroadcastChangedAtMs = new LinkedHashMap<>();

    public CueRelay(VisibilityChecker visibility, RelayConfig config) {
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.config = Objects.requireNonNull(config, "config");
    }

    // ---- lifecycle -----------------------------------------------------

    /** DESIGN.md §8.3 (join half): registers the player with a neutral default cue and full default prefBits. */
    public void join(UUID id, long nowMs) {
        Objects.requireNonNull(id, "id");
        cues.putIfAbsent(id, new PlayerCue(id, Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0, nowMs));
        prefBits.putIfAbsent(id, PolicyBits.ALL);
    }

    /**
     * DESIGN.md §8.3: "{@code PlayerQuitEvent} → temizle + {@code CueDrop}
     * yayınla." Clears every trace of {@code id}, including as a *viewer* of
     * others (its own delta-tracking maps), and reports who still needs to
     * be told {@code id} is gone.
     */
    public LeaveResult leave(UUID id) {
        Objects.requireNonNull(id, "id");
        cues.remove(id);
        prefBits.remove(id);
        recentUpdateTimestamps.remove(id);
        violationStreak.remove(id);
        lastSentNear.remove(id);
        lastSentGlobal.remove(id);
        globalBroadcastValue.remove(id);
        globalBroadcastChangedAtMs.remove(id);

        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.addAll(purgeAsTarget(id, lastSentNear));
        recipients.addAll(purgeAsTarget(id, lastSentGlobal));
        return new LeaveResult(recipients);
    }

    private static Set<UUID> purgeAsTarget(UUID target, Map<UUID, Map<UUID, CueBatch.Entry>> lastSentByViewer) {
        Set<UUID> viewers = new LinkedHashSet<>();
        for (Map.Entry<UUID, Map<UUID, CueBatch.Entry>> entry : lastSentByViewer.entrySet()) {
            if (entry.getValue().remove(target) != null) {
                viewers.add(entry.getKey());
            }
        }
        return viewers;
    }

    public boolean isKnown(UUID id) {
        return cues.containsKey(id);
    }

    public Set<UUID> knownPlayers() {
        return Set.copyOf(cues.keySet());
    }

    public Optional<PlayerCue> cueOf(UUID id) {
        return Optional.ofNullable(cues.get(id));
    }

    // ---- policy ----------------------------------------------------------

    /** Server-wide policy, applied identically to every player (DESIGN.md §5's {@code ServerHello.policyBits}). */
    public void setPolicyBits(int policyBits) {
        if (policyBits < 0 || policyBits > 0xFF) {
            throw new IllegalArgumentException("policyBits must be 0-255, was " + policyBits);
        }
        this.policyBits = policyBits;
    }

    public int policyBits() {
        return policyBits;
    }

    public OptionalInt prefBitsOf(UUID id) {
        Integer bits = prefBits.get(id);
        return bits == null ? OptionalInt.empty() : OptionalInt.of(bits);
    }

    /**
     * Directly overrides a known player's prefBits, bypassing the normal
     * {@code SharePrefs} message path. Exists for adapters that layer a
     * platform permission on top of the protocol (e.g. Paper's
     * {@code socialcues.share}, DESIGN.md §8.6): the adapter can call this
     * after every {@link #ingest} to make the permission win even if the
     * client's own {@code SharePrefs} just asked for more than it's allowed.
     * A no-op for a sender that hasn't {@link #join joined} (or already left).
     */
    public void setPrefBits(UUID id, int prefBits) {
        if (prefBits < 0 || prefBits > 0xFF) {
            throw new IllegalArgumentException("prefBits must be 0-255, was " + prefBits);
        }
        if (cues.containsKey(id)) {
            this.prefBits.put(id, prefBits);
        }
    }

    /** DESIGN.md §5: "Etkin izin = policyBits AND prefBits", defaulting an unknown player's prefBits to {@link PolicyBits#ALL}. */
    public int effectiveBits(UUID target) {
        return EffectivePolicy.effectiveBits(policyBits, prefBits.getOrDefault(target, PolicyBits.ALL));
    }

    // ---- ingestion ---------------------------------------------------------

    /**
     * Single entrypoint for every raw {@code socialcues:v1} C2S payload.
     * Enforces the size cap first (DESIGN.md §5: "sunucu 64 bayt üstünü
     * reddeder"), then decodes, then dispatches by message type. A
     * {@code ClientHello} or {@code SharePrefs} is applied/acknowledged
     * directly; a {@code CueUpdate} additionally goes through the ≤4/s rate
     * limit before being stored.
     */
    public IngestOutcome ingest(UUID sender, byte[] raw, long nowMs) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(raw, "raw");

        if (!cues.containsKey(sender)) {
            return new IngestOutcome(IngestStatus.UNKNOWN_SENDER, 0);
        }
        if (raw.length > config.maxPacketSize()) {
            return recordViolation(sender, IngestStatus.TOO_LARGE);
        }

        C2SMessage message;
        try {
            message = C2SMessages.decode(raw);
        } catch (ProtocolDecodeException e) {
            return recordViolation(sender, IngestStatus.MALFORMED);
        }

        if (message instanceof ClientHello) {
            violationStreak.put(sender, 0);
            return new IngestOutcome(IngestStatus.HELLO_RECEIVED, 0);
        }
        if (message instanceof SharePrefs prefs) {
            prefBits.put(sender, prefs.prefBits());
            violationStreak.put(sender, 0);
            return new IngestOutcome(IngestStatus.PREFS_UPDATED, 0);
        }
        if (message instanceof CueUpdate update) {
            return ingestCueUpdate(sender, update, nowMs);
        }
        // Unreachable: C2SMessage is sealed over exactly these three types.
        return recordViolation(sender, IngestStatus.MALFORMED);
    }

    private IngestOutcome ingestCueUpdate(UUID sender, CueUpdate update, long nowMs) {
        if (isRateLimited(sender, nowMs)) {
            return recordViolation(sender, IngestStatus.RATE_LIMITED);
        }
        recordUpdateTimestamp(sender, nowMs);
        cues.put(sender, new PlayerCue(sender, update.activity(), update.screenKind(),
                update.intensity(), update.flags(), nowMs));
        violationStreak.put(sender, 0);
        return new IngestOutcome(IngestStatus.ACCEPTED, 0);
    }

    private boolean isRateLimited(UUID id, long nowMs) {
        Deque<Long> timestamps = recentUpdateTimestamps.computeIfAbsent(id, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && nowMs - timestamps.peekFirst() >= 1000L) {
            timestamps.pollFirst();
        }
        return timestamps.size() >= config.maxUpdatesPerSecond();
    }

    private void recordUpdateTimestamp(UUID id, long nowMs) {
        recentUpdateTimestamps.computeIfAbsent(id, k -> new ArrayDeque<>()).addLast(nowMs);
    }

    private IngestOutcome recordViolation(UUID id, IngestStatus status) {
        int streak = violationStreak.merge(id, 1, Integer::sum);
        return new IngestOutcome(status, streak);
    }

    // ---- broadcast (DESIGN.md §5 "İki katman") -----------------------------

    /**
     * Computes, for every known player acting as a viewer, the delta-only,
     * visibility-filtered, permission-masked messages for both tiers. Meant
     * to be called once per {@code updateIntervalTicks} by the adapter's
     * single scheduler (DESIGN.md §8.9); the global tier's own ≤1/s cadence
     * is self-throttled here regardless of how often the caller invokes this.
     */
    public TickResult tick(long nowMs) {
        Map<UUID, CueBatch> nearBatches = new LinkedHashMap<>();
        Map<UUID, CueDrop> nearDrops = new LinkedHashMap<>();
        Map<UUID, CueBatch> globalBatches = new LinkedHashMap<>();
        Map<UUID, CueDrop> globalDrops = new LinkedHashMap<>();

        for (UUID viewer : List.copyOf(cues.keySet())) {
            Optional<Map<UUID, CueBatch.Entry>> nearEligible = eligibleNearTargets(viewer);
            if (nearEligible.isPresent()) {
                TierUpdate update = computeTierUpdate(viewer, nearEligible.get(), lastSentNear);
                if (!update.changed().isEmpty()) {
                    nearBatches.put(viewer, new CueBatch(update.changed()));
                }
                if (!update.dropped().isEmpty()) {
                    nearDrops.put(viewer, new CueDrop(update.dropped()));
                }
            }

            Map<UUID, CueBatch.Entry> globalEligible = eligibleGlobalTargets(viewer, nowMs);
            TierUpdate update = computeTierUpdate(viewer, globalEligible, lastSentGlobal);
            if (!update.changed().isEmpty()) {
                globalBatches.put(viewer, new CueBatch(update.changed()));
            }
            if (!update.dropped().isEmpty()) {
                globalDrops.put(viewer, new CueDrop(update.dropped()));
            }
        }

        return new TickResult(nearBatches, nearDrops, globalBatches, globalDrops);
    }

    /**
     * DESIGN.md §5 near tier: same world, within {@code nearRadius}, and
     * visible. Returns {@link Optional#empty()} — not an empty map — when
     * the viewer's own position is unknown right now, so {@link #tick}
     * leaves that viewer's near-tier state untouched instead of treating
     * "position temporarily unavailable" as "nobody is near anymore" and
     * spuriously dropping everyone.
     */
    private Optional<Map<UUID, CueBatch.Entry>> eligibleNearTargets(UUID viewer) {
        Optional<Position> viewerPos = visibility.positionOf(viewer);
        if (viewerPos.isEmpty()) {
            return Optional.empty();
        }
        Map<UUID, CueBatch.Entry> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, PlayerCue> entry : cues.entrySet()) {
            UUID target = entry.getKey();
            if (target.equals(viewer)) {
                continue;
            }
            if (!visibility.sameWorld(viewer, target)) {
                continue;
            }
            if (!visibility.canSee(viewer, target)) {
                continue; // DESIGN.md §10.3 — vanish/spectator: never even considered, let alone sent.
            }
            Optional<Position> targetPos = visibility.positionOf(target);
            if (targetPos.isEmpty()) {
                continue;
            }
            if (viewerPos.get().distanceTo(targetPos.get()) > config.nearRadius()) {
                continue;
            }
            result.put(target, EffectivePolicy.applyNear(entry.getValue(), effectiveBits(target)));
        }
        return Optional.of(result);
    }

    /**
     * DESIGN.md §5 global tier: no distance/world restriction, but still
     * visibility-filtered (vanish must be hidden from the tab list too),
     * gated by the target's effective {@link PolicyBits#GLOBAL_TIER} bit,
     * and throttled to at most one accepted change per second per target
     * via {@link #resolveGlobalThrottle}.
     */
    private Map<UUID, CueBatch.Entry> eligibleGlobalTargets(UUID viewer, long nowMs) {
        Map<UUID, CueBatch.Entry> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, PlayerCue> entry : cues.entrySet()) {
            UUID target = entry.getKey();
            if (target.equals(viewer)) {
                continue;
            }
            if (!visibility.canSee(viewer, target)) {
                continue;
            }
            int effective = effectiveBits(target);
            CueBatch.Entry nearMasked = EffectivePolicy.applyNear(entry.getValue(), effective);
            Optional<CueBatch.Entry> coarse = EffectivePolicy.applyGlobalCoarse(nearMasked, effective);
            if (coarse.isEmpty()) {
                continue;
            }
            result.put(target, resolveGlobalThrottle(target, coarse.get(), nowMs));
        }
        return result;
    }

    private CueBatch.Entry resolveGlobalThrottle(UUID target, CueBatch.Entry candidate, long nowMs) {
        CueBatch.Entry current = globalBroadcastValue.get(target);
        if (current == null || !current.equals(candidate)) {
            long lastChangeMs = globalBroadcastChangedAtMs.getOrDefault(target, Long.MIN_VALUE);
            if (current == null || nowMs - lastChangeMs >= config.globalBroadcastMinIntervalMs()) {
                globalBroadcastValue.put(target, candidate);
                globalBroadcastChangedAtMs.put(target, nowMs);
                return candidate;
            }
            return current; // change pending: too soon since the last one, keep showing the old value.
        }
        return current;
    }

    private TierUpdate computeTierUpdate(UUID viewer, Map<UUID, CueBatch.Entry> eligible,
                                          Map<UUID, Map<UUID, CueBatch.Entry>> lastSentByViewer) {
        Map<UUID, CueBatch.Entry> lastSent = lastSentByViewer.computeIfAbsent(viewer, v -> new LinkedHashMap<>());

        List<CueBatch.Entry> changed = new ArrayList<>();
        for (Map.Entry<UUID, CueBatch.Entry> entry : eligible.entrySet()) {
            CueBatch.Entry previous = lastSent.get(entry.getKey());
            if (!entry.getValue().equals(previous)) {
                lastSent.put(entry.getKey(), entry.getValue());
                changed.add(entry.getValue());
            }
        }

        List<UUID> dropped = new ArrayList<>();
        lastSent.keySet().removeIf(target -> {
            if (eligible.containsKey(target)) {
                return false;
            }
            dropped.add(target);
            return true;
        });

        return new TierUpdate(changed, dropped);
    }

    private record TierUpdate(List<CueBatch.Entry> changed, List<UUID> dropped) {
    }
}
