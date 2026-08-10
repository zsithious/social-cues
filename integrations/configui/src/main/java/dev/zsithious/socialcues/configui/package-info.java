/**
 * DESIGN.md §9 / §14 P6 — the in-game config screen, and the only place in
 * this repository allowed to import Cloth Config or ModMenu.
 *
 * <p><b>Why this is its own source directory instead of living in
 * {@code mc-shared}.</b> {@code mc-shared/src/main/java} is absorbed verbatim
 * by all twelve generated {@code :mc:<version>} projects (see
 * {@code settings.gradle.kts}), but {@code versions.json} pins {@code
 * modMenu}/{@code clothConfig} for the 1.21.11 row only — DESIGN.md §14's
 * deliberate order is that 1.21.11 goes end-to-end first (P0–P6) and the
 * other eleven rows follow in P7. A Cloth import inside {@code mc-shared}
 * would therefore fail to compile on eleven rows that have no Cloth on their
 * classpath. So this directory is added to a row's source set by {@code
 * mc/mc.gradle.kts} <em>only</em> when that row pins the dependency, exactly
 * like {@code adapters/bucket*} is added only for that row's bucket.
 *
 * <p>Unlike the buckets, this directory is <b>not</b> version-specific:
 * Cloth's {@code ConfigBuilder}/{@code ConfigEntryBuilder} API is stable
 * across the whole 1.21.x range, so P7 is expected to add {@code modMenu}/
 * {@code clothConfig} columns to the remaining rows and reuse this source
 * unchanged, not to fork it per bucket.
 *
 * <p><b>Both entrypoints are conditional too.</b> {@code fabric.mod.json} is
 * a single shared file, so its {@code client} and {@code modmenu} entrypoint
 * lists are {@code processResources} placeholders that {@code mc.gradle.kts}
 * fills per row — a row without this directory ends up with no entrypoint
 * naming a class it does not contain. That is the same trap the per-bucket
 * {@code socialcues.mixins.json} already had to solve in P4 (DESIGN.md §7).
 *
 * <p><b>Reaching the screen.</b> ModMenu is a soft dependency and
 * deliberately never bundled (shipping a mod-list UI inside a cue mod would
 * be hostile), so it cannot be the only way in: {@link
 * dev.zsithious.socialcues.configui.ConfigUiClientEntrypoint} also registers
 * a client-side {@code /socialcues config} command. Cloth itself <em>is</em>
 * bundled (jar-in-jar), so that path always works.
 */
package dev.zsithious.socialcues.configui;
