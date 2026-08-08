package dev.zsithious.socialcues.core.client;

import dev.zsithious.socialcues.core.policy.PolicyBits;

/**
 * DESIGN.md §9 / P3 task note: "SharePrefs: şimdilik hepsi açık varsayılan;
 * ama değeri tek bir enjekte edilebilir yerden gelsin ki P6'da config UI
 * oraya bağlansın." This is that single seam: "what does the local player
 * currently agree to share" (DESIGN.md §5's {@code SharePrefs.prefBits}
 * bit layout, see {@link PolicyBits}). P3 wires exactly one implementation,
 * {@link #allEnabled()}, into one field on the Minecraft side; a future
 * config-backed implementation (Cloth Config, P6) is a drop-in replacement
 * for that field without touching anything that reads through this
 * interface.
 */
@FunctionalInterface
public interface SharePrefsSource {

    /** Current {@code prefBits} — see {@link PolicyBits} for the bit layout. */
    int prefBits();

    /** P3's default: nothing is user-configurable yet, so everything the client is capable of sharing is shared. */
    static SharePrefsSource allEnabled() {
        return () -> PolicyBits.ALL;
    }
}
