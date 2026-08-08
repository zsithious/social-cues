package dev.zsithious.socialcues.core.protocol;

/** DESIGN.md §5 — protocol-wide constants. */
public final class ProtocolConstants {

    /** Single custom-payload channel for protocol v1. Bumped to :v2 on a breaking change. */
    public static final String CHANNEL = "socialcues:v1";

    public static final int VERSION = 1;

    /** ClientHello.modVersion cap, in characters. */
    public static final int MAX_MOD_VERSION_LENGTH = 32;

    /** DESIGN.md §5 — "sunucu 64 bayt üstünü reddeder" for any C2S packet. */
    public static final int MAX_C2S_PACKET_SIZE = 64;

    private ProtocolConstants() {
    }
}
