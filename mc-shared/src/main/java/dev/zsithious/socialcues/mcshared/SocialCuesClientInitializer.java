package dev.zsithious.socialcues.mcshared;

import dev.zsithious.socialcues.mcshared.client.ClientCueCapture;
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

        // No render registration happens here. P4b originally registered Layer 1 as
        // a Fabric API feature renderer through a ServiceLoader-discovered, per-bucket
        // FeatureRendererBootstrap; the P4 hand test showed a feature renderer draws in
        // the wrong matrix space for a camera-facing world overlay, so both layers are
        // now bucket-local mixins that need no bring-up from the shared entrypoint
        // (see CueBillboardRenderer's Javadoc and DESIGN.md §7).
    }
}
