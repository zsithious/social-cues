package dev.zsithious.socialcues.mcshared;

import java.util.logging.Logger;

import dev.zsithious.socialcues.mcshared.network.ServerHandshake;
import dev.zsithious.socialcues.mcshared.network.SocialCuesChannels;

import net.fabricmc.api.ModInitializer;

/**
 * Common entrypoint — runs on both client and dedicated server per
 * fabric.mod.json's {@code "environment": "*"}. P1 (DESIGN.md §14):
 * registers the {@code socialcues:v1} CustomPayload type for both
 * directions and starts the server side of the handshake. No render, no
 * relay yet.
 *
 * <p>Nothing reachable from here may reference client-only Minecraft/
 * fabric-api classes (e.g. {@code ClientPlayNetworking}, {@code
 * MinecraftClient}) — those live behind {@link SocialCuesClientInitializer}
 * instead, so a dedicated server never tries to link a class that only
 * exists on the client.
 */
public final class SocialCuesInitializer implements ModInitializer {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    @Override
    public void onInitialize() {
        SocialCuesChannels.registerPayloadType();
        ServerHandshake.register();
        LOGGER.info("Social Cues loaded (P1: handshake wired, no relay/render yet)");
    }
}
