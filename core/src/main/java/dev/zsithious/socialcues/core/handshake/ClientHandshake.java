package dev.zsithious.socialcues.core.handshake;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Client-side dormant/active state machine for DESIGN.md §5's handshake.
 *
 * <p>Deliberately pure Java (no Minecraft/Fabric imports) so the transition
 * logic — dormant → hello sent → active → timeout → dormant — can be
 * verified with JUnit and a fake clock, without booting Minecraft. The
 * Fabric client wiring ({@code mcshared.network.ClientHandshakeNetworking})
 * is a thin adapter around this class: it decides *when* to call these
 * methods (join, packet received, tick, disconnect) and performs the actual
 * network I/O, but every transition decision lives here, in one place, per
 * DESIGN.md's "tek doğruluk kaynağı" (single source of truth) principle.
 */
public final class ClientHandshake {

    /** DESIGN.md §5: "İstemci ... ServerHello gelmediyse N=10s sonra DORMANT". */
    public static final long DEFAULT_TIMEOUT_MILLIS = 10_000L;

    /** Minimal logging seam so tests can assert on messages without depending on java.util.logging. */
    @FunctionalInterface
    public interface Log {
        void info(String message);
    }

    private final LongSupplier clock;
    private final long timeoutMillis;
    private final Log log;

    private HandshakeState state = HandshakeState.DORMANT;
    private long helloSentAtMillis;

    public ClientHandshake(LongSupplier clock, long timeoutMillis, Log log) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeoutMillis = timeoutMillis;
        this.log = Objects.requireNonNull(log, "log");
    }

    public HandshakeState state() {
        return state;
    }

    public boolean isActive() {
        return state == HandshakeState.ACTIVE;
    }

    /**
     * The server never announced the channel (the pre-filter, e.g. Fabric's
     * {@code canSend()}, was false at join time). We must not send a single
     * packet to a server that doesn't support this protocol.
     */
    public void onChannelNotAnnounced() {
        state = HandshakeState.DORMANT;
        log.info("server did not announce the socialcues channel, staying dormant");
    }

    /** Caller has just sent ClientHello as a side effect; start waiting for the reply. */
    public void onHelloSent() {
        state = HandshakeState.HELLO_SENT;
        helloSentAtMillis = clock.getAsLong();
        log.info("handshake started: ClientHello sent, waiting for ServerHello");
    }

    /**
     * A ServerHello arrived. Per DESIGN.md §5 this is the single source of
     * truth for becoming active: it takes effect even from DORMANT (e.g. an
     * unsolicited ServerHello), not only after {@link #onHelloSent()}.
     *
     * @param serverProtoVersion   {@code protoVersion} carried by the ServerHello
     * @param expectedProtoVersion the client's own {@code ProtocolConstants.VERSION}
     */
    public void onServerHelloReceived(int serverProtoVersion, int expectedProtoVersion) {
        if (serverProtoVersion != expectedProtoVersion) {
            state = HandshakeState.DORMANT;
            log.info("protocol version mismatch: server=" + serverProtoVersion
                    + " client=" + expectedProtoVersion + ", staying dormant");
            return;
        }
        boolean wasActive = state == HandshakeState.ACTIVE;
        state = HandshakeState.ACTIVE;
        if (!wasActive) {
            log.info("handshake complete: ServerHello received, active");
        }
    }

    /**
     * Drive the timeout. Call once per regular pulse (e.g. every client
     * tick); a cheap no-op unless we're actually waiting for a reply.
     */
    public void tick() {
        if (state != HandshakeState.HELLO_SENT) {
            return;
        }
        if (clock.getAsLong() - helloSentAtMillis >= timeoutMillis) {
            state = HandshakeState.DORMANT;
            log.info("handshake timed out after " + timeoutMillis + "ms with no ServerHello, staying dormant");
        }
    }

    /** Connection dropped (or a fresh join is starting): forget everything, back to square one. */
    public void reset() {
        boolean wasDormant = state == HandshakeState.DORMANT;
        state = HandshakeState.DORMANT;
        if (!wasDormant) {
            log.info("connection reset, handshake state cleared");
        }
    }
}
