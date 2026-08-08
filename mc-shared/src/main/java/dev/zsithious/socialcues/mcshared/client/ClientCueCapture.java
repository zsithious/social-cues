package dev.zsithious.socialcues.mcshared.client;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.core.client.CommandDraftDetector;
import dev.zsithious.socialcues.core.client.CueSampler;
import dev.zsithious.socialcues.core.client.ScreenKindMapper;
import dev.zsithious.socialcues.core.client.SharePrefsSource;
import dev.zsithious.socialcues.core.policy.EffectivePolicy;
import dev.zsithious.socialcues.core.policy.PolicyBits;
import dev.zsithious.socialcues.core.protocol.C2SMessages;
import dev.zsithious.socialcues.core.protocol.CueUpdate;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;
import dev.zsithious.socialcues.core.util.IdleTimer;
import dev.zsithious.socialcues.core.util.TypingRateMeter;
import dev.zsithious.socialcues.mcshared.network.ClientHandshakeNetworking;
import dev.zsithious.socialcues.mcshared.network.SocialCuesPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.PlayerInput;

import org.lwjgl.glfw.GLFW;

/**
 * DESIGN.md §14 P3 "İstemci yakalama": the Minecraft-side glue that turns
 * "what screen is open / was a key pressed / when did the player last do
 * anything" into the {@code Activity}/{@code ScreenKind}/intensity/flags
 * that {@code core.client.CueSampler} needs. Every actual decision (policy
 * masking, change detection, rate limiting) lives in {@code core.client},
 * already unit tested without Minecraft; this class only reads Minecraft
 * state and feeds it in. Client-only (wired from
 * {@code SocialCuesClientInitializer}), never reachable from a dedicated
 * server.
 *
 * <p><b>Activity precedence</b> (DESIGN.md §4 says a player has exactly one
 * dominant {@code Activity} but does not spell out the order when several
 * conditions overlap; this is this class's resolution, applied every tick):
 * <ol>
 *   <li>A typing screen ({@link ChatScreen}, {@link AbstractSignEditScreen}
 *       — covers both sign and hanging-sign editing — or
 *       {@link BookEditScreen}) open beats everything else: one of the four
 *       {@code TYPING_*} activities, never {@code IN_SCREEN} or {@code AFK},
 *       for as long as it stays open.</li>
 *   <li>Otherwise, any other open {@link Screen} means {@link Activity#IN_SCREEN}
 *       (never {@code AFK} — an open menu means the player's attention is on
 *       it, however long they leave it sitting there).</li>
 *   <li>Otherwise (no screen open at all), idle time past the server's
 *       threshold means {@link Activity#AFK}.</li>
 *   <li>Otherwise {@link Activity#NORMAL}.</li>
 * </ol>
 *
 * <p><b>"Hiç ölçülmeyecek" (DESIGN.md §6/§10):</b> each tier above is gated
 * by its own policy bit computed fresh every tick
 * ({@link #currentEffectiveBits()}); when a bit is off, this class does not
 * merely omit the field from the outgoing packet (that would already be true
 * courtesy of {@code core.policy.EffectivePolicy} inside
 * {@code CueSampler}) — it skips resolving that tier's detail at all (e.g.
 * the {@code ScreenHandlerType} registry lookup never runs when
 * {@code SCREEN_DETAIL} is off; the chat-command classification below never
 * runs when {@code TYPING} is off).
 *
 * <p><b>Written text is never read, not even transiently (DESIGN.md §10.1):</b>
 * {@code TYPING_COMMAND} vs {@code TYPING_CHAT} is decided purely from
 * keycodes via {@link CommandDraftDetector} (pure Java, unit tested without
 * Minecraft), never from the chat field's contents — see that class's
 * Javadoc for why "just peek at the first character" is not actually
 * possible without materializing the whole in-progress message as a Java
 * {@code String}, up to ~20 times/second while typing. {@code mc-shared}'s
 * {@code checkNoTextAccess} Gradle task (see {@code mc/mc.gradle.kts})
 * enforces this mechanically: it fails the build on any
 * {@code getText()}/{@code getMessage()}/{@code chatField}/
 * {@code originalChatText} occurrence under this module's sources, so a
 * future edit can't reintroduce a text read by accident.
 */
public final class ClientCueCapture {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    private static final TypingRateMeter TYPING_RATE = new TypingRateMeter();
    private static final IdleTimer IDLE_TIMER = new IdleTimer(System.currentTimeMillis());
    private static final CueSampler SAMPLER = new CueSampler();

    /**
     * DESIGN.md §9 / P3 task note's single injectable seam: everything below
     * reads "what does the local player agree to share" through this one
     * field. P3 wires the only implementation that exists yet
     * ({@link SharePrefsSource#allEnabled()}); a future config UI (P6) only
     * has to call {@link #setSharePrefs} once with its own implementation —
     * nothing else in this class, or in {@code core}, changes.
     */
    private static SharePrefsSource sharePrefs = SharePrefsSource.allEnabled();

    // Movement/look bookkeeping for AFK detection (DESIGN.md §6: "son girdi
    // (tuş/fare/hareket/bakış) zamanı").
    private static boolean havePreviousPose;
    private static double lastX;
    private static double lastY;
    private static double lastZ;
    private static float lastYaw;
    private static float lastPitch;

    // Log-on-transition-only bookkeeping (DESIGN.md §6: "Her tick log basma").
    private static Activity lastLoggedActivity;
    private static ScreenKind lastLoggedScreenKind;

    // Keycode-only TYPING_COMMAND detection (DESIGN.md §6/§10.1): reset every
    // time a fresh ChatScreen opens. See CommandDraftDetector's Javadoc.
    private static final CommandDraftDetector COMMAND_DRAFT = new CommandDraftDetector();

    private ClientCueCapture() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register(ClientCueCapture::onScreenInit);
        ClientTickEvents.END_CLIENT_TICK.register(ClientCueCapture::onClientTick);
    }

    /** The single seam P6's config UI is expected to call into; see the class Javadoc. */
    public static void setSharePrefs(SharePrefsSource source) {
        sharePrefs = Objects.requireNonNull(source, "source");
    }

    /**
     * Called by {@code ClientHandshakeNetworking} whenever the handshake
     * leaves {@code ACTIVE} (disconnect) or is about to be renegotiated
     * (fresh join): forgets the last-sent state and pose bookkeeping so a
     * new session starts clean rather than assuming the other end — which
     * might be an entirely different server with different policy bits —
     * remembers anything a previous session sent.
     */
    public static void reset() {
        SAMPLER.reset();
        havePreviousPose = false;
        lastLoggedActivity = null;
        lastLoggedScreenKind = null;
        COMMAND_DRAFT.reset();
        // DESIGN.md §7 P4b: same reasoning as RemoteCueStoreHolder's own reset call
        // (ClientHandshakeNetworking) — a stale self-cue from a previous server must
        // never survive into a new session.
        LocalCueState.reset();
    }

    // ---- ScreenKeyboardEvents wiring (typing cadence + command detection) --

    private static void onScreenInit(MinecraftClient client, Screen screen, int width, int height) {
        if (screen instanceof ChatScreen) {
            // A fresh chat session: nothing decided yet about command-vs-chat.
            COMMAND_DRAFT.reset();
        }
        if (!isTypingScreen(screen)) {
            return;
        }
        boolean isChat = screen instanceof ChatScreen;
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> onTypingKeyPress(key, isChat));
    }

    private static void onTypingKeyPress(KeyInput key, boolean isChat) {
        long now = System.currentTimeMillis();
        IDLE_TIMER.recordActivity(now);
        if (!hasBit(currentEffectiveBits(), PolicyBits.TYPING)) {
            return;
        }
        // DESIGN.md §10.1: only "a keystroke happened, and was it the slash
        // key" is ever observed here — KeyInput carries a keycode, never the
        // field's text (see the class Javadoc and CommandDraftDetector for
        // why that distinction matters and checkNoTextAccess for how it's
        // enforced mechanically).
        TYPING_RATE.recordKeystroke(now);
        if (isChat) {
            COMMAND_DRAFT.onKeyPress(key.key() == GLFW.GLFW_KEY_SLASH);
        }
    }

    private static boolean isTypingScreen(Screen screen) {
        return screen instanceof ChatScreen
                || screen instanceof AbstractSignEditScreen
                || screen instanceof BookEditScreen;
    }

    // ---- per-tick sampling --------------------------------------------------

    private static void onClientTick(MinecraftClient client) {
        if (!ClientHandshakeNetworking.isActive()) {
            return; // DESIGN.md §5: a dormant client sends nothing at all.
        }
        if (client.player == null) {
            return; // not in a world yet
        }

        long now = System.currentTimeMillis();
        recordInputActivity(client, now);

        int effectiveBits = currentEffectiveBits();
        Screen screen = client.currentScreen;

        Activity activity;
        ScreenKind screenKind = ScreenKind.UNKNOWN;
        int intensity = 0;

        if (isTypingScreen(screen)) {
            if (hasBit(effectiveBits, PolicyBits.TYPING)) {
                activity = typingActivityFor(screen);
                if (hasBit(effectiveBits, PolicyBits.INTENSITY)) {
                    intensity = TYPING_RATE.intensity(now);
                }
            } else {
                // Policy forbids sharing typing -> don't even classify this
                // as "in a screen"; report nothing unusual for it.
                activity = Activity.NORMAL;
            }
        } else if (screen != null) {
            if (hasBit(effectiveBits, PolicyBits.SCREENS)) {
                activity = Activity.IN_SCREEN;
                if (hasBit(effectiveBits, PolicyBits.SCREEN_DETAIL)) {
                    screenKind = resolveScreenKind(screen);
                }
            } else {
                activity = Activity.NORMAL;
            }
        } else if (hasBit(effectiveBits, PolicyBits.IDLE)
                && IDLE_TIMER.isAfk(now, ServerPolicyState.idleThresholdTicks())) {
            activity = Activity.AFK;
        } else {
            activity = Activity.NORMAL;
        }

        int flags = client.player.isSneaking() ? CueFlags.SNEAKING : 0;
        if (activity == Activity.AFK && IDLE_TIMER.isSleepy(now, ServerPolicyState.idleThresholdTicks())) {
            flags |= CueFlags.SLEEPY;
        }

        // DESIGN.md §7 P4b: feeds Layer 1's showOnSelf (core.client.BillboardCueVisibility)
        // its only possible data source for the local player — see LocalCueState's Javadoc
        // for why core.client.RemoteCueStore can never have this player's own id. Recorded
        // pre-policy-mask (the same activity/screenKind/intensity/flags CueSampler.sample
        // below is about to mask for the wire) and unconditionally every tick, independent
        // of CueSampler's own change-detection/rate-limit gate: a player showing themselves
        // their own current state has nothing to do with how often that state is worth
        // spending network bandwidth to tell someone else.
        LocalCueState.update(new PlayerCue(client.player.getUuid(), activity, screenKind, intensity, flags, now));

        Optional<CueUpdate> update = SAMPLER.sample(activity, screenKind, intensity, flags, effectiveBits, now);
        update.ifPresent(u -> {
            ClientPlayNetworking.send(new SocialCuesPayload(C2SMessages.encode(u)));
            logTransition(u);
        });
    }

    private static Activity typingActivityFor(Screen screen) {
        if (screen instanceof AbstractSignEditScreen) {
            return Activity.TYPING_SIGN;
        }
        if (screen instanceof BookEditScreen) {
            return Activity.TYPING_BOOK;
        }
        // The only remaining member of isTypingScreen(). Decided purely by
        // CommandDraftDetector's keycode-only observation — see its Javadoc
        // for why there is no field-content check here at all, by design.
        return COMMAND_DRAFT.isCommandDraft() ? Activity.TYPING_COMMAND : Activity.TYPING_CHAT;
    }

    private static ScreenKind resolveScreenKind(Screen screen) {
        if (screen instanceof HandledScreen<?> handled) {
            ScreenHandlerType<?> type = handled.getScreenHandler().getType();
            if (type == null) {
                // javap-verified (1.21.11): PlayerScreenHandler (the survival
                // inventory) passes null to the ScreenHandler super
                // constructor's type parameter — it is the one vanilla
                // handler that was never registered, so there is no registry
                // id to hand to ScreenKindMapper at all.
                return ScreenKind.INVENTORY;
            }
            Identifier id = Registries.SCREEN_HANDLER.getId(type);
            return ScreenKindMapper.fromRegistryId(id == null ? null : id.toString());
        }
        if (screen instanceof GameMenuScreen) {
            return ScreenKind.PAUSE;
        }
        if (screen instanceof OptionsScreen) {
            return ScreenKind.SETTINGS;
        }
        if (screen instanceof AdvancementsScreen) {
            return ScreenKind.ADVANCEMENTS;
        }
        return ScreenKind.UNKNOWN;
    }

    // ---- AFK input tracking (DESIGN.md §6: "tuş/fare/hareket/bakış") -------

    private static void recordInputActivity(MinecraftClient client, long nowMs) {
        ClientPlayerEntity player = client.player;
        PlayerInput input = player.getLastPlayerInput();
        boolean keyHeld = input.forward() || input.backward() || input.left() || input.right()
                || input.jump() || input.sneak() || input.sprint();

        GameOptions options = client.options;
        boolean acting = options.attackKey.isPressed() || options.useKey.isPressed();

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYaw();
        float pitch = player.getPitch();

        boolean moved = havePreviousPose && (x != lastX || y != lastY || z != lastZ);
        boolean looked = havePreviousPose && (yaw != lastYaw || pitch != lastPitch);

        lastX = x;
        lastY = y;
        lastZ = z;
        lastYaw = yaw;
        lastPitch = pitch;
        havePreviousPose = true;

        if (keyHeld || acting || moved || looked) {
            IDLE_TIMER.recordActivity(nowMs);
        }
    }

    private static int currentEffectiveBits() {
        return EffectivePolicy.effectiveBits(ServerPolicyState.policyBits(), sharePrefs.prefBits());
    }

    private static boolean hasBit(int bits, int flag) {
        return (bits & flag) != 0;
    }

    /** DESIGN.md §6: one line per Activity/ScreenKind transition, at debug level, never per tick. */
    private static void logTransition(CueUpdate update) {
        if (update.activity() == lastLoggedActivity && update.screenKind() == lastLoggedScreenKind) {
            return;
        }
        lastLoggedActivity = update.activity();
        lastLoggedScreenKind = update.screenKind();
        LOGGER.log(Level.FINE, () -> "socialcues: activity=" + update.activity()
                + " screen=" + update.screenKind() + " intensity=" + update.intensity());
    }
}
