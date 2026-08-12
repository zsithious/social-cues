package dev.zsithious.socialcues.voicechat;

import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.mcshared.client.ClientCueCapture;
import dev.zsithious.socialcues.mcshared.client.VoiceProbe;

import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;

/**
 * DESIGN.md §6 "Konuşma" / §14 P8 — the optional Simple Voice Chat
 * integration, and the <b>only</b> class in this repository that names a
 * {@code de.maxhenkel.voicechat.*} type.
 *
 * <p><b>How it is reached.</b> {@code fabric.mod.json} declares this class
 * under the {@code voicechat} entrypoint. That key means nothing to Fabric
 * itself — only Simple Voice Chat ever reads it — so on the overwhelming
 * majority of installs, where the mod is absent, this class is never loaded,
 * never linked, and never verified. That is the whole reason the seam is
 * shaped this way: {@code compileOnly} means these imports resolve to nothing
 * at runtime, so a class carrying them must not sit on any path the base mod
 * walks.
 *
 * <p><b>Why the connection event rather than {@code initialize(VoicechatApi)}.</b>
 * {@link VoicechatPlugin#initialize} is the shared entry point for both sides
 * and hands over the neutral {@code VoicechatApi} supertype, which on a
 * listen server is genuinely ambiguous. {@link ClientVoicechatConnectionEvent}
 * has neither problem: it is a client event, its {@code getVoicechat()} is
 * typed {@link VoicechatClientApi} outright, and it reports both edges — so
 * the probe is installed exactly when there is a voice connection to read and
 * withdrawn the moment there is not.
 *
 * <p><b>What it does.</b> Nothing but hand {@link ClientCueCapture} a
 * {@link VoiceProbe} closing over that API, and take it back on disconnect.
 * All of the actual behaviour — the hold window that makes the signal steady,
 * the policy gate that decides whether to ask at all, where {@code SPEAKING}
 * sits against typing and AFK — lives on the other side of that interface, in
 * code that is unit tested and compiled for every player.
 *
 * <p><b>Licensing</b> (DESIGN.md §13, {@code CLEANROOM.md}): Simple Voice
 * Chat is All Rights Reserved, its API module included — there is no separate
 * licence under {@code api/}, the repository-wide one covers it. Its author
 * publishes that API to Maven and documents it for addon authors, so calling
 * it is its intended use; but nothing of it is redistributed here. The
 * dependency is {@code compileOnly}, so no Simple Voice Chat byte reaches any
 * jar this project ships, and confining every mention of it to this one file
 * keeps that checkable at a glance instead of on trust.
 */
public final class SocialCuesVoicechatPlugin implements VoicechatPlugin {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /**
     * Must be unique among voice chat plugins; matches the mod id
     * ({@code gradle.properties}'s {@code mod_id}) for the same reason the
     * network channel does.
     */
    @Override
    public String getPluginId() {
        return "socialcues";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatConnectionEvent.class,
                SocialCuesVoicechatPlugin::onConnectionChanged);
    }

    private static void onConnectionChanged(ClientVoicechatConnectionEvent event) {
        if (!event.isConnected()) {
            // Withdraw rather than leave a probe pointing at a dead connection.
            // Also clears any hold still counting down, so the cue cannot
            // linger past the thing that produced it.
            ClientCueCapture.setVoiceProbe(VoiceProbe.NONE);
            return;
        }

        VoicechatClientApi clientApi = event.getVoicechat();
        ClientCueCapture.setVoiceProbe(() -> isTransmitting(clientApi));
        LOGGER.log(Level.INFO, "socialcues: Simple Voice Chat connected — voice cues enabled.");
    }

    /**
     * The one question this whole integration exists to answer.
     *
     * <p>{@code isMuted()} is checked first and separately from
     * {@code isTalking()}. Muting yourself is a deliberate "do not broadcast
     * me" action, and a mod whose entire pitch is that it tells other people
     * what you are doing (DESIGN.md §10) has no business reporting the one
     * state the player took an explicit step to suppress. In practice a muted
     * microphone should not report talking anyway — but "should not" is not a
     * guarantee worth resting a privacy claim on, and the check costs nothing.
     *
     * <p>{@code isTalking()} with no argument is the local player, and is true
     * only while audio is actually being transmitted: it goes false in every
     * gap between two sentences. Smoothing that is deliberately not done here;
     * see {@code core.client.VoiceActivityTracker}.
     */
    private static boolean isTransmitting(VoicechatClientApi clientApi) {
        return !clientApi.isMuted() && clientApi.isTalking();
    }
}
