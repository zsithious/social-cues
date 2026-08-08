package dev.zsithious.socialcues.core.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SharePrefsTest {

    @Test
    void roundTrips() {
        SharePrefs original = new SharePrefs(0b1010);
        byte[] encoded = C2SMessages.encode(original);
        assertEquals(SharePrefs.TYPE_ID, encoded[0] & 0xFF);
        assertEquals(original, C2SMessages.decode(encoded));
    }

    @Test
    void boundaryValuesRoundTrip() {
        assertEquals(new SharePrefs(0), C2SMessages.decode(C2SMessages.encode(new SharePrefs(0))));
        assertEquals(new SharePrefs(255), C2SMessages.decode(C2SMessages.encode(new SharePrefs(255))));
    }

    @Test
    void constructorRejectsOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new SharePrefs(256));
        assertThrows(IllegalArgumentException.class, () -> new SharePrefs(-1));
    }

    @Test
    void decodeRejectsEmptyBody() {
        ByteReader reader = new ByteReader(new byte[0]);
        assertThrows(ProtocolDecodeException.class, () -> SharePrefs.decode(reader));
    }
}
