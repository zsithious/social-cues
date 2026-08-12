package dev.zsithious.socialcues.core.client;

/**
 * DESIGN.md §6 "Konuşma" / §14 P8 — turns the raw, frame-by-frame "is the
 * microphone transmitting right now" boolean that the Simple Voice Chat
 * bridge reads into the steadier "this player is in a conversation" signal
 * {@link dev.zsithious.socialcues.core.state.Activity#SPEAKING} is supposed
 * to mean.
 *
 * <p><b>Why a hold window rather than the raw boolean.</b> Voice transmission
 * is not continuous: it stops in every gap between two sentences, and with
 * voice activation it stops on every breath. Feeding that straight to Layer 1
 * would flicker the nametag icon several times per sentence — the cue would
 * be strictly worse than showing nothing, because a blinking icon reads as
 * "something is wrong" rather than "this person is talking". So the last
 * moment of transmission is remembered and {@code SPEAKING} is held for
 * {@link #DEFAULT_HOLD_MS} past it.
 *
 * <p>The window is deliberately a constant rather than a config field: it is
 * not a matter of taste but of what a sentence gap physically is, and every
 * value in the 1.5–3s range behaves the same to a viewer. Two seconds covers
 * ordinary sentence gaps and short pauses for thought while still dropping
 * the cue promptly once someone actually stops talking.
 *
 * <p>Pure Java, no Minecraft and no Simple Voice Chat types — like every
 * other decision in {@code core.client}, this is unit tested on its own
 * (see {@code VoiceActivityTrackerTest}) and the Minecraft side only feeds
 * it. Not thread safe; it is only ever touched from the client tick.
 */
public final class VoiceActivityTracker {

    /** See the class Javadoc for why this is a constant and why it is 2s. */
    public static final long DEFAULT_HOLD_MS = 2000L;

    private final long holdMs;

    /**
     * Tracked separately from {@link #lastTransmittingAtMs} rather than
     * encoding "never" as a sentinel timestamp: every sentinel choice either
     * overflows on subtraction ({@code Long.MIN_VALUE}) or is a real,
     * reachable epoch value (0).
     */
    private boolean transmittedBefore;
    private long lastTransmittingAtMs;

    public VoiceActivityTracker() {
        this(DEFAULT_HOLD_MS);
    }

    public VoiceActivityTracker(long holdMs) {
        if (holdMs < 0) {
            throw new IllegalArgumentException("holdMs must not be negative: " + holdMs);
        }
        this.holdMs = holdMs;
    }

    /**
     * Feeds one sample and answers whether the player should be reported as
     * speaking at {@code nowMs}.
     *
     * @param transmitting whether the microphone is transmitting this instant
     * @param nowMs        wall clock, same source as the rest of the capture path
     */
    public boolean update(boolean transmitting, long nowMs) {
        if (transmitting) {
            transmittedBefore = true;
            lastTransmittingAtMs = nowMs;
            return true;
        }
        return isSpeaking(nowMs);
    }

    /** Whether the hold window from the last transmission is still open. */
    public boolean isSpeaking(long nowMs) {
        if (!transmittedBefore) {
            return false;
        }
        long since = nowMs - lastTransmittingAtMs;
        if (since < 0) {
            // The wall clock moved backwards (NTP step). Treat the sample as
            // current rather than letting a negative age read as "inside the
            // window forever"; the next transmitting sample re-anchors it.
            lastTransmittingAtMs = nowMs;
            return true;
        }
        return since < holdMs;
    }

    /**
     * Forgets any held transmission. Called when the voice bridge detaches
     * (Simple Voice Chat disconnected, or the probe was disabled after a
     * failure) so a stale hold cannot outlive the thing that produced it.
     */
    public void reset() {
        transmittedBefore = false;
        lastTransmittingAtMs = 0L;
    }
}
