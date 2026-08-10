package dev.zsithious.socialcues.configui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.api.Requirement;
import me.shedaniel.clothconfig2.gui.entries.BooleanListEntry;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import dev.zsithious.socialcues.core.client.ClientConfigData;
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;

/**
 * DESIGN.md §9 / §14 P6 — the Cloth Config screen behind ModMenu's
 * "Configure" button and {@code /socialcues config}.
 *
 * <p><b>Everything here is live.</b> DESIGN.md §14's acceptance criterion for
 * P6 is "tüm anahtarlar canlı etki ediyor", and no part of this class has to
 * work for that: the render layers read {@code ClientConfigState.get()} once
 * per frame, and {@code mcshared.client.ClientCueCapture} reads its share
 * prefs through {@code ClientConfigState.SHARE_PREFS}, a lambda over the
 * current record rather than a captured snapshot. So a single {@code
 * ClientConfigState.set(...)} at save time is the whole of "apply" — there is
 * no reload step, no re-registration, and (because DESIGN.md §5 applies share
 * prefs <em>client-side</em>, sending the server only the already-masked
 * result) no re-handshake either.
 *
 * <p><b>The draft.</b> {@link ClientConfigData} is an immutable record whose
 * compact constructor clamps and normalizes, so it cannot be edited field by
 * field as the user clicks. Each entry's save consumer therefore writes into
 * a mutable {@link Draft}, and Cloth's single {@code setSavingRunnable} turns
 * that into exactly one record — one clamp pass, one file write, one
 * {@code set}. Building a record per entry would also mean a partially-saved
 * config on disk if anything threw halfway through.
 *
 * <p><b>Ranges are read, never re-typed.</b> Every slider takes its bounds
 * from {@link ClientConfigData}'s own {@code MIN_*}/{@code MAX_*} constants,
 * and every entry takes its reset value from {@link
 * ClientConfigData#defaults()}: the model stays the single source of truth
 * for what is valid, so a range change there can never leave a stale number
 * in the UI. The two of them being out of step would be invisible — the
 * record would silently clamp whatever the slider allowed.
 *
 * <p>Scale and opacity are shown as integer percent sliders rather than raw
 * {@code double} fields: a slider cannot produce an invalid value in the
 * first place, so the clamp never has to visibly correct the user, and
 * "150%" is a great deal easier to reason about than "1.5".
 */
public final class SocialCuesConfigScreen {

    private SocialCuesConfigScreen() {
    }

    /**
     * Builds the screen from whatever config is loaded <em>now</em> — called
     * fresh on every open, never cached, so a config changed on disk (or by a
     * future in-game command) is always what the widgets show.
     *
     * @param parent the screen to return to on close; {@code null} when
     *               opened from the {@code /socialcues config} command, which
     *               means "return to the game"
     */
    public static Screen create(Screen parent) {
        ClientConfigData current = ClientConfigState.get();
        Draft draft = Draft.of(current);

        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Text.translatable("socialcues.config.title"))
                .setSavingRunnable(() -> ClientConfigState.set(draft.toData()));

        ConfigEntryBuilder entries = builder.entryBuilder();

        addDisplayCategory(builder, entries, current, draft);
        addAccessibilityCategory(builder, entries, current, draft);
        addPrivacyCategory(builder, entries, current, draft);
        addMutedCategory(builder, entries, current, draft);

        return builder.build();
    }

    // ------------------------------------------------------------- display

    /** DESIGN.md §9 "Görünüm": layer toggles, scale, opacity, max distance, showOnSelf. */
    private static void addDisplayCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
            ClientConfigData current, Draft draft) {
        ClientConfigData defaults = ClientConfigData.defaults();
        ConfigCategory category = builder.getOrCreateCategory(Text.translatable("socialcues.config.category.display"));

        category.addEntry(entries.startBooleanToggle(option("layer1Enabled"), current.layer1Enabled())
                .setDefaultValue(defaults.layer1Enabled())
                .setTooltip(tooltip("layer1Enabled"))
                .setSaveConsumer(value -> draft.layer1Enabled = value)
                .build());

        category.addEntry(entries.startBooleanToggle(option("layer2Enabled"), current.layer2Enabled())
                .setDefaultValue(defaults.layer2Enabled())
                .setTooltip(tooltip("layer2Enabled"))
                .setSaveConsumer(value -> draft.layer2Enabled = value)
                .build());

        category.addEntry(entries.startBooleanToggle(option("layer3Enabled"), current.layer3Enabled())
                .setDefaultValue(defaults.layer3Enabled())
                .setTooltip(tooltip("layer3Enabled"))
                .setSaveConsumer(value -> draft.layer3Enabled = value)
                .build());

        category.addEntry(entries.startIntSlider(option("scale"), percent(current.scale()),
                        percent(ClientConfigData.MIN_SCALE), percent(ClientConfigData.MAX_SCALE))
                .setDefaultValue(percent(defaults.scale()))
                .setTextGetter(SocialCuesConfigScreen::percentLabel)
                .setTooltip(tooltip("scale"))
                .setSaveConsumer(value -> draft.scale = value / 100.0)
                .build());

        category.addEntry(entries.startIntSlider(option("opacity"), percent(current.opacity()),
                        percent(ClientConfigData.MIN_OPACITY), percent(ClientConfigData.MAX_OPACITY))
                .setDefaultValue(percent(defaults.opacity()))
                .setTextGetter(SocialCuesConfigScreen::percentLabel)
                .setTooltip(tooltip("opacity"))
                .setSaveConsumer(value -> draft.opacity = value / 100.0)
                .build());

        category.addEntry(entries.startIntSlider(option("maxDistance"), blocks(current.maxDistance()),
                        blocks(ClientConfigData.MIN_MAX_DISTANCE), blocks(ClientConfigData.MAX_MAX_DISTANCE))
                .setDefaultValue(blocks(defaults.maxDistance()))
                .setTextGetter(value -> Text.translatable("socialcues.config.value.blocks", value))
                .setTooltip(tooltip("maxDistance"))
                .setSaveConsumer(value -> draft.maxDistance = value)
                .build());

        category.addEntry(entries.startBooleanToggle(option("showOnSelf"), current.showOnSelf())
                .setDefaultValue(defaults.showOnSelf())
                .setTooltip(tooltip("showOnSelf"))
                .setSaveConsumer(value -> draft.showOnSelf = value)
                .build());
    }

    // ------------------------------------------------------- accessibility

    /**
     * DESIGN.md §9 "Erişilebilirlik": {@code reducedMotion} (no animation at
     * all, static icon only) and {@code textOnly} (a short translated label
     * instead of the icon).
     */
    private static void addAccessibilityCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
            ClientConfigData current, Draft draft) {
        ClientConfigData defaults = ClientConfigData.defaults();
        ConfigCategory category =
                builder.getOrCreateCategory(Text.translatable("socialcues.config.category.accessibility"));

        category.addEntry(entries.startBooleanToggle(option("reducedMotion"), current.reducedMotion())
                .setDefaultValue(defaults.reducedMotion())
                .setTooltip(tooltip("reducedMotion"))
                .setSaveConsumer(value -> draft.reducedMotion = value)
                .build());

        category.addEntry(entries.startBooleanToggle(option("textOnly"), current.textOnly())
                .setDefaultValue(defaults.textOnly())
                .setTooltip(tooltip("textOnly"))
                .setSaveConsumer(value -> draft.textOnly = value)
                .build());
    }

    // ------------------------------------------------------------- privacy

    /**
     * DESIGN.md §9 "Gizlilik (paylaşım)". Two rules the model already
     * enforces are mirrored here as Cloth {@link
     * me.shedaniel.clothconfig2.api.Requirement}s so the UI greys the
     * dependent switches out instead of accepting a click it will silently
     * discard:
     * <ul>
     *   <li>every {@code share*} entry requires {@code shareNothing} to be
     *       off ({@code Requirement.isFalse(shareNothingEntry)});</li>
     *   <li>{@code shareScreenDetail} additionally requires {@code
     *       shareScreens} ({@code Requirement.isTrue(shareScreensEntry)}) —
     *       the record's own {@code shareScreenDetail && shareScreens}
     *       line.</li>
     * </ul>
     * Both need the <em>built</em> entry object, which implements {@code
     * ValueHolder}: build it into a local variable, add it to the category,
     * and pass that variable to the dependents' {@code setRequirement}.
     */
    private static void addPrivacyCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
            ClientConfigData current, Draft draft) {
        ClientConfigData defaults = ClientConfigData.defaults();
        ConfigCategory category =
                builder.getOrCreateCategory(Text.translatable("socialcues.config.category.privacy"));

        BooleanListEntry shareNothingEntry =
                entries.startBooleanToggle(option("shareNothing"), current.shareNothing())
                        .setDefaultValue(defaults.shareNothing())
                        .setTooltip(tooltip("shareNothing"))
                        .setSaveConsumer(value -> draft.shareNothing = value)
                        .build();
        category.addEntry(shareNothingEntry);
        Requirement notShareNothing = Requirement.isFalse(shareNothingEntry);

        category.addEntry(entries.startBooleanToggle(option("shareTyping"), current.shareTyping())
                .setDefaultValue(defaults.shareTyping())
                .setTooltip(tooltip("shareTyping"))
                .setRequirement(notShareNothing)
                .setSaveConsumer(value -> draft.shareTyping = value)
                .build());

        BooleanListEntry shareScreensEntry =
                entries.startBooleanToggle(option("shareScreens"), current.shareScreens())
                        .setDefaultValue(defaults.shareScreens())
                        .setTooltip(tooltip("shareScreens"))
                        .setRequirement(notShareNothing)
                        .setSaveConsumer(value -> draft.shareScreens = value)
                        .build();
        category.addEntry(shareScreensEntry);

        category.addEntry(entries.startBooleanToggle(option("shareScreenDetail"), current.shareScreenDetail())
                .setDefaultValue(defaults.shareScreenDetail())
                .setTooltip(tooltip("shareScreenDetail"))
                .setRequirement(Requirement.all(notShareNothing, Requirement.isTrue(shareScreensEntry)))
                .setSaveConsumer(value -> draft.shareScreenDetail = value)
                .build());

        category.addEntry(entries.startBooleanToggle(option("shareIdle"), current.shareIdle())
                .setDefaultValue(defaults.shareIdle())
                .setTooltip(tooltip("shareIdle"))
                .setRequirement(notShareNothing)
                .setSaveConsumer(value -> draft.shareIdle = value)
                .build());

        category.addEntry(entries.startBooleanToggle(option("shareVoice"), current.shareVoice())
                .setDefaultValue(defaults.shareVoice())
                .setTooltip(tooltip("shareVoice"))
                .setRequirement(notShareNothing)
                .setSaveConsumer(value -> draft.shareVoice = value)
                .build());
    }

    // --------------------------------------------------------------- muted

    /** DESIGN.md §9 "Sessize alma listesi": hide these players' cues locally. */
    private static void addMutedCategory(ConfigBuilder builder, ConfigEntryBuilder entries,
            ClientConfigData current, Draft draft) {
        ConfigCategory category =
                builder.getOrCreateCategory(Text.translatable("socialcues.config.category.muted"));

        category.addEntry(entries.startStrList(option("mutedPlayers"), new ArrayList<>(current.mutedPlayers()))
                .setDefaultValue(List.of())
                .setTooltip(tooltip("mutedPlayers"))
                .setExpanded(true)
                // Case and duplicates are the record's problem, not the UI's:
                // ClientConfigData's compact constructor trims, lower-cases and
                // de-duplicates every name (matching its case-insensitive
                // isMuted). Re-validating here would be a second copy of a rule
                // that must not be allowed to drift.
                .setSaveConsumer(values -> draft.mutedPlayers = new LinkedHashSet<>(values))
                .build());
    }

    // --------------------------------------------------------------- utils

    /** {@code socialcues.config.option.<field>} — {@code field} is the record component name, verbatim. */
    private static Text option(String field) {
        return Text.translatable("socialcues.config.option." + field);
    }

    /** The matching {@code .tooltip} key, as the single-element array Cloth's {@code setTooltip} varargs wants. */
    private static Text tooltip(String field) {
        return Text.translatable("socialcues.config.option." + field + ".tooltip");
    }

    private static int percent(double fraction) {
        return (int) Math.round(fraction * 100.0);
    }

    private static int blocks(double distance) {
        return (int) Math.round(distance);
    }

    /**
     * A literal, not a translation key: "%" is the same symbol in every
     * language this mod is likely to ship, and a format string with a literal
     * percent sign is a well-known way to get a {@code MissingFormatArgument}
     * out of a translation file. The one place a unit really is a word —
     * blocks — does use a key.
     */
    private static Text percentLabel(int value) {
        return Text.literal(value + "%");
    }

    /**
     * The mutable mirror of {@link ClientConfigData} that entry save
     * consumers write into. Package-free, deliberately field-per-component
     * rather than a builder: this is only ever alive between opening the
     * screen and Cloth's save runnable firing, and its one job is to make
     * {@link #toData()} a single, obviously-total conversion.
     */
    private static final class Draft {

        private boolean layer1Enabled;
        private boolean layer2Enabled;
        private boolean layer3Enabled;
        private double scale;
        private double opacity;
        private double maxDistance;
        private boolean showOnSelf;
        private boolean reducedMotion;
        private boolean textOnly;
        private boolean shareNothing;
        private boolean shareTyping;
        private boolean shareScreens;
        private boolean shareScreenDetail;
        private boolean shareIdle;
        private boolean shareVoice;
        private Set<String> mutedPlayers;

        static Draft of(ClientConfigData data) {
            Draft draft = new Draft();
            draft.layer1Enabled = data.layer1Enabled();
            draft.layer2Enabled = data.layer2Enabled();
            draft.layer3Enabled = data.layer3Enabled();
            draft.scale = data.scale();
            draft.opacity = data.opacity();
            draft.maxDistance = data.maxDistance();
            draft.showOnSelf = data.showOnSelf();
            draft.reducedMotion = data.reducedMotion();
            draft.textOnly = data.textOnly();
            draft.shareNothing = data.shareNothing();
            draft.shareTyping = data.shareTyping();
            draft.shareScreens = data.shareScreens();
            draft.shareScreenDetail = data.shareScreenDetail();
            draft.shareIdle = data.shareIdle();
            draft.shareVoice = data.shareVoice();
            draft.mutedPlayers = data.mutedPlayers();
            return draft;
        }

        ClientConfigData toData() {
            return new ClientConfigData(
                    layer1Enabled, layer2Enabled, layer3Enabled,
                    scale, opacity, maxDistance,
                    showOnSelf,
                    reducedMotion, textOnly,
                    shareNothing,
                    shareTyping, shareScreens, shareScreenDetail, shareIdle, shareVoice,
                    mutedPlayers);
        }
    }
}
