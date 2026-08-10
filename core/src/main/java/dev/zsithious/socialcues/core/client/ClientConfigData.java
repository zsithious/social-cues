package dev.zsithious.socialcues.core.client;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import dev.zsithious.socialcues.core.policy.PolicyBits;

/**
 * DESIGN.md §9 — the client-side render/privacy configuration model: what
 * P4b's render code (layers, scale, opacity, accessibility) and P4a's own
 * wiring (share prefs, mute list) read, and what a future P6 config UI
 * (ModMenu/Cloth) edits. Deliberately pure {@code core} — no Gson, no
 * Minecraft/Bukkit import — so this class owns exactly the defaults, valid
 * ranges, and normalization rules, and is fully JUnit-testable; JSON file
 * I/O is {@code mcshared.config}'s job (Gson is only ever used there, never
 * here).
 *
 * <p><b>Clamp, not reject:</b> unlike e.g. {@code core.relay.RelayConfig}
 * (which throws on an out-of-range value because it is only ever built from
 * already-sanitized admin YAML), every numeric field here is clamped into
 * its valid range by the compact constructor instead. This record is built
 * directly from a user-editable JSON file on disk that can be hand-edited,
 * corrupted, or simply stale after a version upgrade added a field — DESIGN.md's
 * requirement ("bozuksa varsayılana düşer ve asla çökmez") is that a bad
 * value degrade gracefully to something safe, not that construction fail.
 * Structural corruption (unparsable JSON, wrong types) is a different
 * failure mode handled one layer up, in {@code mcshared.config}'s loader,
 * by falling back to {@link #defaults()} entirely.
 *
 * <p>Implements {@link SharePrefsSource} directly — {@link #prefBits()} is
 * this record's own {@code SharePrefsSource.prefBits()} — so a
 * config-holder in {@code mcshared} can hand an instance (or, for a live
 * view that survives config reloads, a small lambda reading the current
 * instance) straight to {@code mcshared.client.ClientCueCapture#setSharePrefs}
 * without an adapter class in between. DESIGN.md §9's last sentence still
 * holds unmodified: the server's {@code policyBits} remains the upper bound
 * ({@code core.policy.EffectivePolicy}), this record only ever describes
 * what the *client* is asking to share.
 *
 * <p><b>{@code shareNothing} — P6 master privacy switch (DESIGN.md §9
 * "Gizlilik", §4 {@code CueFlags.MUTED_SELF}).</b> A single opt-out that
 * suspends all five {@code share*} signals at once, for the person who
 * wants this mod's rendering but never wants their own state relayed to
 * anyone. <em>Suspends</em>, not erases: the five fields keep whatever the
 * user chose, so switching the master back off restores those choices
 * instead of leaving everything silently off — see the compact constructor
 * for why this one rule is enforced in {@link #prefBits()} rather than
 * folded into the fields the way {@code shareScreenDetail} is. It is a
 * <em>sharing</em> switch, never a <em>viewing</em> one:
 * flipping it changes what this client tells the relay about its own
 * player and has no effect whatsoever on what this client renders about
 * other players — that asymmetry is deliberate (a P6 config-screen tooltip
 * must say so explicitly, not just imply it). The compact constructor
 * enforces the flag→booleans direction of that rule once, here, the same
 * way {@code shareScreenDetail = shareScreenDetail && shareScreens} already
 * keeps that narrower rule from needing a second copy anywhere that reads
 * the raw booleans (a config screen, a debug overlay). {@link #prefBits()}
 * enforces the other half — see that method's own Javadoc for why
 * {@code GLOBAL_TIER} is not exempted the way it normally is.
 */
public record ClientConfigData(
        boolean layer1Enabled,
        boolean layer2Enabled,
        boolean layer3Enabled,
        double scale,
        double opacity,
        double maxDistance,
        boolean showOnSelf,
        boolean reducedMotion,
        boolean textOnly,
        boolean shareNothing,
        boolean shareTyping,
        boolean shareScreens,
        boolean shareScreenDetail,
        boolean shareIdle,
        boolean shareVoice,
        Set<String> mutedPlayers) implements SharePrefsSource {

    public static final double MIN_SCALE = 0.25;
    public static final double MAX_SCALE = 4.0;
    public static final double DEFAULT_SCALE = 1.0;

    public static final double MIN_OPACITY = 0.0;
    public static final double MAX_OPACITY = 1.0;
    public static final double DEFAULT_OPACITY = 1.0;

    /** Blocks. DESIGN.md §7 "Mesafeyle solma, config'te maksimum mesafe ve ölçek." */
    public static final double MIN_MAX_DISTANCE = 4.0;
    public static final double MAX_MAX_DISTANCE = 256.0;
    public static final double DEFAULT_MAX_DISTANCE = 32.0;

    public ClientConfigData {
        Objects.requireNonNull(mutedPlayers, "mutedPlayers");
        scale = clamp(scale, MIN_SCALE, MAX_SCALE);
        opacity = clamp(opacity, MIN_OPACITY, MAX_OPACITY);
        maxDistance = clamp(maxDistance, MIN_MAX_DISTANCE, MAX_MAX_DISTANCE);
        // shareScreenDetail only ever means anything when SCREENS itself is
        // shared too (EffectivePolicy already enforces this on the wire: the
        // SCREEN_DETAIL bit is only consulted once activity == IN_SCREEN
        // survives masking) — folding that rule into the model itself keeps
        // it internally consistent for anything that reads the raw booleans
        // directly (a config UI checkbox, a debug screen), so nothing else
        // has to remember "SCREEN_DETAIL implies SCREENS" a second time.
        shareScreenDetail = shareScreenDetail && shareScreens;
        // shareNothing is deliberately NOT folded into the five share* fields the
        // way shareScreenDetail is folded into shareScreens one line above. The
        // two rules look alike and are not: SCREEN_DETAIL without SCREENS is
        // *meaningless* (a detail of something that is never sent), so collapsing
        // it loses nothing, whereas shareNothing is a *suspension* of five
        // preferences the user actually expressed. Forcing them false here would
        // persist that erasure to disk, and turning the master switch back off
        // would leave every signal silently off — the user would have to
        // remember and re-enter choices the mod threw away on their behalf. So
        // the individual fields keep their values, and shareNothing is enforced
        // in the one place that decides what actually leaves this machine:
        // prefBits(), which short-circuits to NONE (plus the explicit
        // CueFlags.MUTED_SELF that mcshared.client.ClientCueCapture stamps on
        // the outgoing update). Nothing else in the mod reads the raw share*
        // booleans — a config screen renders them greyed out but still ticked,
        // which is exactly the state being described.
        mutedPlayers = normalizeMutedPlayers(mutedPlayers);
    }

    /** DESIGN.md §9: every listed field's shipped default. {@code showOnSelf} defaults off per DESIGN.md §7 "Kendi oyuncusu". */
    public static ClientConfigData defaults() {
        return new ClientConfigData(
                true, true, true,
                DEFAULT_SCALE, DEFAULT_OPACITY, DEFAULT_MAX_DISTANCE,
                false,
                false, false,
                false,
                true, true, true, true, true,
                Set.of());
    }

    /**
     * DESIGN.md §9 "Sessize alma listesi" + P4a task note: "karşılaştırma
     * büyük/küçük harf duyarsız olsun." {@code mutedPlayers} is already
     * normalized to lower-case by the compact constructor, so this is a
     * plain set lookup, not a scan.
     */
    public boolean isMuted(String playerName) {
        if (playerName == null) {
            return false;
        }
        return mutedPlayers.contains(playerName.toLowerCase(Locale.ROOT));
    }

    /**
     * DESIGN.md §5's {@code SharePrefs.prefBits} layout, derived from the
     * five signal toggles this class exposes. Two bits DESIGN.md §5 defines
     * but §9 does not list as a separate client-facing toggle are folded
     * into the closest related one rather than left user-configurable here —
     * see the DESIGN.md §9 "P4a uygulama notu" this class's Javadoc points
     * to for why:
     * <ul>
     *   <li>{@code INTENSITY} follows {@code shareTyping} — the cadence
     *       number is not meaningfully more sensitive than the typing status
     *       it is a detail of.</li>
     *   <li>{@code GLOBAL_TIER} is always requested — the same coarse
     *       "activity only" view {@link #defaults()} already exposes today
     *       via {@code SharePrefsSource.allEnabled()}, kept unchanged rather
     *       than silently narrowed by this refactor.</li>
     *   <li>{@code GLOBAL_AFK} follows {@code shareIdle} — not wanting your
     *       idle status shared at all implies not wanting it in the
     *       server-wide tab-list tier either.</li>
     * </ul>
     *
     * <p><b>{@code shareNothing} is the one exception to "{@code GLOBAL_TIER}
     * is always requested" above</b> (P6 §3): the compact constructor has
     * already forced every {@code share*} field to {@code false} by the time
     * this method runs, which alone would still leave {@code GLOBAL_TIER} set
     * — that bit is not gated by any of the five fields the loop below reads,
     * it is unconditional. So {@code shareNothing} is checked here too,
     * short-circuiting to {@link PolicyBits#NONE} before the unconditional
     * bit is ever added. Opting out of every signal is meant to mean opting
     * out of the coarse server-wide activity tier as well, not just the
     * near-range detail — leaving {@code GLOBAL_TIER} on for a "share
     * nothing" client would silently defeat the whole point of the switch.
     */
    @Override
    public int prefBits() {
        if (shareNothing) {
            return PolicyBits.NONE;
        }
        int bits = PolicyBits.NONE;
        if (shareTyping) {
            bits |= PolicyBits.TYPING | PolicyBits.INTENSITY;
        }
        if (shareScreens) {
            bits |= PolicyBits.SCREENS;
        }
        if (shareScreenDetail) {
            bits |= PolicyBits.SCREEN_DETAIL;
        }
        if (shareIdle) {
            bits |= PolicyBits.IDLE | PolicyBits.GLOBAL_AFK;
        }
        if (shareVoice) {
            bits |= PolicyBits.VOICE;
        }
        bits |= PolicyBits.GLOBAL_TIER;
        return bits;
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static Set<String> normalizeMutedPlayers(Set<String> input) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String name : input) {
            if (name == null) {
                continue;
            }
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            normalized.add(trimmed.toLowerCase(Locale.ROOT));
        }
        return Set.copyOf(normalized);
    }
}
