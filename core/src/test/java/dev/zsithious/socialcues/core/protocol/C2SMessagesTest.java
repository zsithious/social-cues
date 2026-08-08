package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class C2SMessagesTest {

    @Test
    void decodeRejectsUnknownTypeId() {
        byte[] bogus = { (byte) 0x7F };
        assertThrows(ProtocolDecodeException.class, () -> C2SMessages.decode(bogus));
    }

    @Test
    void decodeRejectsEmptyInput() {
        assertThrows(ProtocolDecodeException.class, () -> C2SMessages.decode(new byte[0]));
    }

    @Test
    void decodeRejectsS2CTypeIdOnC2SChannel() {
        // 0x81 is a valid S2C type id (ServerHello) but not a C2S one.
        byte[] bogus = { (byte) 0x81 };
        assertThrows(ProtocolDecodeException.class, () -> C2SMessages.decode(bogus));
    }
}
