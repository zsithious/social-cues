package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ClientHelloTest {

    @Test
    void roundTrips() {
        ClientHello original = new ClientHello(1, "0.1.0", 0b101);
        ByteWriter writer = new ByteWriter();
        original.encode(writer);
        ClientHello decoded = ClientHello.decode(new ByteReader(writer.toByteArray()));
        assertEquals(original, decoded);
    }

    @Test
    void roundTripsThroughC2SMessagesDispatcher() {
        ClientHello original = new ClientHello(1, "0.1.0", 0);
        byte[] encoded = C2SMessages.encode(original);
        assertEquals(ClientHello.TYPE_ID, encoded[0] & 0xFF);
        assertEquals(original, C2SMessages.decode(encoded));
    }

    @Test
    void constructorRejectsModVersionOverMax() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClientHello(1, "x".repeat(33), 0));
    }

    @Test
    void decodeRejectsTruncatedBody() {
        // Only the protoVersion varint, nothing else.
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> ClientHello.decode(reader));
    }

    @Test
    void decodeRejectsModVersionOverMax() {
        // Build a wire body directly, bypassing the constructor check, to
        // simulate a hostile/buggy peer sending an over-length string.
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        writer.writeString("y".repeat(40), 1000);
        writer.writeVarInt(0);
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> ClientHello.decode(reader));
    }

    @Test
    void allFieldsAtBoundaryValuesRoundTrip() {
        ClientHello boundary = new ClientHello(Integer.MAX_VALUE, "x".repeat(32), Integer.MAX_VALUE);
        byte[] encoded = C2SMessages.encode(boundary);
        assertEquals(boundary, C2SMessages.decode(encoded));
    }
}
