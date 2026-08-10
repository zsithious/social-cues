package dev.zsithious.socialcues.mcshared.client;

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.adapter.compat.TypingKeyEvents;
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
import dev.zsithious.socialcues.mcshared.config.ClientConfigState;
import dev.zsithious.socialcues.mcshared.network.ClientHandshakeNetworking;
import dev.zsithious.socialcues.mcshared.network.SocialCuesPayload;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.advancement.AdvancementsScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.GameOptions;
import net.minecraft.registry.Registries;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

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
 *   <li>Otherwise, the ESC/pause menu ({@link GameMenuScreen}) means
 *       {@link Activity#AFK}, immediately — DESIGN.md §7 P5 hand-test fix.
 *       Unlike every other screen, opening the pause menu is treated as
 *       stepping away rather than as attention on a GUI (see {@link
 *       #onClientTick}'s inline comment for the full reasoning).</li>
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

    /**
     * Set once by {@link #tickGuarded} when capture throws. Deliberately not
     * cleared by {@link #reset}: a capture bug is a property of this build, not
     * of the connection, so rejoining would only re-throw and re-log it.
     */
    private static boolean captureDisabledByError;

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
        ClientTickEvents.END_CLIENT_TICK.register(ClientCueCapture::tickGuarded);
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

    /**
     * P6 §4.5's "a config change must not wait out the rate limit" gap: called
     * by {@code mcshared.config.ClientConfigState#set} — the single choke point
     * every config write goes through (see its own Javadoc) — every time the
     * player saves new settings. {@link CueSampler#reset} already exists for
     * exactly this shape of problem (its own Javadoc: "the new session must not
     * assume the other end still remembers what a previous session last saw"),
     * originally written for reconnects; a privacy toggle is the same situation
     * with a different trigger — the relay must not go on assuming it still
     * knows this client's current, just-changed state. Resetting only the
     * sampler (not the rest of {@link #reset()}, which also forgets AFK/pose
     * bookkeeping and the command-draft detector) is deliberate: none of that
     * other state has anything to do with a config save, and clearing it would
     * cost, at worst, one tick's worth of AFK-timer precision for no reason.
     */
    public static void onConfigChanged() {
        SAMPLER.reset();
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
        // DESIGN.md §7 P7: registered through the compat layer, not through
        // ScreenKeyboardEvents directly. fabric-screen-api-v1 changed the
        // callback from (Screen, int key, int scancode, int modifiers) to
        // (Screen, KeyInput) at its 3.x release — the one 1.21.9+ resolves —
        // and this file is compiled by all twelve rows, so it can name neither
        // shape. adapter.compat.TypingKeyEvents normalises both to the GLFW key
        // code, which is the only thing below ever wanted.
        TypingKeyEvents.afterKeyPress(screen, keyCode -> onTypingKeyPress(keyCode, isChat));
    }

    private static void onTypingKeyPress(int keyCode, boolean isChat) {
        long now = System.currentTimeMillis();
        IDLE_TIMER.recordActivity(now);
        if (!hasBit(currentEffectiveBits(), PolicyBits.TYPING)) {
            return;
        }
        // DESIGN.md §10.1: only "a keystroke happened, and was it the slash
        // key" is ever observed here — a GLFW key code, never the field's text
        // (see the class Javadoc and CommandDraftDetector for why that
        // distinction matters and checkNoTextAccess for how it's enforced
        // mechanically). The compat layer deliberately hands over the code
        // alone rather than the platform event object, so this is the only
        // shape available here even by accident.
        TYPING_RATE.recordKeystroke(now);
        if (isChat) {
            COMMAND_DRAFT.onKeyPress(keyCode == GLFW.GLFW_KEY_SLASH);
        }
    }

    private static boolean isTypingScreen(Screen screen) {
        return screen instanceof ChatScreen
                || screen instanceof AbstractSignEditScreen
                || screen instanceof BookEditScreen;
    }

    // ---- per-tick sampling --------------------------------------------------

    /**
     * Capture runs on the client tick, so anything it throws propagates into
     * {@code MinecraftClient.tick} and takes the whole game down with a crash
     * report — which is exactly what a single unreachable-looking null check in
     * {@link #resolveScreenKind} did the first time this mod met a live server.
     * DESIGN.md §11's stance ("a conflict must not crash the mod") is worth
     * strictly more here than any cue is: this mod is cosmetic, and a cosmetic
     * feature has no business ending someone's session. So capture gets exactly
     * one chance — on an unexpected throwable it logs once, with the stack
     * trace, and switches itself off for the rest of the session, leaving a
     * player with no cues instead of no game.
     *
     * <p>This is a backstop, not a licence to stop handling known cases: every
     * throwable that lands here is a bug to fix at its source, and the log line
     * says so.
     */
    private static void tickGuarded(MinecraftClient client) {
        if (captureDisabledByError) {
            return;
        }
        try {
            onClientTick(client);
        } catch (Throwable t) {
            captureDisabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: client cue capture threw and has been disabled for "
                    + "this session; cues will not be sent. This is a bug — please report it.", t);
        }
    }

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
        } else if (screen instanceof GameMenuScreen) {
            // DESIGN.md §7 P5 hand-test fix ("esc menüsündeykende chest guisi
            // açılıyor. escdeyken afkymış gibi çalışsın"): the ESC/pause menu
            // must never surface as Activity.IN_SCREEN -- to anyone watching,
            // a paused player is exactly as absent as an idle one, and the old
            // behaviour drew the held-panel/container-GUI cue over what is, to
            // every other player, someone who is not even looking at the
            // screen. Resolved as AFK immediately (no need to wait out the
            // idle timer -- opening the pause menu is itself an unambiguous
            // "stepped away" signal), gated on the same PolicyBits.IDLE bit
            // every other AFK signal already respects. ScreenKind stays
            // UNKNOWN (the default above): it has no meaning for AFK. This
            // check has to come after the typing-screen branch (a chat/sign/
            // book screen always wins) and before the generic "any other
            // screen" branch below (which would otherwise catch GameMenuScreen
            // too, since it is a Screen).
            activity = hasBit(effectiveBits, PolicyBits.IDLE) ? Activity.AFK : Activity.NORMAL;
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
        //
        // P6 §3/§4.5 (B1): deliberately NOT stamped with CueFlags.MUTED_SELF here even when
        // ClientConfigState.get().shareNothing() is set, unlike the outgoing wire update
        // below. shareNothing is a *sharing* switch, not a *viewing* one (see
        // ClientConfigData's own Javadoc, DESIGN.md §9): it must never change what this
        // client renders about anyone — including, deliberately, itself. LocalCueState
        // exists specifically so showOnSelf shows the player their own true, unmasked state
        // (see that class's Javadoc: "maskeleme başkalarının ne göreceğini korumak için var,
        // kendi gerçek durumunu kendinden gizlemenin bir anlamı yok"). Stamping MUTED_SELF
        // here instead would make BillboardCueVisibility.passesSharedRules bail on the
        // player's own cue too (it treats the flag as an unconditional "never render this"),
        // silently turning "stop telling the relay" into "stop telling yourself" — which
        // nothing in P6 asks for and the spec explicitly rules out.
        LocalCueState.update(new PlayerCue(client.player.getUuid(), activity, screenKind, intensity, flags, now));

        Optional<CueUpdate> update = SAMPLER.sample(activity, screenKind, intensity, flags, effectiveBits, now);
        // P6 §3/§4.5 (B1): the *wire* half of shareNothing — applied to the already-sampled
        // CueUpdate, not folded into `flags` above. prefBits() already forces effectiveBits to
        // PolicyBits.NONE under shareNothing (see ClientConfigData.prefBits's own Javadoc),
        // which alone already collapses `activity` to NORMAL inside CueSampler/
        // EffectivePolicy.applyNear — so this flag changes nothing about the value actually
        // sent today. It is set anyway because it is the *explicit* statement of intent, not a
        // derived one: it is exactly what core.policy.EffectivePolicy#applyNear's own
        // MUTED_SELF branch on the relay reads as its defense-in-depth check (see that
        // method's Javadoc), and that check has had nothing to actually read until now — the
        // relay has been honouring a flag nobody ever set. It has to be applied here, after
        // SAMPLER.sample returns, rather than folded into `flags` as an input to that call:
        // applyNear treats MUTED_SELF as an unconditional "return a fully neutral entry"
        // short-circuit, which zeroes the flags field of that neutral entry too — fed in as an
        // input, the flag would erase itself before ever reaching the wire. OR'd onto the
        // result instead, it survives to the byte the relay actually sees.
        if (ClientConfigState.get().shareNothing()) {
            update = update.map(u -> new CueUpdate(u.activity(), u.screenKind(), u.intensity(),
                    u.flags() | CueFlags.MUTED_SELF));
        }
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
            ScreenHandler handler = handled.getScreenHandler();
            // PlayerScreenHandler (the survival inventory) is the one vanilla
            // handler that was never registered, so it holds a null type. Its
            // *accessor* does not hand that null back: ScreenHandler#getType
            // throws UnsupportedOperationException("Unable to construct this
            // menu by type") instead. P3 read the constructor (which really
            // does pass null) and wrote a `type == null` branch that therefore
            // could never be reached — opening your own inventory crashed the
            // client. Ask the question the accessor cannot answer safely by
            // testing the type of the handler itself, before touching getType.
            if (handler instanceof PlayerScreenHandler) {
                return ScreenKind.INVENTORY;
            }
            // DESIGN.md §7 P5 second hand-test fix ("düz oyuncu envanteri
            // açıkken sandık GUI'si görünüyor"): creative mode's own inventory
            // is a SECOND handler this table can never be asked about safely.
            // javap -c on CreativeInventoryScreen.CreativeScreenHandler's
            // constructor shows the exact same shape as PlayerScreenHandler's
            // -- it calls `ScreenHandler.<init>(null, 0)`, i.e. it was never
            // registered either, so handler.getType() below would throw the
            // identical UnsupportedOperationException and fall into the
            // MODDED branch, which this table's fallback then renders as a
            // small container -- a chest-shaped panel over a player's own
            // creative inventory. The screen (not the handler) is what
            // reliably tells the two apart, the same way PlayerScreenHandler
            // does above -- checked before getType(), same reasoning. No new
            // ScreenKind: a creative inventory is still, fundamentally, the
            // player's own inventory, and the signal this mod shares is
            // already just "in their inventory", not which game mode.
            if (screen instanceof CreativeInventoryScreen) {
                return ScreenKind.INVENTORY;
            }
            ScreenHandlerType<?> type;
            try {
                type = handler.getType();
            } catch (UnsupportedOperationException e) {
                // Any modded handler built without a registered type throws the
                // same way. There is no registry id to map, and DESIGN.md §4
                // already has the bucket for "menu type not recognised": a
                // cosmetic cue is never worth taking the game down for.
                return ScreenKind.MODDED;
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
        GameOptions options = client.options;

        // DESIGN.md §7 P7. This used to read player.getLastPlayerInput(), which
        // does not exist across the version range: net.minecraft.util.PlayerInput
        // only appears in 1.21.2, and ClientPlayerEntity#getLastPlayerInput only
        // in 1.21.6 (both javap-verified over all twelve mapped jars). Rather
        // than shim two more seams, the movement keys are now read the same way
        // the attack/use keys on the next line always were — GameOptions'
        // KeyBinding fields, which javap confirms are present and identically
        // named on every one of the twelve.
        //
        // Not merely equivalent, but the more direct question for this use:
        // DESIGN.md §6 defines AFK as "no key/mouse/movement/look input", i.e.
        // is the player *pressing* anything, which is precisely what
        // KeyBinding#isPressed answers. PlayerInput was a record of the
        // movement intent already sent to the server, one derivation further
        // away. Both go false while a screen is open, and both go false when
        // the player genuinely stops — the moved/looked checks below cover the
        // remaining case (walking under momentum with no key held).
        boolean keyHeld = options.forwardKey.isPressed() || options.backKey.isPressed()
                || options.leftKey.isPressed() || options.rightKey.isPressed()
                || options.jumpKey.isPressed() || options.sneakKey.isPressed()
                || options.sprintKey.isPressed();

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
