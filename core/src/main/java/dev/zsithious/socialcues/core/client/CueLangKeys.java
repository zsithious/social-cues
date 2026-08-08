package dev.zsithious.socialcues.core.client;

import java.util.Objects;

import dev.zsithious.socialcues.core.state.Activity;

/**
 * DESIGN.md §9 P4a — translation key names backing
 * {@code assets/socialcues/lang/en_us.json} / {@code tr_tr.json}. P4b's
 * {@code textOnly} accessibility mode (DESIGN.md §9) and the tab-list layer
 * are expected to read these through Minecraft's own
 * {@code Text.translatable(String)}; this class has no reason to import
 * that itself, it only hands back the key string.
 *
 * <p>Same reasoning as {@link CueIconAtlas}: an exhaustive {@code switch}
 * over {@link Activity}, rather than a derived/computed key (e.g.
 * {@code "socialcues.activity." + activity.name().toLowerCase()}), so a
 * future {@code Activity} constant added without a matching lang-file entry
 * fails to <em>compile</em> here — forcing whoever adds it to also add the
 * translation, rather than silently falling back to a raw key string
 * Minecraft renders as-is when no translation exists for it.
 */
public final class CueLangKeys {

    /** DESIGN.md §4's {@code CueFlags#SLEEPY} variant of {@link Activity#AFK} — the text-mode counterpart of {@code CueIconAtlas#SLEEPY_CELL}. */
    public static final String SLEEPY_FLAG_KEY = "socialcues.flag.sleepy";

    private CueLangKeys() {
    }

    /** The translation key for {@code activity}'s short label. Every {@link Activity} value has one — see {@code CueLangKeysTest}. */
    public static String keyFor(Activity activity) {
        Objects.requireNonNull(activity, "activity");
        return switch (activity) {
            case NORMAL -> "socialcues.activity.normal";
            case TYPING_CHAT -> "socialcues.activity.typing_chat";
            case TYPING_COMMAND -> "socialcues.activity.typing_command";
            case TYPING_SIGN -> "socialcues.activity.typing_sign";
            case TYPING_BOOK -> "socialcues.activity.typing_book";
            case IN_SCREEN -> "socialcues.activity.in_screen";
            case AFK -> "socialcues.activity.afk";
            case SPEAKING -> "socialcues.activity.speaking";
        };
    }
}
