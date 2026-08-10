package dev.zsithious.socialcues.configui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * DESIGN.md §9 — ModMenu's "Configure" button for Social Cues.
 *
 * <p>Registered under {@code fabric.mod.json}'s {@code modmenu} entrypoint,
 * and referenced from nowhere else. ModMenu is {@code modCompileOnly} plus a
 * {@code suggests} entry, never {@code include}d: Fabric simply never invokes
 * an entrypoint whose owner mod is absent, so a user without ModMenu loses
 * this button and nothing else — {@link ConfigUiClientEntrypoint}'s
 * {@code /socialcues config} command is the way in for them.
 *
 * <p>Deliberately holds no state and builds nothing itself: the screen is
 * constructed fresh on every click by {@link SocialCuesConfigScreen#create},
 * because it seeds its widgets from whatever {@code ClientConfigState} holds
 * at that moment.
 */
public final class SocialCuesModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return SocialCuesConfigScreen::create;
    }
}
