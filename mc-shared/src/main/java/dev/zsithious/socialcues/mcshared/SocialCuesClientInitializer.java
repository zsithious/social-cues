package dev.zsithious.socialcues.mcshared;

import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.mcshared.client.ClientCueCapture;
import dev.zsithious.socialcues.mcshared.client.FeatureRendererBootstrap;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;
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

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    @Override
    public void onInitializeClient() {
        // DESIGN.md §9 P4a: load socialcues-client.json (creating it with
        // defaults if absent) before anything below reads share prefs from
        // it, so the very first tick already sees the user's real settings
        // rather than a brief window of hardcoded defaults.
        ClientConfigState.load();

        ClientHandshakeNetworking.register();
        // DESIGN.md §14 P3: client-side capture (typing/screen/AFK/intensity).
        // Only ever sends anything once ClientHandshakeNetworking reports the
        // handshake ACTIVE (see ClientCueCapture.onClientTick's first check).
        ClientCueCapture.register();
        // DESIGN.md §9 P4a: what P3 left as SharePrefsSource.allEnabled() is
        // now backed by the loaded config, live for the rest of the session
        // (see ClientConfigState.SHARE_PREFS's Javadoc for why no further
        // wiring is needed after a future P6 reload).
        ClientCueCapture.setSharePrefs(ClientConfigState.SHARE_PREFS);

        // DESIGN.md §7 P4b: hand off to whichever bucket is actually on the
        // classpath for this MC version's Layer 1 feature-renderer
        // registration, without this (bucket-agnostic) class ever importing a
        // bucket-specific one — see FeatureRendererBootstrap's Javadoc. A
        // bucket that supplies no provider (every bucket but D, today)
        // contributes an empty ServiceLoader result, not an error.
        for (FeatureRendererBootstrap bootstrap : ServiceLoader.load(
                FeatureRendererBootstrap.class, FeatureRendererBootstrap.class.getClassLoader())) {
            try {
                bootstrap.register();
            } catch (RuntimeException e) {
                // A render-registration provider failing to register must never take the
                // rest of client init down with it (DESIGN.md §11's general "one layer's
                // conflict must not crash the mod" stance, applied here to bring-up too).
                LOGGER.log(Level.WARNING, "socialcues: " + bootstrap.getClass().getName()
                        + " failed to register its feature renderer(s)", e);
            }
        }
    }
}
