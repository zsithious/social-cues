package dev.zsithious.socialcues.configui;

import com.mojang.brigadier.Command;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

import net.minecraft.client.MinecraftClient;

/**
 * DESIGN.md §9 — a second, dependency-free way into the config screen:
 * {@code /socialcues config}.
 *
 * <p><b>Why this exists at all.</b> DESIGN.md §9 names ModMenu as the config
 * screen's host, but ModMenu is a <em>soft</em> dependency this mod
 * deliberately never bundles, while Cloth Config <em>is</em> bundled. Without
 * this command, a user who installs Social Cues alone gets a config UI that
 * physically cannot be opened — every switch DESIGN.md §9 promises would be
 * reachable only by hand-editing {@code config/socialcues-client.json}. One
 * client command is a far smaller cost than shipping a mod-list UI nobody
 * asked for. Recorded as a §9 addition, not a silent extra.
 *
 * <p>Registered as a second {@code client} entrypoint rather than folded into
 * {@code mcshared.SocialCuesClientInitializer}, because that class is
 * compiled by all twelve rows and this one may only exist where Cloth is
 * pinned (see this package's {@code package-info}).
 *
 * <p><b>Client-side command, not a server one</b> ({@code
 * fabric-command-api-v2}'s {@code ClientCommandRegistrationCallback}): it
 * edits this player's own client config, so it must work on any server —
 * including a vanilla one that has never heard of this mod, where DESIGN.md
 * §5 requires the mod to stay entirely dormant on the wire. A client command
 * sends no packets at all.
 */
public final class ConfigUiClientEntrypoint implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("socialcues")
                        .then(ClientCommandManager.literal("config")
                                .executes(context -> {
                                    openConfigScreen();
                                    return Command.SINGLE_SUCCESS;
                                }))));
    }

    /**
     * Deferred with {@link net.minecraft.util.thread.ThreadExecutor#send}
     * rather than opened inline: a command runs while the chat screen is
     * still the current screen, and Minecraft closes that screen (a
     * {@code setScreen(null)}) immediately after the command returns — which
     * would close ours right back. Running on the next client tick puts the
     * screen up after that teardown instead.
     */
    private static void openConfigScreen() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.send(() -> client.setScreen(SocialCuesConfigScreen.create(null)));
    }
}
