package dev.zsithious.socialcues.core.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.protocol.C2SMessages;
import dev.zsithious.socialcues.core.protocol.ClientHello;
import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueTier;
import dev.zsithious.socialcues.core.protocol.CueUpdate;
import dev.zsithious.socialcues.core.protocol.ProtocolConstants;
import dev.zsithious.socialcues.core.protocol.SharePrefs;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §14 P2's actual acceptance path: the whole relay — state store,
 * join/leave, permission masking, delta suppression, near/global tiering,
 * the vanish/visibility filter, rate limiting, and size/malformed policing —
 * exercised without booting Minecraft or Bukkit, using {@link FakeVisibility}
 * in place of a real {@code Player}.
 */
class CueRelayTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-00000000000c");

    /** Minimal mutable {@link VisibilityChecker} test double. Defaults: no position, no world, mutually visible. */
    private static final class FakeVisibility implements VisibilityChecker {
        private final Map<UUID, Position> positions = new HashMap<>();
        private final Map<UUID, String> worlds = new HashMap<>();
        private final Set<Pair> hidden = new HashSet<>();

        void place(UUID id, Position pos, String world) {
            positions.put(id, pos);
            worlds.put(id, world);
        }

        void forgetPosition(UUID id) {
            positions.remove(id);
        }

        void hide(UUID viewer, UUID target) {
            hidden.add(new Pair(viewer, target));
        }

        void unhide(UUID viewer, UUID target) {
            hidden.remove(new Pair(viewer, target));
        }

        @Override
        public boolean canSee(UUID viewer, UUID target) {
            return !hidden.contains(new Pair(viewer, target));
        }

        @Override
        public Optional<Position> positionOf(UUID id) {
            return Optional.ofNullable(positions.get(id));
        }

        @Override
        public boolean sameWorld(UUID a, UUID b) {
            String wa = worlds.get(a);
            String wb = worlds.get(b);
            return wa != null && wa.equals(wb);
        }

        private record Pair(UUID viewer, UUID target) {
        }
    }

    private static CueRelay relay(FakeVisibility visibility) {
        return new CueRelay(visibility, RelayConfig.defaults());
    }

    private static void placeTogether(FakeVisibility vis, UUID... ids) {
        for (UUID id : ids) {
            vis.place(id, new Position(0, 0, 0), "world");
        }
    }

    private static byte[] cueUpdateBytes(Activity activity, ScreenKind screen, int intensity, int flags) {
        return C2SMessages.encode(new CueUpdate(activity, screen, intensity, flags));
    }

    // ---- join / leave -----------------------------------------------------

    @Test
    void joinRegistersDefaultNeutralCue() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        assertTrue(relay.isKnown(A));
        assertEquals(Activity.NORMAL, relay.cueOf(A).orElseThrow().activity());
    }

    @Test
    void joinIsIdempotentAndDoesNotResetExistingState() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 50, 0), 10L);

        relay.join(A, 999L); // e.g. a duplicate JOIN event

        assertEquals(Activity.TYPING_CHAT, relay.cueOf(A).orElseThrow().activity());
    }

    @Test
    void leaveOfUnseenPlayerReturnsNoRecipients() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        var result = relay.leave(A);

        assertTrue(result.recipientsToNotify().isEmpty());
        assertFalse(relay.isKnown(A));
    }

    @Test
    void leaveNotifiesEveryoneWhoHadReceivedThePlayer() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 10, 0), 0L);
        relay.tick(0L); // B now has A in lastSentNear/Global

        var result = relay.leave(A);

        assertTrue(result.recipientsToNotify().contains(B));
    }

    // ---- ingest: dispatch + boundary policing ------------------------------

    @Test
    void ingestFromUnknownSenderIsRejected() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        var outcome = relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);
        assertEquals(IngestStatus.UNKNOWN_SENDER, outcome.status());
    }

    @Test
    void ingestOversizedPacketIsRejectedBeforeDecoding() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        byte[] oversized = new byte[ProtocolConstants.MAX_C2S_PACKET_SIZE + 1];
        var outcome = relay.ingest(A, oversized, 0L);

        assertEquals(IngestStatus.TOO_LARGE, outcome.status());
        assertEquals(1, outcome.violationStreak());
    }

    @Test
    void ingestMalformedBytesUnderSizeLimitIsRejected() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        var outcome = relay.ingest(A, new byte[] {(byte) 0xEE}, 0L); // unknown type id, valid size
        assertEquals(IngestStatus.MALFORMED, outcome.status());
    }

    @Test
    void ingestClientHelloDoesNotTouchStoredCue() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        Activity before = relay.cueOf(A).orElseThrow().activity();

        byte[] hello = C2SMessages.encode(new ClientHello(ProtocolConstants.VERSION, "1.0.0", 0));
        var outcome = relay.ingest(A, hello, 0L);

        assertEquals(IngestStatus.HELLO_RECEIVED, outcome.status());
        assertEquals(before, relay.cueOf(A).orElseThrow().activity());
    }

    @Test
    void ingestSharePrefsUpdatesEffectivePermission() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.setPolicyBits(PolicyBits.ALL);

        byte[] prefs = C2SMessages.encode(new SharePrefs(PolicyBits.TYPING));
        var outcome = relay.ingest(A, prefs, 0L);

        assertEquals(IngestStatus.PREFS_UPDATED, outcome.status());
        assertEquals(PolicyBits.TYPING, relay.effectiveBits(A));
    }

    @Test
    void ingestCueUpdateAcceptedStoresNewState() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        var outcome = relay.ingest(A, cueUpdateBytes(Activity.IN_SCREEN, ScreenKind.CRAFTING, 0, 0), 0L);

        assertEquals(IngestStatus.ACCEPTED, outcome.status());
        assertEquals(Activity.IN_SCREEN, relay.cueOf(A).orElseThrow().activity());
        assertEquals(ScreenKind.CRAFTING, relay.cueOf(A).orElseThrow().screen());
    }

    @Test
    void effectiveBitsDefaultsToAllBeforeAnySharePrefs() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.setPolicyBits(PolicyBits.TYPING | PolicyBits.SCREENS);

        assertEquals(PolicyBits.TYPING | PolicyBits.SCREENS, relay.effectiveBits(A));
    }

    @Test
    void setPrefBitsOverridesWhateverTheClientLastSent() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.ingest(A, C2SMessages.encode(new SharePrefs(PolicyBits.ALL)), 0L);
        assertEquals(PolicyBits.ALL, relay.effectiveBits(A));

        relay.setPrefBits(A, PolicyBits.NONE); // e.g. adapter enforcing socialcues.share=false

        assertEquals(PolicyBits.NONE, relay.effectiveBits(A));
    }

    @Test
    void setPrefBitsIsNoOpForUnknownPlayer() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.setPrefBits(A, PolicyBits.ALL); // A never joined
        assertFalse(relay.isKnown(A));
    }

    @Test
    void setPolicyBitsRejectsOutOfRangeValues() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> relay.setPolicyBits(-1));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> relay.setPolicyBits(256));
    }

    // ---- rate limiting ------------------------------------------------------

    @Test
    void rateLimitAllowsExactlyFourUpdatesPerSecondThenRejects() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        for (int i = 0; i < 4; i++) {
            var outcome = relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, i, 0), i * 10L);
            assertEquals(IngestStatus.ACCEPTED, outcome.status(), "update " + i + " should be accepted");
        }

        var fifth = relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 5, 0), 45L);
        assertEquals(IngestStatus.RATE_LIMITED, fifth.status());
        assertEquals(1, fifth.violationStreak());
    }

    @Test
    void rateLimitWindowSlidesAfterOneSecond() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        for (int i = 0; i < 4; i++) {
            relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);
        }
        assertEquals(IngestStatus.RATE_LIMITED, relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L).status());

        // A full second later the oldest timestamps have aged out of the window.
        var accepted = relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 1000L);
        assertEquals(IngestStatus.ACCEPTED, accepted.status());
    }

    @Test
    void violationStreakAccumulatesAcrossDifferentViolationTypesAndResetsOnAccept() {
        FakeVisibility vis = new FakeVisibility();
        CueRelay relay = relay(vis);
        relay.join(A, 0L);

        var first = relay.ingest(A, new byte[ProtocolConstants.MAX_C2S_PACKET_SIZE + 1], 0L); // TOO_LARGE
        assertEquals(1, first.violationStreak());
        var second = relay.ingest(A, new byte[] {(byte) 0xEE}, 0L); // MALFORMED
        assertEquals(2, second.violationStreak());

        var accepted = relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);
        assertEquals(IngestStatus.ACCEPTED, accepted.status());
        assertEquals(0, accepted.violationStreak());

        var third = relay.ingest(A, new byte[] {(byte) 0xEE}, 0L);
        assertEquals(1, third.violationStreak(), "streak should have reset after the accepted message");
    }

    // ---- tick: permission masking + delta ----------------------------------

    @Test
    void tickAppliesEffectivePermissionMaskingNotRawClientState() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.setPolicyBits(PolicyBits.ALL & ~PolicyBits.TYPING); // server forbids sharing typing state
        relay.ingest(A, cueUpdateBytes(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 200, 0), 0L);

        TickResult result = relay.tick(0L);

        CueBatch batch = result.nearBatches().get(B);
        assertTrue(batch != null && !batch.entries().isEmpty());
        assertEquals(Activity.NORMAL, batch.entries().get(0).activity(),
                "server policy must win even though the client itself claims TYPING_CHAT");
    }

    @Test
    void tickOmitsUnchangedEntriesOnSubsequentTicks() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.IN_SCREEN, ScreenKind.INVENTORY, 0, 0), 0L);

        TickResult first = relay.tick(0L);
        assertTrue(first.nearBatches().containsKey(B));

        TickResult second = relay.tick(4L); // nothing changed since the first tick
        assertFalse(second.nearBatches().containsKey(B), "delta: unchanged state must not be resent");
    }

    @Test
    void tickResendsAfterAGenuineChange() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);
        relay.tick(0L);

        relay.ingest(A, cueUpdateBytes(Activity.AFK, ScreenKind.UNKNOWN, 0, 0), 4L);
        TickResult second = relay.tick(4L);

        assertTrue(second.nearBatches().containsKey(B));
        assertEquals(Activity.AFK, second.nearBatches().get(B).entries().get(0).activity());
    }

    // ---- visibility filter (DESIGN.md §10.3, the critical one) -------------

    @Test
    void tickNeverSendsATargetTheViewerCannotSee() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        vis.hide(B, A); // A is vanished from B's perspective
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 200, 0), 0L);

        TickResult result = relay.tick(0L);

        assertFalse(result.nearBatches().containsKey(B), "vanished player must never appear, not even masked");
        assertFalse(result.globalBatches().containsKey(B), "vanish must hide from the global/tab-list tier too");
    }

    @Test
    void tickDropsTargetWhenVisibilityIsRevokedMidSession() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.setPolicyBits(PolicyBits.ALL);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);
        relay.tick(0L);
        assertTrue(relay.tick(0L).nearBatches().isEmpty(), "sanity: nothing new on an immediate re-tick");

        vis.hide(B, A); // B's vanish plugin now hides A (e.g. A entered vanish)
        TickResult afterHide = relay.tick(4L);

        assertTrue(afterHide.nearDrops().containsKey(B));
        assertTrue(afterHide.nearDrops().get(B).ids().contains(A));
        assertFalse(afterHide.nearBatches().containsKey(B));
    }

    // ---- near tier: radius + world -----------------------------------------

    @Test
    void tickExcludesTargetsBeyondNearRadius() {
        FakeVisibility vis = new FakeVisibility();
        vis.place(A, new Position(0, 0, 0), "world");
        vis.place(B, new Position(1000, 0, 0), "world"); // far beyond default 48-block radius
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);

        TickResult result = relay.tick(0L);

        assertFalse(result.nearBatches().containsKey(B));
    }

    @Test
    void tickIncludesTargetsWithinNearRadius() {
        FakeVisibility vis = new FakeVisibility();
        vis.place(A, new Position(0, 0, 0), "world");
        vis.place(B, new Position(10, 0, 0), "world");
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);

        TickResult result = relay.tick(0L);

        assertTrue(result.nearBatches().containsKey(B));
    }

    @Test
    void tickExcludesTargetsInADifferentWorldRegardlessOfCoordinates() {
        FakeVisibility vis = new FakeVisibility();
        vis.place(A, new Position(0, 0, 0), "world");
        vis.place(B, new Position(0, 0, 0), "world_nether"); // identical coordinates, different world
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);

        TickResult result = relay.tick(0L);

        assertFalse(result.nearBatches().containsKey(B));
    }

    @Test
    void tickSkipsNearTierEntirelyWhenViewerPositionIsUnknownInsteadOfDroppingEveryone() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 10, 0), 0L);
        relay.tick(0L); // B now holds A in its near-tier delta state

        vis.forgetPosition(B); // e.g. B is mid-teleport/chunk-load and has no resolvable position this tick
        TickResult duringUnknownPosition = relay.tick(4L);

        assertFalse(duringUnknownPosition.nearDrops().containsKey(B),
                "an unresolved viewer position must not be treated as \"nobody is near\"");
        assertFalse(duringUnknownPosition.nearBatches().containsKey(B));
    }

    // ---- global tier: gating + AFK gating + throttle -----------------------

    @Test
    void globalTierRequiresGlobalTierBitEvenWhenNearTierWorks() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.setPolicyBits(PolicyBits.ALL & ~PolicyBits.GLOBAL_TIER);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);

        TickResult result = relay.tick(0L);

        assertTrue(result.nearBatches().containsKey(B));
        assertFalse(result.globalBatches().containsKey(B));
    }

    @Test
    void globalTierHidesAfkWithoutGlobalAfkBitButNearTierStillShowsIt() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.setPolicyBits(PolicyBits.ALL & ~PolicyBits.GLOBAL_AFK); // GLOBAL_TIER stays on, GLOBAL_AFK off
        relay.ingest(A, cueUpdateBytes(Activity.AFK, ScreenKind.UNKNOWN, 0, CueFlags.SLEEPY), 0L);

        TickResult result = relay.tick(0L);

        assertEquals(Activity.AFK, result.nearBatches().get(B).entries().get(0).activity());
        assertEquals(Activity.NORMAL, result.globalBatches().get(B).entries().get(0).activity());
    }

    @Test
    void globalTierThrottlesRapidChangesToAtMostOncePerSecond() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0), 0L);
        TickResult first = relay.tick(0L);
        assertEquals(Activity.NORMAL, first.globalBatches().get(B).entries().get(0).activity());

        relay.ingest(A, cueUpdateBytes(Activity.AFK, ScreenKind.UNKNOWN, 0, 0), 200L);
        TickResult tooSoon = relay.tick(200L);
        assertFalse(tooSoon.globalBatches().containsKey(B),
                "change happened only 200ms after the last global broadcast; must be throttled to <=1/s");

        TickResult afterWindow = relay.tick(1000L);
        assertTrue(afterWindow.globalBatches().containsKey(B));
        assertEquals(Activity.AFK, afterWindow.globalBatches().get(B).entries().get(0).activity());
    }

    @Test
    void globalTierRespectsMutedSelfLikeNearTierDoes() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.TYPING_CHAT, ScreenKind.UNKNOWN, 200, CueFlags.MUTED_SELF), 0L);

        TickResult result = relay.tick(0L);

        assertEquals(Activity.NORMAL, result.globalBatches().get(B).entries().get(0).activity());
    }

    /**
     * DESIGN.md §5 (P5 hand-test bug, 2026-08-09): the relay labelling each
     * batch is the server half of the tier fix. {@code
     * core.client.RemoteCueStore} routes an incoming batch purely by {@link
     * CueBatch#tier()}, so a near batch mislabelled {@code GLOBAL} — or the
     * reverse — silently reintroduces exactly the coarse-overwrites-detailed
     * bug the client's two maps exist to prevent, and nothing else on the
     * wire would catch it. The near entry deliberately carries a real
     * {@link ScreenKind} so the two batches differ in content as well as
     * label, matching the situation the bug actually appeared in.
     */
    @Test
    void eachTiersBatchesAreLabelledWithTheirOwnTier() {
        FakeVisibility vis = new FakeVisibility();
        placeTogether(vis, A, B);
        CueRelay relay = relay(vis);
        relay.join(A, 0L);
        relay.join(B, 0L);
        relay.ingest(A, cueUpdateBytes(Activity.IN_SCREEN, ScreenKind.FURNACE, 0, 0), 0L);

        TickResult result = relay.tick(0L);

        assertEquals(CueTier.NEAR, result.nearBatches().get(B).tier());
        assertEquals(CueTier.GLOBAL, result.globalBatches().get(B).tier());
        // The premise of the whole fix: same player, same tick, two different
        // detail levels — FURNACE near, UNKNOWN global (applyGlobalCoarse).
        assertEquals(ScreenKind.FURNACE, result.nearBatches().get(B).entries().get(0).screenKind());
        assertEquals(ScreenKind.UNKNOWN, result.globalBatches().get(B).entries().get(0).screenKind());
    }
}
