package dev.zsithious.socialcues.mcshared.config;

import java.nio.file.Path;
import java.util.Objects;

import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.core.client.SharePrefsSource;

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
 * onInitializeClient}, and not reassigned again until a future P6 config UI
 * exists — matching the no-synchronization assumption every other piece of
 * this mod's Minecraft-side state already makes (see {@code
 * core.relay.CueRelay}'s Javadoc).
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

    /** For a future P6 config UI: applies {@code data} immediately and persists it to disk. */
    public static void set(ClientConfigData data) {
        current = Objects.requireNonNull(data, "data");
        ClientConfigIo.save(configFile(), current);
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }
}
