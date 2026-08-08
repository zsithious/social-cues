package dev.zsithious.socialcues.mcshared.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.zsithious.socialcues.core.client.ClientConfigData;

/**
 * DESIGN.md §9 — reads/writes {@code socialcues-client.json} under Fabric's
 * config directory. This is the only place in the whole mod that imports
 * Gson: per the task's clean-room note, Gson is already on Minecraft's own
 * classpath and fair game to use, but only here in {@code mc-shared}, never
 * in {@code core} — {@link ClientConfigData} itself stays plain Java with no
 * awareness of how it gets to/from disk.
 *
 * <p><b>Never crashes</b> (DESIGN.md §9's task note: "dosya yoksa
 * varsayılanlarla yazılır; bozuksa varsayılana düşer ve asla çökmez"): every
 * individual field is read defensively — a missing key, a value of the
 * wrong JSON type, or the file failing to parse at all each fall back to
 * {@link ClientConfigData#defaults()}'s corresponding value, or, for a
 * completely unreadable/malformed file, to the entire default record —
 * rather than throwing. This mirrors {@code paper.config.PluginConfig#load}'s
 * defend-every-key style, just written against Gson's tree API instead of
 * Bukkit's {@code FileConfiguration} (which already defends every key on its
 * own). {@link ClientConfigData}'s own compact constructor additionally
 * clamps every numeric field, so an in-range-JSON-type-but-out-of-bounds
 * value (e.g. {@code "scale": 999}) is handled one layer down, not here.
 */
public final class ClientConfigIo {

    private static final Logger LOGGER = Logger.getLogger("socialcues");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ClientConfigIo() {
    }

    /**
     * Loads {@code file}, creating it (with {@link ClientConfigData#defaults()})
     * if it does not exist yet. Any failure at any stage — I/O error,
     * unparsable JSON, a top-level value that isn't even a JSON object — is
     * logged at warning level and answered with {@link
     * ClientConfigData#defaults()}; this method itself never throws.
     */
    public static ClientConfigData load(Path file) {
        try {
            if (Files.notExists(file)) {
                ClientConfigData defaults = ClientConfigData.defaults();
                save(file, defaults);
                return defaults;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            JsonElement root = JsonParser.parseString(text);
            if (!root.isJsonObject()) {
                LOGGER.warning("socialcues: " + file + " does not contain a JSON object, using defaults");
                return ClientConfigData.defaults();
            }
            return fromJson(root.getAsJsonObject());
        } catch (RuntimeException | IOException e) {
            // RuntimeException covers Gson's JsonParseException/JsonSyntaxException
            // (both unchecked) plus anything an unexpectedly shaped file could
            // still trigger below despite the field-by-field defenses in
            // fromJson/getBoolean/getDouble/getStringSet — DESIGN.md's "asla
            // çökmez" is meant as an outer guarantee, not only a per-field one.
            LOGGER.log(Level.WARNING, "socialcues: failed to read " + file + ", using defaults", e);
            return ClientConfigData.defaults();
        }
    }

    /** Overwrites {@code file} with {@code data}, creating parent directories if needed. Logs and returns on I/O failure, never throws. */
    public static void save(Path file, ClientConfigData data) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, GSON.toJson(toJson(data)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "socialcues: failed to write " + file, e);
        }
    }

    private static ClientConfigData fromJson(JsonObject root) {
        ClientConfigData d = ClientConfigData.defaults();
        return new ClientConfigData(
                getBoolean(root, "layer1Enabled", d.layer1Enabled()),
                getBoolean(root, "layer2Enabled", d.layer2Enabled()),
                getBoolean(root, "layer3Enabled", d.layer3Enabled()),
                getDouble(root, "scale", d.scale()),
                getDouble(root, "opacity", d.opacity()),
                getDouble(root, "maxDistance", d.maxDistance()),
                getBoolean(root, "showOnSelf", d.showOnSelf()),
                getBoolean(root, "reducedMotion", d.reducedMotion()),
                getBoolean(root, "textOnly", d.textOnly()),
                getBoolean(root, "shareTyping", d.shareTyping()),
                getBoolean(root, "shareScreens", d.shareScreens()),
                getBoolean(root, "shareScreenDetail", d.shareScreenDetail()),
                getBoolean(root, "shareIdle", d.shareIdle()),
                getBoolean(root, "shareVoice", d.shareVoice()),
                getStringSet(root, "mutedPlayers", d.mutedPlayers()));
    }

    private static JsonObject toJson(ClientConfigData data) {
        JsonObject root = new JsonObject();
        root.addProperty("layer1Enabled", data.layer1Enabled());
        root.addProperty("layer2Enabled", data.layer2Enabled());
        root.addProperty("layer3Enabled", data.layer3Enabled());
        root.addProperty("scale", data.scale());
        root.addProperty("opacity", data.opacity());
        root.addProperty("maxDistance", data.maxDistance());
        root.addProperty("showOnSelf", data.showOnSelf());
        root.addProperty("reducedMotion", data.reducedMotion());
        root.addProperty("textOnly", data.textOnly());
        root.addProperty("shareTyping", data.shareTyping());
        root.addProperty("shareScreens", data.shareScreens());
        root.addProperty("shareScreenDetail", data.shareScreenDetail());
        root.addProperty("shareIdle", data.shareIdle());
        root.addProperty("shareVoice", data.shareVoice());
        JsonArray muted = new JsonArray();
        for (String name : data.mutedPlayers()) {
            muted.add(name);
        }
        root.add("mutedPlayers", muted);
        return root;
    }

    private static boolean getBoolean(JsonObject root, String key, boolean fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private static double getDouble(JsonObject root, String key, double fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return fallback;
        }
        return element.getAsDouble();
    }

    private static Set<String> getStringSet(JsonObject root, String key, Set<String> fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return fallback;
        }
        JsonArray array = element.getAsJsonArray();
        Set<String> result = new LinkedHashSet<>();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                result.add(item.getAsString());
            }
        }
        return result;
    }
}
