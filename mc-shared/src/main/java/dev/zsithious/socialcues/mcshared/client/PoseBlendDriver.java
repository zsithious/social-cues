package dev.zsithious.socialcues.mcshared.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import dev.zsithious.socialcues.core.client.PoseBlend;
import dev.zsithious.socialcues.core.state.PlayerCue;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;

/**
 * DESIGN.md §7 Katman 3, P5a — the bucket-agnostic clock that drives {@code
 * core.client.PoseBlend} for every player in the current world, and the one
 * place a bucket's model mixin asks "what pose is this player mid-way
 * through right now." Bucket-agnostic on purpose (unlike the mixin itself,
 * which is necessarily per-bucket): every bucket that eventually implements
 * Layer 3 reuses this same driver, exactly like {@code LocalCueState}/
 * {@code RemoteCueStoreHolder} are already shared.
 *
 * <p><b>Why a fixed client-tick step, not a render-frame delta (P5a task
 * note):</b> the eventual caller — {@code PlayerEntityModel#setAngles}, one
 * bucket-specific mixin away — is invoked a variable number of times per
 * rendered frame and per render pass (a player's own third-person model, the
 * {@code PlayerListHud} head preview, a mirror/portal, a spectating camera
 * all trigger their own {@code setAngles} call on the same tick). Deriving
 * {@code deltaSeconds} from however many times that happens this frame would
 * make the ease-in/out speed depend on an implementation detail — how many
 * places currently render this player — that has nothing to do with elapsed
 * wall-clock time; two players with an identical cue could visibly ease in
 * at different rates purely because the camera can see one of them twice.
 * The client tick, by contrast, already runs at a fixed 20&nbsp;Hz
 * ({@link ClientTickEvents#END_CLIENT_TICK} — the same event {@link
 * ClientCueCapture} and {@code mcshared.network.ClientHandshakeNetworking}
 * already drive their own per-tick logic from), so advancing {@link
 * PoseBlend} exactly once per tick by exactly {@link #TICK_SECONDS} is both
 * correct (matches real elapsed time on average, exactly like every other
 * tick-driven system in the game) and reproducible: the same cue history
 * always produces the same blend curve, independent of framerate or how many
 * times a frame happens to render this particular player.
 *
 * <p><b>Why {@link #blendFor} is a cheap map lookup and not a fresh call
 * into {@link PoseBlend}:</b> {@link PoseBlend} has no passive "peek the
 * current blend" accessor — {@code update(id, target, dt)} is the only way
 * to read a {@link PoseBlend.Blend} out of it, and calling it also advances
 * time as a side effect. Calling it straight from a renderer (potentially
 * several times per player per frame, per the paragraph above) would
 * silently reintroduce the exact render-pass-count coupling the fixed tick
 * step exists to avoid. This class therefore advances {@link PoseBlend}
 * exactly once per player per client tick and caches each call's result in
 * {@link #latest}; {@link #blendFor} only ever reads that cache, which is
 * why it is safe and cheap to call from as many render passes as a frame
 * happens to need.
 */
public final class PoseBlendDriver {

    private static final Logger LOGGER = Logger.getLogger("socialcues");

    /** DESIGN.md P5a task note: the client tick rate, not a measured render delta. */
    private static final float TICK_SECONDS = 1f / 20f;

    private static final PoseBlend BLEND = new PoseBlend();

    /**
     * This tick's {@link PoseBlend#update} results, keyed by player id —
     * {@link #blendFor}'s only data source. Rebuilt from scratch every tick
     * (not mutated in place) so a player who stops being both "in the
     * world" and "worth animating" in the same tick simply does not appear
     * in the new map, with no separate removal bookkeeping needed here on
     * top of what {@link PoseBlend} itself already tracks internally.
     */
    private static Map<UUID, PoseBlend.Blend> latest = Map.of();

    /**
     * Set once by {@link #tickGuarded} when a tick throws. Deliberately not
     * cleared by {@link #reset}, matching {@code ClientCueCapture
     * .captureDisabledByError}: a driver bug is a property of this build,
     * not of the connection, so rejoining would only re-throw and re-log it.
     */
    private static boolean disabledByError;

    private PoseBlendDriver() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(PoseBlendDriver::tickGuarded);
    }

    /**
     * The pose a renderer should draw for {@code id} right now, if any.
     * Cheap (a single map lookup) and safe to call several times per player
     * per frame — see the class Javadoc for why that matters. Empty for any
     * player {@link PoseBlend} currently has nothing to draw for (no cue,
     * cue is {@code NORMAL}/{@code SPEAKING}, or this driver has not ticked
     * since a fresh join yet).
     */
    public static Optional<PoseBlend.Blend> blendFor(UUID id) {
        return Optional.ofNullable(latest.get(id));
    }

    /**
     * Drops every tracked blend. Called from {@code ClientHandshakeNetworking}
     * at the same two points {@code RemoteCueStoreHolder}/{@code LocalCueState}
     * are already reset (a fresh join and a disconnect) — DESIGN.md §5: a
     * stale, mid-fade pose from a previous session — possibly a different
     * player entirely under a reused UUID — must never survive into a new one.
     */
    public static void reset() {
        BLEND.clear();
        latest = Map.of();
    }

    /**
     * DESIGN.md §11 / P3's {@code ClientCueCapture.tickGuarded} precedent:
     * this runs on the client tick, so anything it throws propagates into
     * {@code MinecraftClient.tick} and takes the whole game down. One
     * throwable disables Layer 3's animation for the rest of the session and
     * logs exactly once, at {@code SEVERE} — never {@code FINE}: the P4 hand
     * test note in DESIGN.md §7 records a bug that hid for an entire session
     * behind a {@code FINE} log, and a driver whose only failure mode is
     * "poses silently stop updating" must not repeat that mistake.
     */
    private static void tickGuarded(MinecraftClient client) {
        if (disabledByError) {
            return;
        }
        try {
            tick(client);
        } catch (Throwable t) {
            disabledByError = true;
            LOGGER.log(Level.SEVERE, "socialcues: pose blend driver threw and has been disabled for "
                    + "this session; layer 3 will stop animating/fading. This is a bug — please report it.", t);
        }
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) {
            return; // Between worlds; reset() (driven by the handshake, not this class) is the deliberate clear point.
        }

        UUID selfId = client.player != null ? client.player.getUuid() : null;
        Map<UUID, PoseBlend.Blend> next = new HashMap<>();
        Set<UUID> seen = new HashSet<>();
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            UUID id = player.getUuid();
            PlayerCue cue = cueFor(id, selfId).orElse(null);
            // Passing null for a player with no cue right now (NORMAL, muted,
            // simply unknown) is exactly how PoseBlend.update eases them back
            // out -- every player in the world is updated every tick, never
            // only the ones that currently have something to show.
            PoseBlend.Blend blend = BLEND.update(id, cue, TICK_SECONDS);
            seen.add(id);
            if (blend != null) {
                next.put(id, blend);
            }
        }
        // PoseBlend only drops a player once update() has eased them to zero, and a
        // player who left the world is never passed to update() again -- so without
        // this their entry would sit in the map at whatever weight they walked away
        // with, for the rest of the session. Everyone still here was just updated,
        // so anything the tracker still holds beyond `seen` is a departure.
        BLEND.retainOnly(seen);
        latest = next;
    }

    /**
     * DESIGN.md §7 P4b uygulama notu (same reasoning {@code
     * CueBillboardRenderer}/{@code PlayerListHudMixin} already rely on):
     * {@code RemoteCueStoreHolder} structurally never has an entry for the
     * local player — the relay never echoes a viewer's own cue back — so the
     * self case has to come from {@link LocalCueState} instead.
     */
    private static Optional<PlayerCue> cueFor(UUID id, UUID selfId) {
        if (id.equals(selfId)) {
            return LocalCueState.get();
        }
        return RemoteCueStoreHolder.get().cueOf(id);
    }
}
