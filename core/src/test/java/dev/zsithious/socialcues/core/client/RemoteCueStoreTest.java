package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueDrop;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §14 P4a: the delta-application/local-timestamping contract
 * {@code mcshared.network.ClientHandshakeNetworking} relies on and P4b's
 * render code will read through.
 */
class RemoteCueStoreTest {

    private static final UUID ALICE = new UUID(0L, 1L);
    private static final UUID BOB = new UUID(0L, 2L);

    private static CueBatch batchOf(CueBatch.Entry... entries) {
        return new CueBatch(List.of(entries));
    }

    private static CueBatch.Entry entry(UUID id, Activity activity, int intensity, int flags) {
        return new CueBatch.Entry(id, activity, ScreenKind.UNKNOWN, intensity, flags);
    }

    @Test
    void unknownPlayerStartsAbsent() {
        RemoteCueStore store = new RemoteCueStore();
        assertFalse(store.isKnown(ALICE));
        assertTrue(store.cueOf(ALICE).isEmpty());
        assertTrue(store.knownPlayers().isEmpty());
    }

    @Test
    void firstBatchEntryIsStampedWithNowAndStored() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.TYPING_CHAT, 100, 0)), 1_000L);

        PlayerCue cue = store.cueOf(ALICE).orElseThrow();
        assertEquals(Activity.TYPING_CHAT, cue.activity());
        assertEquals(100, cue.intensity());
        assertEquals(1_000L, cue.lastChangeMs());
        assertTrue(store.isKnown(ALICE));
    }

    @Test
    void deltaBatchesAccumulateRatherThanReplacingTheWholeStore() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.TYPING_CHAT, 50, 0)), 0L);
        // A later batch about BOB only, exactly like a real relay delta —
        // ALICE is not mentioned at all, so ALICE must not disappear.
        store.applyBatch(batchOf(entry(BOB, Activity.AFK, 0, 0)), 10L);

        assertTrue(store.isKnown(ALICE));
        assertTrue(store.isKnown(BOB));
        assertEquals(Set.of(ALICE, BOB), store.knownPlayers());
    }

    @Test
    void applyDropRemovesOnlyTheNamedPlayers() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(
                entry(ALICE, Activity.TYPING_CHAT, 0, 0),
                entry(BOB, Activity.AFK, 0, 0)), 0L);

        store.applyDrop(new CueDrop(List.of(ALICE)));

        assertFalse(store.isKnown(ALICE));
        assertTrue(store.isKnown(BOB));
    }

    @Test
    void applyDropOfUnknownIdIsHarmlessNoOp() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyDrop(new CueDrop(List.of(ALICE)));
        assertFalse(store.isKnown(ALICE));
    }

    @Test
    void lastChangeMsStaysStableWhenTheSameStateIsResent() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.AFK, 0, 0)), 1_000L);

        // Same activity/screen/intensity/flags, much later timestamp: the
        // relay might resend after a reconnect, or this might just be a
        // second tier's batch repeating unchanged state.
        store.applyBatch(batchOf(entry(ALICE, Activity.AFK, 0, 0)), 50_000L);

        assertEquals(1_000L, store.cueOf(ALICE).orElseThrow().lastChangeMs());
    }

    @Test
    void lastChangeMsAdvancesWhenActivityActuallyChanges() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.NORMAL, 0, 0)), 1_000L);
        store.applyBatch(batchOf(entry(ALICE, Activity.TYPING_CHAT, 0, 0)), 2_000L);

        assertEquals(2_000L, store.cueOf(ALICE).orElseThrow().lastChangeMs());
    }

    @Test
    void lastChangeMsAdvancesWhenOnlyIntensityChanges() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.TYPING_CHAT, 10, 0)), 1_000L);
        store.applyBatch(batchOf(entry(ALICE, Activity.TYPING_CHAT, 200, 0)), 2_000L);

        assertEquals(2_000L, store.cueOf(ALICE).orElseThrow().lastChangeMs());
        assertEquals(200, store.cueOf(ALICE).orElseThrow().intensity());
    }

    @Test
    void lastChangeMsAdvancesWhenOnlyFlagsChange() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.AFK, 0, 0)), 1_000L);
        store.applyBatch(batchOf(entry(ALICE, Activity.AFK, 0, CueFlags.SLEEPY)), 2_000L);

        PlayerCue cue = store.cueOf(ALICE).orElseThrow();
        assertEquals(2_000L, cue.lastChangeMs());
        assertTrue(cue.hasFlag(CueFlags.SLEEPY));
    }

    @Test
    void emptyBatchIsANoOpAndDoesNotClearExistingEntries() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.TYPING_CHAT, 0, 0)), 0L);

        store.applyBatch(batchOf(), 999L);

        assertTrue(store.isKnown(ALICE));
        assertEquals(0L, store.cueOf(ALICE).orElseThrow().lastChangeMs());
    }

    @Test
    void clearWipesEverything() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(
                entry(ALICE, Activity.TYPING_CHAT, 0, 0),
                entry(BOB, Activity.AFK, 0, 0)), 0L);

        store.clear();

        assertTrue(store.knownPlayers().isEmpty());
        assertFalse(store.isKnown(ALICE));
        assertFalse(store.isKnown(BOB));
    }

    @Test
    void reappliedAfterClearIsTreatedAsBrandNewRegardlessOfPriorHistory() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.AFK, 0, 0)), 1_000L);
        store.clear();

        // Same exact state as before the clear, but the store has no memory
        // of it anymore -> stamped with the new nowMs, not the old one.
        store.applyBatch(batchOf(entry(ALICE, Activity.AFK, 0, 0)), 99_000L);

        assertEquals(99_000L, store.cueOf(ALICE).orElseThrow().lastChangeMs());
    }

    @Test
    void duplicateIdWithinASingleBatchLetsTheLastEntryWin() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(
                entry(ALICE, Activity.TYPING_CHAT, 10, 0),
                entry(ALICE, Activity.AFK, 0, 0)), 5_000L);

        PlayerCue cue = store.cueOf(ALICE).orElseThrow();
        assertEquals(Activity.AFK, cue.activity());
        assertEquals(5_000L, cue.lastChangeMs());
    }

    @Test
    void knownPlayersIsADefensiveCopy() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(batchOf(entry(ALICE, Activity.NORMAL, 0, 0)), 0L);

        var snapshot = store.knownPlayers();
        store.applyBatch(batchOf(entry(BOB, Activity.NORMAL, 0, 0)), 0L);

        assertEquals(1, snapshot.size());
        assertEquals(2, store.knownPlayers().size());
    }

    @Test
    void nullBatchIsRejected() {
        RemoteCueStore store = new RemoteCueStore();
        assertThrows(NullPointerException.class, () -> store.applyBatch(null, 0L));
    }

    @Test
    void nullDropIsRejected() {
        RemoteCueStore store = new RemoteCueStore();
        assertThrows(NullPointerException.class, () -> store.applyDrop(null));
    }

    @Test
    void nullIdLookupsAreRejected() {
        RemoteCueStore store = new RemoteCueStore();
        assertThrows(NullPointerException.class, () -> store.cueOf(null));
        assertThrows(NullPointerException.class, () -> store.isKnown(null));
    }
}
