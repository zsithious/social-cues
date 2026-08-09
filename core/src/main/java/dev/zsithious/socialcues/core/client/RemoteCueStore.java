package dev.zsithious.socialcues.core.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueDrop;
import dev.zsithious.socialcues.core.protocol.CueTier;
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
 * <p><b>Two separate maps, one per {@link CueTier}, not one shared map
 * (P5 hand-test bug, 2026-08-09).</b> {@code nearCues} holds the near tier's
 * full-detail entries; {@code globalCues} holds the global tier's coarse,
 * tab-list-only entries. Before this split existed there was a single
 * {@code Map<UUID, PlayerCue>} that {@link #applyBatch} wrote into with a
 * plain {@code put(...)}, regardless of which tier the batch came from. That
 * is exactly what let the bug happen: the global tier's own broadcast (at
 * most once per second, {@code global-broadcast-min-interval-ms}) always
 * carries {@code ScreenKind.UNKNOWN}/intensity 0/flags 0 by design (DESIGN.md
 * §5: "Global katman: ... sadece activity (kaba)",
 * {@code core.policy.EffectivePolicy#applyGlobalCoarse}) — a perfectly
 * correct coarse summary for the tab list, but a garbage detail level for the
 * world render. Whenever that coarse global batch happened to arrive after a
 * detailed near batch for the same player, "last write wins" silently
 * replaced the good data with the bad: a hand test saw the correct inventory
 * GUI on the held panel for about a second, then watched it flip to the
 * single-chest fallback texture the instant the next global broadcast landed.
 * Two maps make that structurally impossible — {@link #applyBatch} routes an
 * incoming batch to the map matching its {@link CueBatch#tier()}, and each of
 * {@link #cueOf}/{@link #tabCueOf} reads from the map appropriate to what is
 * asking (see their own Javadoc). See {@link CueTier}'s Javadoc and {@code
 * core.protocol.CueBatch}'s for the wire-format half of this fix.
 *
 * <p><b>{@code lastChangeMs} is invented here, not carried on the wire</b>
 * (DESIGN.md §4: "sadece yerel, ağda yok") — a {@link PlayerCue} arriving
 * for the first time, or arriving with a different activity/screen/
 * intensity/flags than what is already stored for that id **in the same
 * tier's map**, is timestamped {@code nowMs}; re-applying the exact same
 * state (which can legitimately happen — the relay resending after a
 * reconnect, or simply nothing having changed) leaves the previously
 * recorded timestamp untouched. {@code nowMs} is caller-supplied rather than
 * an injected clock object, matching {@code core.util.TypingRateMeter}/
 * {@code IdleTimer}'s existing pattern, so this class stays trivially
 * fake-clock testable without a mocking library.
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

    private final Map<UUID, PlayerCue> nearCues = new LinkedHashMap<>();
    private final Map<UUID, PlayerCue> globalCues = new LinkedHashMap<>();

    /**
     * DESIGN.md §5: applies one delta batch, into the map matching {@code
     * batch.tier()} (see class Javadoc — this is the routing step that keeps
     * the two tiers from overwriting each other). Entries not mentioned in
     * {@code batch} are left completely untouched — only an explicit
     * {@link #applyDrop} (or {@link #clear}) ever removes anything.
     */
    public void applyBatch(CueBatch batch, long nowMs) {
        Objects.requireNonNull(batch, "batch");
        Map<UUID, PlayerCue> target = mapFor(batch.tier());
        for (CueBatch.Entry entry : batch.entries()) {
            PlayerCue previous = target.get(entry.id());
            long changeMs = (previous != null && sameState(previous, entry)) ? previous.lastChangeMs() : nowMs;
            target.put(entry.id(), new PlayerCue(
                    entry.id(), entry.activity(), entry.screenKind(), entry.intensity(), entry.flags(), changeMs));
        }
    }

    /**
     * DESIGN.md §5: a {@code CueDrop} means "this id is no longer relevant to
     * you" (out of view, or the player disconnected) — forget it entirely,
     * in **both** tier maps. {@code CueDrop} carries no tier of its own (see
     * {@code core.protocol.CueDrop}), so a drop is applied uniformly; that is
     * correct for a genuine disconnect ({@code PlayerQuitEvent}, DESIGN.md
     * §8.3) or a vanish/visibility change, where the player should disappear
     * everywhere at once. Dropping an id this store never had is a harmless
     * no-op.
     */
    public void applyDrop(CueDrop drop) {
        Objects.requireNonNull(drop, "drop");
        for (UUID id : drop.ids()) {
            nearCues.remove(id);
            globalCues.remove(id);
        }
    }

    /**
     * Forgets every remote cue, in both tier maps. Callers must invoke this
     * whenever the handshake leaves {@code ACTIVE} (disconnect) or is about
     * to be renegotiated (a fresh join) — DESIGN.md's P4a task note:
     * "Bağlantı kesilince / el sıkışma dormant'a düşünce temizlenir." A stale
     * entry from a previous server — possibly with a completely different
     * player set, or the same UUID meaning someone else's current activity —
     * must never survive into a new session.
     */
    public void clear() {
        nearCues.clear();
        globalCues.clear();
    }

    /**
     * Katman 1 (billboard) and Katman 3 (held panel) — the world render —
     * both use this. It reads **only** {@code nearCues}: the coarse global
     * tier's entries (always {@code ScreenKind.UNKNOWN}, intensity 0, flags
     * 0, see class Javadoc) must never be drawn in the world, where a real
     * {@link PlayerCue#screen()} value is expected. If a player has no near
     * entry, this returns empty even when a global entry exists for them —
     * that is the correct "nothing to draw" answer for the world render, not
     * a bug; see {@link #tabCueOf} for the accessor that falls back to the
     * global tier.
     */
    public Optional<PlayerCue> cueOf(UUID id) {
        return Optional.ofNullable(nearCues.get(Objects.requireNonNull(id, "id")));
    }

    /**
     * Katman 2 (sekme listesi) uses this instead of {@link #cueOf}: the tab
     * list wants *some* status for every online player it can get one for,
     * even a coarse one, so it prefers the near tier's full detail when
     * available and falls back to the global tier's coarse activity
     * otherwise. Adapters should route the tab-list icon lookup
     * ({@code adapters/bucketD/.../mixin/PlayerListHudMixin}) through this
     * method, not {@link #cueOf}.
     */
    public Optional<PlayerCue> tabCueOf(UUID id) {
        Objects.requireNonNull(id, "id");
        PlayerCue near = nearCues.get(id);
        if (near != null) {
            return Optional.of(near);
        }
        return Optional.ofNullable(globalCues.get(id));
    }

    /** Known in either tier — a player only ever seen through the global tier still counts as known. */
    public boolean isKnown(UUID id) {
        Objects.requireNonNull(id, "id");
        return nearCues.containsKey(id) || globalCues.containsKey(id);
    }

    /** Every id this store currently holds a cue for, in either tier. A defensive copy — mutating it never affects the store. */
    public Set<UUID> knownPlayers() {
        Set<UUID> all = new LinkedHashSet<>(nearCues.keySet());
        all.addAll(globalCues.keySet());
        return Set.copyOf(all);
    }

    private Map<UUID, PlayerCue> mapFor(CueTier tier) {
        return switch (tier) {
            case NEAR -> nearCues;
            case GLOBAL -> globalCues;
        };
    }

    private static boolean sameState(PlayerCue stored, CueBatch.Entry entry) {
        return stored.activity() == entry.activity()
                && stored.screen() == entry.screenKind()
                && stored.intensity() == entry.intensity()
                && stored.flags() == entry.flags();
    }
}
