/**
 * DESIGN.md §5 "Politika ve paylaşım bitleri" — the 8-bit layout shared by
 * {@code ServerHello.policyBits} and {@code SharePrefs.prefBits}
 * ({@link dev.zsithious.socialcues.core.policy.PolicyBits}), the
 * off/nearby/all AFK visibility conversion
 * ({@link dev.zsithious.socialcues.core.policy.AfkVisibility}), and the
 * send-time permission masking
 * ({@link dev.zsithious.socialcues.core.policy.EffectivePolicy}) that
 * {@code core.relay.CueRelay} applies to every outgoing cue. Pure Java, no
 * Minecraft/Bukkit imports — see DESIGN.md §8's "röle mantığı iki kez
 * yazılmayacak".
 */
package dev.zsithious.socialcues.core.policy;
