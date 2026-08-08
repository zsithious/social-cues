package dev.zsithious.socialcues.core.client;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import dev.zsithious.socialcues.core.policy.EffectivePolicy;
import dev.zsithious.socialcues.core.protocol.CueBatch;
import dev.zsithious.socialcues.core.protocol.CueUpdate;
import dev.zsithious.socialcues.core.state.Activity;
import dev.zsithious.socialcues.core.state.PlayerCue;
import dev.zsithious.socialcues.core.state.ScreenKind;

/**
 * DESIGN.md §14 P3's actual verification surface: the "should I send a
 * {@code CueUpdate} right now" decision, entirely as pure Java so it is unit
 * testable without booting Minecraft. Three independent rules apply, in
 * order:
 *
 * <ol>
 *   <li><b>Policy masking</b> — the locally observed
 *       activity/screen/intensity/flags are masked through
 *       {@link EffectivePolicy#applyNear} <em>before</em> anything else, using
 *       {@code effectiveBits = policyBits AND prefBits} (DESIGN.md §5). This
 *       is the same masking {@code core.relay.CueRelay} applies server-side —
 *       reused rather than re-implemented so the two can never drift — and it
 *       is what turns a disallowed signal into its least-informative wire
 *       value (e.g. a policy-forbidden {@code TYPING_CHAT} becomes
 *       {@code NORMAL}) before the change-detection step below ever sees it.</li>
 *   <li><b>Change detection</b> — DESIGN.md §5: "Röle her alıcı için son
 *       gönderdiği durumu tutar, değişmeyeni tekrar göndermez." The client
 *       applies the same rule to its own outgoing state: if the masked value
 *       is identical to the last one actually sent, nothing is sent, no
 *       matter how much time has passed.</li>
 *   <li><b>Rate limiting</b> — DESIGN.md §5: "oyuncu başına ≤4
 *       {@code CueUpdate}/saniye." Even when the masked value did change, a
 *       send is suppressed if it would arrive sooner than
 *       {@code minSendIntervalMillis} after the last one. The caller (the
 *       Minecraft-side tick loop) is expected to call {@link #sample} again
 *       on a later tick with the then-current state, so a change that is
 *       held back here is not lost, only delayed until the next tick where
 *       the state still differs from what was last sent.</li>
 * </ol>
 *
 * <p>Not thread-safe, and not itself aware of DESIGN.md §5's dormant/active
 * handshake — callers must not invoke {@link #sample} at all while
 * {@code core.handshake.ClientHandshake} is not {@code ACTIVE}, exactly as
 * DESIGN.md's P3 task note requires ("el sıkışma tamamlanmadıysa (dormant)
 * hiç gönderilmeyecek").
 */
public final class CueSampler {

    /**
     * DESIGN.md §5: the relay accepts at most 4 {@code CueUpdate}/s per
     * player; the client self-limits to the same cadence so its own updates
     * are never the ones the relay has to silently drop.
     */
    public static final long DEFAULT_MIN_SEND_INTERVAL_MILLIS = 250L;

    /**
     * Never sent over the wire (masked entries built here always discard
     * their id — see {@link #sample}), only used as a required, non-null
     * placeholder so {@link EffectivePolicy#applyNear} can be reused as-is.
     */
    private static final UUID SCRATCH_ID = new UUID(0L, 0L);

    private static final long NEVER = Long.MIN_VALUE;

    private final long minSendIntervalMillis;

    private CueBatch.Entry lastSent;
    private long lastSentAtMillis = NEVER;

    public CueSampler() {
        this(DEFAULT_MIN_SEND_INTERVAL_MILLIS);
    }

    public CueSampler(long minSendIntervalMillis) {
        if (minSendIntervalMillis < 0) {
            throw new IllegalArgumentException("minSendIntervalMillis must be >= 0, was " + minSendIntervalMillis);
        }
        this.minSendIntervalMillis = minSendIntervalMillis;
    }

    /**
     * @param activity      locally observed activity, pre-policy
     * @param screen        locally observed screen kind, pre-policy
     * @param intensity     locally observed intensity (0-255), pre-policy
     * @param flags         locally observed flags, pre-policy
     * @param effectiveBits {@link EffectivePolicy#effectiveBits(int, int)} of
     *                      the server's {@code ServerHello.policyBits} and
     *                      the local {@link SharePrefsSource#prefBits()}
     * @param nowMs         current time, caller-supplied so this stays
     *                      fake-clock testable
     * @return the exact {@code CueUpdate} to send now, or empty if nothing
     *         should be sent this tick
     */
    public Optional<CueUpdate> sample(Activity activity, ScreenKind screen, int intensity, int flags,
                                       int effectiveBits, long nowMs) {
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(screen, "screen");

        PlayerCue raw = new PlayerCue(SCRATCH_ID, activity, screen, intensity, flags, nowMs);
        CueBatch.Entry masked = EffectivePolicy.applyNear(raw, effectiveBits);

        if (masked.equals(lastSent)) {
            return Optional.empty();
        }
        if (lastSentAtMillis != NEVER && nowMs - lastSentAtMillis < minSendIntervalMillis) {
            return Optional.empty();
        }

        lastSent = masked;
        lastSentAtMillis = nowMs;
        return Optional.of(new CueUpdate(masked.activity(), masked.screenKind(), masked.intensity(), masked.flags()));
    }

    /**
     * Forgets the last-sent state and rate-limit clock, so the next
     * {@link #sample} call sends unconditionally (subject only to policy
     * masking). Callers should invoke this whenever the handshake leaves
     * {@code ACTIVE} and later re-enters it (e.g. a reconnect, possibly to a
     * server with different policy bits) — the new session must not assume
     * the other end still remembers what a previous session last saw.
     */
    public void reset() {
        lastSent = null;
        lastSentAtMillis = NEVER;
    }
}
