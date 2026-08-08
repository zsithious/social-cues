package dev.zsithious.socialcues.core.protocol;

/** DESIGN.md §5 — client-to-server protocol v1 messages. */
public sealed interface C2SMessage permits ClientHello, CueUpdate, SharePrefs {

    /** First byte on the wire, per message body. */
    int typeId();

    void encode(ByteWriter writer);
}
