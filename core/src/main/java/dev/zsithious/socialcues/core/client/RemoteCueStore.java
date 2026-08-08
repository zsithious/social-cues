package dev.zsithious.socialcues.core.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueDrop;
import dev.zsithious.socialcues.core.state.PlayerCue;

/**
 * DESIGN.md §14 P4a — the client-side twin of {@code core.relay.CueRelay}'s
 * state map, but far simpler: there is exactly one viewer (the local
 * player), permission masking already happened server-side before either
 * message type was ever sent, and this class's only job is to turn the wire
 * shape ({@code CueBatch}/{@code CueDrop}) into the shape P4b's render code
 * wants ({@code UUID -> PlayerCue}), while tracking when each entry's
 * *state* — not "when a packet last mentioned it" — actually last changed.
 *
 * <p><b>{@code CueBatch} is a delta, not a snapshot</b> (DESIGN.md §5: "Röle
 * her alıcı için son gönderdiği durumu tutar, değişmeyeni tekrar
 * göndermez.") — {@link #applyBatch} therefore only ever adds or overwrites
 * the entries a given batch actually carries; it never clears players the
 * batch simply doesn't mention. A player leaving view (moving out of
 * {@code nearRadius}, going invisible, or actually disconnecting) is always
 * communicated by an explicit {@code CueDrop} ({@link #applyDrop}), never by
 * a batch's silence about them.
 *
 * <p><b>{@code lastChangeMs} is invented here, not carried on the wire</b>
 * (DESIGN.md §4: "sadece yerel, ağda yok") — a {@link PlayerCue} arriving
 * for the first time, or arriving with a different activity/screen/
 * intensity/flags than what is already stored for that id, is timestamped
 * {@code nowMs}; re-applying the exact same state (which can legitimately
 * happen — the relay resending after a reconnect, or simply nothing having
 * changed) leaves the previously recorded timestamp untouched. {@code nowMs}
 * is caller-supplied rather than an injected clock object, matching
 * {@code core.util.TypingRateMeter}/{@code IdleTimer}'s existing pattern, so
 * this class stays trivially fake-clock testable without a mocking library.
 *
 * <p><b>Trust</b>: {@code CueBatch.Entry}'s own compact constructor already
 * guarantees a non-null id/activity/screenKind and a 0-255 intensity/flags
 * byte (see {@code WireChecks}) — this class relies on exactly that and
 * nothing more. It does not, for instance, assume a batch's entries have
 * distinct ids (a malformed or adversarial relay could repeat one; the last
 * occurrence in iteration order simply wins, same as any {@code Map.put}
 * loop would) or that {@code nowMs} is monotonically increasing across
 * calls.
 *
 * <p>Not thread-safe; expected to be driven entirely from the client thread,
 * exactly like every other piece of this mod's Minecraft-side state (see
 * {@code core.relay.CueRelay}'s Javadoc for the same assumption server-side).
 */
public final class RemoteCueStore {

    private final Map<UUID, PlayerCue> cues = new LinkedHashMap<>();

    /**
     * DESIGN.md §5: applies one delta batch. Entries not mentioned in
     * {@code batch} are left completely untouched — only an explicit
     * {@link #applyDrop} (or {@link #clear}) ever removes anything.
     */
    public void applyBatch(CueBatch batch, long nowMs) {
        Objects.requireNonNull(batch, "batch");
        for (CueBatch.Entry entry : batch.entries()) {
            PlayerCue previous = cues.get(entry.id());
            long changeMs = (previous != null && sameState(previous, entry)) ? previous.lastChangeMs() : nowMs;
            cues.put(entry.id(), new PlayerCue(
                    entry.id(), entry.activity(), entry.screenKind(), entry.intensity(), entry.flags(), changeMs));
        }
    }

    /**
     * DESIGN.md §5: a {@code CueDrop} means "this id is no longer relevant to
     * you" (out of view, or the player disconnected) — forget it entirely.
     * Dropping an id this store never had is a harmless no-op.
     */
    public void applyDrop(CueDrop drop) {
        Objects.requireNonNull(drop, "drop");
        for (UUID id : drop.ids()) {
            cues.remove(id);
        }
    }

    /**
     * Forgets every remote cue. Callers must invoke this whenever the
     * handshake leaves {@code ACTIVE} (disconnect) or is about to be
     * renegotiated (a fresh join) — DESIGN.md's P4a task note: "Bağlantı
     * kesilince / el sıkışma dormant'a düşünce temizlenir." A stale entry
     * from a previous server — possibly with a completely different player
     * set, or the same UUID meaning someone else's current activity — must
     * never survive into a new session.
     */
    public void clear() {
        cues.clear();
    }

    public Optional<PlayerCue> cueOf(UUID id) {
        return Optional.ofNullable(cues.get(Objects.requireNonNull(id, "id")));
    }

    public boolean isKnown(UUID id) {
        return cues.containsKey(Objects.requireNonNull(id, "id"));
    }

    /** Every id this store currently holds a cue for. A defensive copy — mutating it never affects the store. */
    public Set<UUID> knownPlayers() {
        return Set.copyOf(cues.keySet());
    }

    private static boolean sameState(PlayerCue stored, CueBatch.Entry entry) {
        return stored.activity() == entry.activity()
                && stored.screen() == entry.screenKind()
                && stored.intensity() == entry.intensity()
                && stored.flags() == entry.flags();
    }
}
