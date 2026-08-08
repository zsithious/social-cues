package dev.zsithious.socialcues.mcshared;

import dev.zsithious.socialcues.mcshared.network.ClientHandshakeNetworking;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client-only entrypoint (fabric.mod.json {@code "client"}). Kept separate
 * from {@link SocialCuesInitializer} so nothing on a dedicated server ever
 * references client-only classes ({@code ClientPlayNetworking}, {@code
 * ClientTickEvents}, {@code MinecraftClient}, ...) — Fabric Loader simply
 * never instantiates this class outside a client environment.
 */
public final class SocialCuesClientInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientHandshakeNetworking.register();
    }
}
