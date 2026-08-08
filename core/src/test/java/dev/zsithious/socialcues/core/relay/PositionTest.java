package dev.zsithious.socialcues.core.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void distanceToIsEuclidean() {
        Position a = new Position(0, 0, 0);
        Position b = new Position(3, 4, 0);
        assertEquals(5.0, a.distanceTo(b), 1e-9);
    }

    @Test
    void distanceToSelfIsZero() {
        Position a = new Position(12.5, -3.0, 7.0);
        assertEquals(0.0, a.distanceTo(a), 1e-9);
    }

    @Test
    void distanceToIsSymmetric() {
        Position a = new Position(1, 2, 3);
        Position b = new Position(-4, 5, -6);
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-9);
    }
}
