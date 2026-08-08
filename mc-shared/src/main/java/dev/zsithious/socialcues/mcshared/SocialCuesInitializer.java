package dev.zsithious.socialcues.mcshared;

import java.util.logging.Logger;

import net.fabricmc.api.ModInitializer;

/**
 * P0 stub entrypoint: no channel registration, no listeners, no render —
 * just proves the mod loads. DESIGN.md's actual behaviour (handshake,
 * dormant fallback, relay) starts at P1 (see DESIGN.md §14).
 */
public final class SocialCuesInitializer implements ModInitializer {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    @Override
    public void onInitialize() {
        LOGGER.info("Social Cues loaded (P0 stub, no features yet)");
    }
}
