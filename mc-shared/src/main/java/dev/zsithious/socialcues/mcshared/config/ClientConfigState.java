package dev.zsithious.socialcues.mcshared.config;

import java.nio.file.Path;
import java.util.Objects;

import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.SharePrefsSource;
import dev.zsithious.socialcues.mcshared.client.ClientCueCapture;

import net.fabricmc.loader.api.FabricLoader;

/**
 * DESIGN.md §9 — the loaded-for-the-session client configuration. Mirrors
 * {@code mcshared.client.ServerPolicyState}'s "static holder + accessor"
 * shape: the actual file I/O lives in {@link ClientConfigIo}, this class
 * only remembers the most recently loaded/applied {@link ClientConfigData}
 * and is the one static access point both {@code SocialCuesClientInitializer}
 * (which loads it at startup and wires it into
 * {@code mcshared.client.ClientCueCapture#setSharePrefs}) and P4b's render
 * code (which reads layers/scale/opacity/accessibility/mute list from
 * {@link #get()}) touch.
 *
 * <p>Only ever touched from the client thread — loaded once during {@code
 * onInitializeClient}, and reassigned by {@link #set} every time P6's Cloth
 * config screen (or the {@code /socialcues config} fallback command) saves.
 * Cloth screens themselves run on the render thread, so this is still the
 * same no-synchronization assumption every other piece of this mod's
 * Minecraft-side state already makes (see {@code core.relay.CueRelay}'s
 * Javadoc) — {@link #set} being reachable at runtime now, not just in a
 * future phase, does not change that.
 */
public final class ClientConfigState {

    private static final String FILE_NAME = "socialcues-client.json";

    private static ClientConfigData current = ClientConfigData.defaults();

    /**
     * DESIGN.md §9 / P3 task note's injectable seam, now backed by the
     * config instead of {@link SharePrefsSource#allEnabled()}: reads {@link
     * #current} fresh on every call (a static field access, not a captured
     * snapshot), so a future P6 reload via {@link #set} takes effect
     * immediately without {@code ClientCueCapture} having to re-register
     * anything.
     */
    public static final SharePrefsSource SHARE_PREFS = () -> current.prefBits();

    private ClientConfigState() {
    }

    /** Loads (or creates, with defaults) {@code socialcues-client.json} under Fabric's config dir and stores the result as {@link #get()}. */
    public static void load() {
        current = ClientConfigIo.load(configFile());
    }

    public static ClientConfigData get() {
        return current;
    }

    /**
     * P6's config screen's single "apply" step: {@code data} takes effect
     * immediately (every renderer already reads {@link #get()} fresh every
     * frame, and {@link #SHARE_PREFS} fresh every call) and is persisted to
     * disk. Also pushes the new privacy state onto the wire right away —
     * {@link ClientCueCapture#onConfigChanged()} — instead of leaving it to
     * be picked up whenever {@code CueSampler}'s own rate limit next allows a
     * send; see that method's Javadoc for why. This is the single choke point
     * every config write goes through, on purpose, so nothing else ever has
     * to remember to call {@code onConfigChanged()} itself.
     */
    public static void set(ClientConfigData data) {
        current = Objects.requireNonNull(data, "data");
        ClientConfigIo.save(configFile(), current);
        ClientCueCapture.onConfigChanged();
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
