/**
 * DESIGN.md §9 P4a — client-side configuration file I/O
 * ({@link dev.zsithious.socialcues.mcshared.config.ClientConfigIo}, the only
 * place in this mod that imports Gson) and the loaded-for-the-session holder
 * ({@link dev.zsithious.socialcues.mcshared.config.ClientConfigState}) that
 * feeds {@code mcshared.client.ClientCueCapture#setSharePrefs} and P4b's
 * render code. The model itself — defaults, valid ranges, share-prefs
 * derivation — lives in {@code core.client.ClientConfigData}; this package
 * only reads/writes {@code socialcues-client.json} and remembers the
 * result, never doing anything Minecraft-specific beyond finding Fabric's
 * config directory.
 */
package dev.zsithious.socialcues.mcshared.config;
