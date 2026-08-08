/**
 * DESIGN.md §2/§3/§7 — render adapter for bucket D (MC 1.21.9-1.21.11,
 * GUI/render layer break). This is the primary target
 * (client Fabric 1.21.11 / server Leaf 1.21.11, DESIGN.md §1) and the
 * bucket implemented first per §2's instruction.
 *
 * <p>P4b (Katman 1 billboard + Katman 2 tab list, DESIGN.md §7) is the first
 * real content: {@code render} holds the non-mixin glue (the Layer 1 feature
 * renderer and its registration), {@code mixin} holds the three mixins
 * (player render state UUID capture ×2, the Layer 2 tab list icon). P5
 * (Layer 3 — pose/animation) still to come.
 */
package dev.zsithious.socialcues.adapter.bucketd;
