package dev.zsithious.socialcues.mcshared.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers {@link SocialCuesPayload}'s codec for both wire directions of
 * the single {@code socialcues:v1} channel. {@code PayloadTypeRegistry} is
 * common (non-client-only) Fabric API, safe to call from the common
 * entrypoint so both the client and the dedicated server know the type
 * before any packet using it can be sent or received.
 */
public final class SocialCuesChannels {

    private SocialCuesChannels() {
    }

    public static void registerPayloadType() {
        PayloadTypeRegistry.playC2S().register(SocialCuesPayload.ID, SocialCuesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SocialCuesPayload.ID, SocialCuesPayload.CODEC);
    }
}
