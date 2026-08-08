package dev.zsithious.socialcues.core.handshake;

/**
 * DESIGN.md §5 "El sıkışma" — the client-side handshake lifecycle.
 *
 * <pre>
 * DORMANT --(channel announced, ClientHello sent)--&gt; HELLO_SENT
 * HELLO_SENT --(ServerHello received, version OK)--&gt; ACTIVE
 * HELLO_SENT --(10s elapsed, no ServerHello)--&gt; DORMANT
 * (any state) --(disconnect)--&gt; DORMANT
 * </pre>
 */
public enum HandshakeState {
    /** No packets sent or accepted. Either never started, timed out, or a version mismatch was seen. */
    DORMANT,
    /** ClientHello was sent; waiting (up to the timeout) for a ServerHello reply. */
    HELLO_SENT,
    /** A compatible ServerHello was received. */
    ACTIVE
}
