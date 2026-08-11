/**
 * DESIGN.md §2/§3/§7 — render adapter for bucket A (MC 1.21–1.21.1, the rows
 * that predate {@code net.minecraft.client.render.entity.state}).
 *
 * <p>Filled in at the end of P7. This is not a port of bucket BC's source: the
 * {@code EntityRenderState} architecture arrived in 1.21.2, so on these two
 * rows the renderer and the model are handed the live entity and every value
 * the newer buckets read off a pre-computed state has to be derived — the
 * measured list is DESIGN.md §7's "Kova A için ölçülmüş render farkları" table
 * and the per-class Javadoc in the two sub-packages.
 */
package dev.zsithious.socialcues.adapter.bucketa;
