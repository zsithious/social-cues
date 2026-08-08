package dev.zsithious.socialcues.core.policy;

import java.util.Objects;
import java.util.Optional;

import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.CueFlags;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §5: "Etkin izin = {@code policyBits AND prefBits}. ... Röle,
 * etkin izne uymayan alanları gönderim anında sıfırlar — istemcinin
 * kurallara uymasına güvenilmez." This class is that sanitization step. It
 * is pure and stateless on purpose: {@code core.relay.CueRelay} calls it
 * once per (viewer, target) pair per tick, so it must not depend on
 * anything but its arguments.
 *
 * <p>Two masking levels exist because DESIGN.md §5 defines two broadcast
 * tiers with different granularity:
 * <ul>
 *   <li>{@link #applyNear} — the near-tier view: full detail, but every
 *       field not permitted by the effective bits is reset to its
 *       least-informative value (never omitted — the wire shape is fixed).</li>
 *   <li>{@link #applyGlobalCoarse} — the global/tab-list tier: takes an
 *       already near-masked entry and coarsens it further (only
 *       {@code activity} ever survives), gated by {@link PolicyBits#GLOBAL_TIER}
 *       and {@link PolicyBits#GLOBAL_AFK}.</li>
 * </ul>
 */
public final class EffectivePolicy {

    private EffectivePolicy() {
    }

    /** DESIGN.md §5: "Etkin izin = policyBits AND prefBits." */
    public static int effectiveBits(int policyBits, int prefBits) {
        return policyBits & prefBits;
    }

    /**
     * Masks one player's real {@link PlayerCue} down to what {@code effectiveBits}
     * permits, producing the wire-shaped entry a near-tier {@code CueBatch}
     * would carry for it (id + activity + screenKind + intensity + flags).
     *
     * <p>DESIGN.md §4: {@link CueFlags#MUTED_SELF} means "gönderen tüm
     * paylaşımı kapattı (röle temizler)" — handled first and unconditionally,
     * before any policy-bit logic, because it is the sender's own override,
     * not a permission question.
     */
    public static CueBatch.Entry applyNear(PlayerCue real, int effectiveBits) {
        Objects.requireNonNull(real, "real");
        if (real.hasFlag(CueFlags.MUTED_SELF)) {
            return neutral(real.id());
        }

        Activity activity = real.activity();
        if (isTyping(activity) && !has(effectiveBits, PolicyBits.TYPING)) {
            activity = Activity.NORMAL;
        }
        if (activity == Activity.IN_SCREEN && !has(effectiveBits, PolicyBits.SCREENS)) {
            activity = Activity.NORMAL;
        }
        if (activity == Activity.AFK && !has(effectiveBits, PolicyBits.IDLE)) {
            activity = Activity.NORMAL;
        }
        if (activity == Activity.SPEAKING && !has(effectiveBits, PolicyBits.VOICE)) {
            activity = Activity.NORMAL;
        }

        ScreenKind screen = (activity == Activity.IN_SCREEN && has(effectiveBits, PolicyBits.SCREEN_DETAIL))
                ? real.screen()
                : ScreenKind.UNKNOWN;

        int intensity = has(effectiveBits, PolicyBits.INTENSITY) ? real.intensity() : 0;

        int flags = real.flags();
        if (activity != Activity.AFK) {
            // SLEEPY only means anything alongside AFK; DESIGN.md §4 defines
            // it as "AFK eşiğinin 2 katını geçti", so once AFK itself is
            // masked away, leaking SLEEPY would be a second, subtler way to
            // reveal idle state through the back door.
            flags &= ~CueFlags.SLEEPY;
        }

        return new CueBatch.Entry(real.id(), activity, screen, intensity, flags);
    }

    /**
     * Coarsens an already near-masked entry for the global/tab-list tier.
     * DESIGN.md §5: "Global katman: ... sadece activity (kaba)." Returns
     * {@link Optional#empty()} when {@link PolicyBits#GLOBAL_TIER} is not
     * set in {@code effectiveBits} — meaning the player has no presence in
     * the global tier at all, not even a neutral one.
     */
    public static Optional<CueBatch.Entry> applyGlobalCoarse(CueBatch.Entry nearMasked, int effectiveBits) {
        Objects.requireNonNull(nearMasked, "nearMasked");
        if (!has(effectiveBits, PolicyBits.GLOBAL_TIER)) {
            return Optional.empty();
        }
        Activity activity = nearMasked.activity();
        if (activity == Activity.AFK && !has(effectiveBits, PolicyBits.GLOBAL_AFK)) {
            activity = Activity.NORMAL;
        }
        return Optional.of(new CueBatch.Entry(nearMasked.id(), activity, ScreenKind.UNKNOWN, 0, 0));
    }

    private static CueBatch.Entry neutral(java.util.UUID id) {
        return new CueBatch.Entry(id, Activity.NORMAL, ScreenKind.UNKNOWN, 0, 0);
    }

    private static boolean isTyping(Activity activity) {
        return activity == Activity.TYPING_CHAT
                || activity == Activity.TYPING_COMMAND
                || activity == Activity.TYPING_SIGN
                || activity == Activity.TYPING_BOOK;
    }

    private static boolean has(int bits, int flag) {
        return (bits & flag) != 0;
    }
}
