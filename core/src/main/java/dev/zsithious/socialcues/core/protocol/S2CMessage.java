package dev.zsithious.socialcues.core.protocol;

/** DESIGN.md §5 — server-to-client protocol v1 messages. */
public sealed interface S2CMessage permits ServerHello, CueBatch, CueDrop {

    /** First byte on the wire, per message body. */
    int typeId();

    void encode(ByteWriter writer);
}
