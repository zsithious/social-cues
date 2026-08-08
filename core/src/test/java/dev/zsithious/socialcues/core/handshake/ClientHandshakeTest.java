package dev.zsithious.socialcues.core.handshake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Exercises the dormant → hello sent → active → timeout → dormant lifecycle
 * with a fake clock and a capturing logger, entirely without Minecraft — the
 * MC-independent proof of the handshake logic required by the P1 task.
 */
class ClientHandshakeTest {

    private static final int PROTO_VERSION = 1;

    /** Mutable fake clock so tests control elapsed time exactly, no sleeping. */
    private static final class FakeClock {
        long now;

        long get() {
            return now;
        }
    }

    private static final class CapturingLog implements ClientHandshake.Log {
        final List<String> messages = new ArrayList<>();

        @Override
        public void info(String message) {
            messages.add(message);
        }
    }

    private ClientHandshake newHandshake(FakeClock clock, CapturingLog log, long timeoutMillis) {
        return new ClientHandshake(clock::get, timeoutMillis, log);
    }

    @Test
    void startsDormant() {
        ClientHandshake handshake = newHandshake(new FakeClock(), new CapturingLog(), 10_000L);
        assertEquals(HandshakeState.DORMANT, handshake.state());
        assertFalse(handshake.isActive());
    }

    @Test
    void channelNotAnnouncedStaysDormantAndLogsOnce() {
        CapturingLog log = new CapturingLog();
        ClientHandshake handshake = newHandshake(new FakeClock(), log, 10_000L);

        handshake.onChannelNotAnnounced();

        assertEquals(HandshakeState.DORMANT, handshake.state());
        assertEquals(1, log.messages.size());
        assertTrue(log.messages.get(0).contains("did not announce"));
    }

    @Test
    void helloSentMovesToHelloSentState() {
        FakeClock clock = new FakeClock();
        ClientHandshake handshake = newHandshake(clock, new CapturingLog(), 10_000L);

        handshake.onHelloSent();

        assertEquals(HandshakeState.HELLO_SENT, handshake.state());
        assertFalse(handshake.isActive());
    }

    @Test
    void serverHelloAfterHelloSentBecomesActive() {
        FakeClock clock = new FakeClock();
        CapturingLog log = new CapturingLog();
        ClientHandshake handshake = newHandshake(clock, log, 10_000L);

        handshake.onHelloSent();
        handshake.onServerHelloReceived(PROTO_VERSION, PROTO_VERSION);

        assertEquals(HandshakeState.ACTIVE, handshake.state());
        assertTrue(handshake.isActive());
        assertTrue(log.messages.get(log.messages.size() - 1).contains("handshake complete"));
    }

    @Test
    void serverHelloIsSingleSourceOfTruthEvenWithoutPriorHello() {
        // DESIGN.md §5: ServerHello is the single source of truth for
        // becoming active, independent of what the client thinks it sent.
        ClientHandshake handshake = newHandshake(new FakeClock(), new CapturingLog(), 10_000L);

        handshake.onServerHelloReceived(PROTO_VERSION, PROTO_VERSION);

        assertEquals(HandshakeState.ACTIVE, handshake.state());
    }

    @Test
    void mismatchedProtocolVersionStaysDormant() {
        FakeClock clock = new FakeClock();
        CapturingLog log = new CapturingLog();
        ClientHandshake handshake = newHandshake(clock, log, 10_000L);

        handshake.onHelloSent();
        handshake.onServerHelloReceived(2, PROTO_VERSION);

        assertEquals(HandshakeState.DORMANT, handshake.state());
        assertTrue(log.messages.get(log.messages.size() - 1).contains("version mismatch"));
    }

    @Test
    void tickBeforeTimeoutStaysHelloSent() {
        FakeClock clock = new FakeClock();
        ClientHandshake handshake = newHandshake(clock, new CapturingLog(), 10_000L);

        handshake.onHelloSent();
        clock.now = 9_999L;
        handshake.tick();

        assertEquals(HandshakeState.HELLO_SENT, handshake.state());
    }

    @Test
    void tickAtOrAfterTimeoutReturnsToDormant() {
        FakeClock clock = new FakeClock();
        CapturingLog log = new CapturingLog();
        ClientHandshake handshake = newHandshake(clock, log, 10_000L);

        handshake.onHelloSent();
        clock.now = 10_000L;
        handshake.tick();

        assertEquals(HandshakeState.DORMANT, handshake.state());
        assertTrue(log.messages.get(log.messages.size() - 1).contains("timed out"));
    }

    @Test
    void tickDoesNothingWhenDormant() {
        FakeClock clock = new FakeClock();
        CapturingLog log = new CapturingLog();
        ClientHandshake handshake = newHandshake(clock, log, 10_000L);

        clock.now = 999_999L;
        handshake.tick();

        assertEquals(HandshakeState.DORMANT, handshake.state());
        assertTrue(log.messages.isEmpty());
    }

    @Test
    void tickDoesNotDisturbActiveStateAfterLongElapsedTime() {
        FakeClock clock = new FakeClock();
        ClientHandshake handshake = newHandshake(clock, new CapturingLog(), 10_000L);

        handshake.onHelloSent();
        handshake.onServerHelloReceived(PROTO_VERSION, PROTO_VERSION);
        clock.now = 1_000_000L;
        handshake.tick();

        assertEquals(HandshakeState.ACTIVE, handshake.state());
    }

    @Test
    void resetFromActiveGoesDormant() {
        ClientHandshake handshake = newHandshake(new FakeClock(), new CapturingLog(), 10_000L);

        handshake.onHelloSent();
        handshake.onServerHelloReceived(PROTO_VERSION, PROTO_VERSION);
        handshake.reset();

        assertEquals(HandshakeState.DORMANT, handshake.state());
    }

    @Test
    void resetFromHelloSentGoesDormant() {
        ClientHandshake handshake = newHandshake(new FakeClock(), new CapturingLog(), 10_000L);

        handshake.onHelloSent();
        handshake.reset();

        assertEquals(HandshakeState.DORMANT, handshake.state());
    }

    @Test
    void resetWhenAlreadyDormantDoesNotLog() {
        CapturingLog log = new CapturingLog();
        ClientHandshake handshake = newHandshake(new FakeClock(), log, 10_000L);

        handshake.reset();

        assertEquals(HandshakeState.DORMANT, handshake.state());
        assertTrue(log.messages.isEmpty());
    }

    @Test
    void fullLifecycleDormantToHelloSentToActiveToTimeoutToDormant() {
        FakeClock clock = new FakeClock();
        ClientHandshake handshake = newHandshake(clock, new CapturingLog(), 10_000L);

        assertEquals(HandshakeState.DORMANT, handshake.state());

        handshake.onHelloSent();
        assertEquals(HandshakeState.HELLO_SENT, handshake.state());

        handshake.onServerHelloReceived(PROTO_VERSION, PROTO_VERSION);
        assertEquals(HandshakeState.ACTIVE, handshake.state());

        // A fresh connection cycle: disconnect, reconnect, this time no reply ever arrives.
        handshake.reset();
        assertEquals(HandshakeState.DORMANT, handshake.state());

        handshake.onHelloSent();
        clock.now += 10_000L;
        handshake.tick();
        assertEquals(HandshakeState.DORMANT, handshake.state());
    }
}
