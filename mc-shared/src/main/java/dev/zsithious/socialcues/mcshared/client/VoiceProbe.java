package dev.zsithious.socialcues.mcshared.client;

/**
 * DESIGN.md §6 "Konuşma" / §14 P8 — the one seam between the client capture
 * loop and the optional Simple Voice Chat integration.
 *
 * <p><b>Why an interface instead of calling the voice API directly.</b>
 * Simple Voice Chat is a <i>soft</i> dependency: its API is on the compile
 * classpath only and is absent at runtime for most players. Any class that
 * names {@code de.maxhenkel.voicechat.*} therefore cannot be loaded unless
 * the mod is installed, and {@link ClientCueCapture} has to load for
 * everyone. So the capture loop only ever sees this interface, which names
 * nothing from that API; the single implementation lives in
 * {@code integrations/voicechat/} and is instantiated exclusively from the
 * {@code voicechat} entrypoint, which by construction only ever runs when
 * Simple Voice Chat is present to invoke it.
 *
 * <p>This is the same shape as {@code core.client.SharePrefsSource}, which
 * lets the config UI replace the capture loop's idea of "what may I share"
 * without the capture loop knowing Cloth exists — see
 * {@link ClientCueCapture#setSharePrefs}.
 *
 * <p><b>The licensing reason it is worth the indirection</b> (DESIGN.md §13,
 * {@code CLEANROOM.md}): Simple Voice Chat is All Rights Reserved, including
 * its API module. Nothing of it is redistributed — {@code compileOnly} keeps
 * it out of every jar we ship — and confining every mention of it to one
 * directory behind one interface keeps that claim mechanically checkable
 * rather than a matter of trust.
 */
@FunctionalInterface
public interface VoiceProbe {

    /**
     * A probe for when no voice mod is installed. Answers "not transmitting"
     * forever, which collapses the whole voice path to a constant: no
     * {@code SPEAKING} is ever produced and no voice API is ever touched.
     */
    VoiceProbe NONE = () -> false;

    /**
     * Whether the local player's microphone is transmitting at this instant.
     *
     * <p>Called at most once per client tick, and only while the server
     * policy and the player's own preferences both permit sharing voice
     * state — DESIGN.md §6's "a signal the policy forbids is never even
     * measured" applies here literally: when the bit is off this method is
     * not called at all.
     *
     * <p>Raw and instantaneous by contract. Smoothing a transmission that
     * stops between two sentences into a steady cue is
     * {@code core.client.VoiceActivityTracker}'s job, not the
     * implementation's.
     */
    boolean transmitting();
}
