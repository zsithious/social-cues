package dev.zsithious.socialcues.core.relay;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RelayConfigTest {

    @Test
    void defaultsMatchDesignDocNumbers() {
        RelayConfig config = RelayConfig.defaults();
        org.junit.jupiter.api.Assertions.assertEquals(48.0, config.nearRadius());
        org.junit.jupiter.api.Assertions.assertEquals(4, config.updateIntervalTicks());
        org.junit.jupiter.api.Assertions.assertEquals(4, config.maxUpdatesPerSecond());
        org.junit.jupiter.api.Assertions.assertEquals(1000L, config.globalBroadcastMinIntervalMs());
        org.junit.jupiter.api.Assertions.assertEquals(64, config.maxPacketSize());
    }

    @Test
    void rejectsNonPositiveNearRadius() {
        assertThrows(IllegalArgumentException.class, () -> new RelayConfig(0, 4, 4, 1000L, 64));
        assertThrows(IllegalArgumentException.class, () -> new RelayConfig(-1, 4, 4, 1000L, 64));
    }

    @Test
    void rejectsNonPositiveUpdateInterval() {
        assertThrows(IllegalArgumentException.class, () -> new RelayConfig(48, 0, 4, 1000L, 64));
    }

    @Test
    void rejectsNonPositiveRateLimit() {
        assertThrows(IllegalArgumentException.class, () -> new RelayConfig(48, 4, 0, 1000L, 64));
    }

    @Test
    void rejectsNegativeGlobalBroadcastInterval() {
        assertThrows(IllegalArgumentException.class, () -> new RelayConfig(48, 4, 4, -1L, 64));
    }

    @Test
    void rejectsNonPositivePacketSize() {
        assertThrows(IllegalArgumentException.class, () -> new RelayConfig(48, 4, 4, 1000L, 0));
    }
}
