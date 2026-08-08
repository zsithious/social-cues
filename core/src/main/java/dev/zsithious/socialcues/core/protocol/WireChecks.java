package dev.zsithious.socialcues.core.protocol;

/** Small shared validation used by the message records' compact constructors. */
final class WireChecks {

    private WireChecks() {
    }

    static void requireUnsignedByte(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " must be in range 0-255, was " + value);
        }
    }

    static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be >= 0, was " + value);
        }
    }
}
