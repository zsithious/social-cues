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
import dev.zsithious.socialcues.core.protocol.CueTier;
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

    /**
     * The near tier is this file's default, since it is the one {@link
     * RemoteCueStore#cueOf} reads and therefore what almost every test below
     * asserts against; the tier-specific tests build their batches with
     * {@link #tierBatchOf} explicitly.
     */
    private static CueBatch batchOf(CueBatch.Entry... entries) {
        return tierBatchOf(CueTier.NEAR, entries);
    }

    private static CueBatch tierBatchOf(CueTier tier, CueBatch.Entry... entries) {
        return new CueBatch(tier, List.of(entries));
    }

    private static CueBatch.Entry entry(UUID id, Activity activity, int intensity, int flags) {
        return new CueBatch.Entry(id, activity, ScreenKind.UNKNOWN, intensity, flags);
    }

    private static CueBatch.Entry screenEntry(UUID id, ScreenKind screen) {
        return new CueBatch.Entry(id, Activity.IN_SCREEN, screen, 0, 0);
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
        assertThrows(NullPointerException.class, () -> store.tabCueOf(null));
        assertThrows(NullPointerException.class, () -> store.isKnown(null));
    }

    // --- Tier separation (DESIGN.md §5 / §7, P5 hand-test bug 2026-08-09) ---
    //
    // The bug: one shared map, plain put(), so whichever tier's batch landed
    // last won. The global tier's entries are ScreenKind.UNKNOWN by design
    // (EffectivePolicy.applyGlobalCoarse), so its ~1/s broadcast would wipe a
    // detailed near entry and the held panel would revert to the fallback
    // texture about a second after showing the right GUI. These tests pin the
    // fix from both directions, since the ordering was the whole bug.

    @Test
    void coarseGlobalEntryDoesNotOverwriteDetailedNearEntry() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.NEAR, screenEntry(ALICE, ScreenKind.FURNACE)), 1_000L);

        // Exactly what the relay's global tier sends a second later.
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, screenEntry(ALICE, ScreenKind.UNKNOWN)), 2_000L);

        PlayerCue cue = store.cueOf(ALICE).orElseThrow();
        assertEquals(ScreenKind.FURNACE, cue.screen());
        assertEquals(1_000L, cue.lastChangeMs());
    }

    @Test
    void detailedNearEntryIsVisibleEvenWhenTheGlobalEntryArrivedFirst() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, screenEntry(ALICE, ScreenKind.UNKNOWN)), 1_000L);
        store.applyBatch(tierBatchOf(CueTier.NEAR, screenEntry(ALICE, ScreenKind.FURNACE)), 2_000L);

        assertEquals(ScreenKind.FURNACE, store.cueOf(ALICE).orElseThrow().screen());
    }

    @Test
    void cueOfIgnoresAGlobalOnlyPlayerEntirely() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(ALICE, Activity.TYPING_CHAT, 0, 0)), 0L);

        // Known (the tab list can show them) but not drawable in the world:
        // there is no near-tier detail to draw.
        assertTrue(store.isKnown(ALICE));
        assertTrue(store.cueOf(ALICE).isEmpty());
        assertEquals(Set.of(ALICE), store.knownPlayers());
    }

    @Test
    void tabCueOfPrefersTheNearTierAndFallsBackToGlobal() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.NEAR, screenEntry(ALICE, ScreenKind.FURNACE)), 0L);
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, screenEntry(ALICE, ScreenKind.UNKNOWN)), 0L);
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(BOB, Activity.AFK, 0, 0)), 0L);

        assertEquals(ScreenKind.FURNACE, store.tabCueOf(ALICE).orElseThrow().screen());
        assertEquals(Activity.AFK, store.tabCueOf(BOB).orElseThrow().activity());
    }

    @Test
    void tabCueOfIsEmptyForAnUnknownPlayer() {
        RemoteCueStore store = new RemoteCueStore();
        assertTrue(store.tabCueOf(ALICE).isEmpty());
    }

    @Test
    void lastChangeMsIsTrackedPerTierNotGlobally() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.NEAR, entry(ALICE, Activity.AFK, 0, 0)), 1_000L);

        // Same state, other tier: this is the global map's *first* sight of
        // ALICE, so it stamps nowMs there — and must not disturb the near
        // map's own earlier stamp.
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(ALICE, Activity.AFK, 0, 0)), 9_000L);

        assertEquals(1_000L, store.cueOf(ALICE).orElseThrow().lastChangeMs());
        assertEquals(1_000L, store.tabCueOf(ALICE).orElseThrow().lastChangeMs());
    }

    @Test
    void dropRemovesThePlayerFromBothTiers() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.NEAR, entry(ALICE, Activity.TYPING_CHAT, 0, 0)), 0L);
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(ALICE, Activity.TYPING_CHAT, 0, 0)), 0L);

        store.applyDrop(new CueDrop(List.of(ALICE)));

        assertFalse(store.isKnown(ALICE));
        assertTrue(store.cueOf(ALICE).isEmpty());
        assertTrue(store.tabCueOf(ALICE).isEmpty());
    }

    @Test
    void clearWipesBothTiers() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.NEAR, entry(ALICE, Activity.TYPING_CHAT, 0, 0)), 0L);
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(BOB, Activity.AFK, 0, 0)), 0L);

        store.clear();

        assertTrue(store.knownPlayers().isEmpty());
        assertTrue(store.tabCueOf(ALICE).isEmpty());
        assertTrue(store.tabCueOf(BOB).isEmpty());
    }

    @Test
    void knownPlayersUnionsBothTiersWithoutDuplicating() {
        RemoteCueStore store = new RemoteCueStore();
        store.applyBatch(tierBatchOf(CueTier.NEAR, entry(ALICE, Activity.NORMAL, 0, 0)), 0L);
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(ALICE, Activity.NORMAL, 0, 0)), 0L);
        store.applyBatch(tierBatchOf(CueTier.GLOBAL, entry(BOB, Activity.NORMAL, 0, 0)), 0L);

        assertEquals(Set.of(ALICE, BOB), store.knownPlayers());
    }
}
