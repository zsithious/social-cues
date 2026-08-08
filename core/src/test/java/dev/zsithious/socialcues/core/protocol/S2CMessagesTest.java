package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class S2CMessagesTest {

    @Test
    void decodeRejectsUnknownTypeId() {
        byte[] bogus = { (byte) 0xFE };
        assertThrows(ProtocolDecodeException.class, () -> S2CMessages.decode(bogus));
    }

    @Test
    void decodeRejectsEmptyInput() {
        assertThrows(ProtocolDecodeException.class, () -> S2CMessages.decode(new byte[0]));
    }

    @Test
    void decodeRejectsC2STypeIdOnS2CChannel() {
        // 0x01 is a valid C2S type id (ClientHello) but not an S2C one.
        byte[] bogus = { (byte) 0x01 };
        assertThrows(ProtocolDecodeException.class, () -> S2CMessages.decode(bogus));
    }
}
