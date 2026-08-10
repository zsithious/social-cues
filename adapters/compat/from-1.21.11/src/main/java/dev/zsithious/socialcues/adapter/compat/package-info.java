/**
 * DESIGN.md §3/§14 P7 — the <b>compat layer</b>: a second version axis
 * alongside {@code adapters/bucket*}.
 *
 * <p>{@code bucket} groups rows that share a <em>render</em> generation. Some
 * APIs this mod needs move on boundaries that are not those boundaries, and
 * two of them sit in {@code mc-shared}, which by definition has no bucket. So
 * each row also names a {@code compat} generation in {@code versions.json},
 * and {@code mc/mc.gradle.kts} puts that directory on the source path next to
 * the bucket's.
 *
 * <p>Every class here exists in <em>all</em> compat generations under the same
 * name and signatures, differing only in body — the same device the per-bucket
 * {@code socialcues.mixins.json} already uses. Shared and bucket source can
 * therefore call them unconditionally. Each class's own Javadoc carries the
 * {@code javap} measurement of the seam it hides; DESIGN.md §7's "P7 uygulama
 * notu" explains why this axis exists separately from {@code bucket}.
 */
package dev.zsithious.socialcues.adapter.compat;
