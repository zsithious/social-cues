package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ServerHelloTest {

    @Test
    void roundTrips() {
        ServerHello original = new ServerHello(1, 0b11, 6000, 4, 48);
        byte[] encoded = S2CMessages.encode(original);
        assertEquals(ServerHello.TYPE_ID, encoded[0] & 0xFF);
        assertEquals(original, S2CMessages.decode(encoded));
    }

    @Test
    void allZeroAndMaxBoundaryValuesRoundTrip() {
        ServerHello zero = new ServerHello(0, 0, 0, 0, 0);
        assertEquals(zero, S2CMessages.decode(S2CMessages.encode(zero)));

        ServerHello max = new ServerHello(Integer.MAX_VALUE, Integer.MAX_VALUE,
                Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(max, S2CMessages.decode(S2CMessages.encode(max)));
    }

    @Test
    void constructorRejectsNegativeFields() {
        assertThrows(IllegalArgumentException.class, () -> new ServerHello(-1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ServerHello(0, -1, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ServerHello(0, 0, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ServerHello(0, 0, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ServerHello(0, 0, 0, 0, -1));
    }

    @Test
    void decodeRejectsShortBody() {
        ByteWriter writer = new ByteWriter();
        writer.writeVarInt(1);
        writer.writeVarInt(0);
        // missing idleThresholdTicks, updateIntervalTicks, nearRadius
        ByteReader reader = new ByteReader(writer.toByteArray());
        assertThrows(ProtocolDecodeException.class, () -> ServerHello.decode(reader));
    }
}
