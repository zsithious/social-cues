#!/usr/bin/env python3
"""Generates Social Cues' own flat-white fill texture (mc-shared assets).

DESIGN.md §7 P5b / CLEANROOM.md: the held-panel renderer needs a plain,
untextured-looking quad for the typing pose's dark chat-box background (the
in-screen pose instead samples a real vanilla GUI texture, see
core.client.ScreenPanelTextures — nothing to generate for that case). Vanilla
ships no small flat-white texture Social Cues could legitimately reuse for
this (checked: no `textures/misc/white.png` or equivalent exists in the
1.21.11 client jar), and `submitCustom`'s vertex format is always textured
(DESIGN.md §7/§11: only vanilla RenderLayer/VertexConsumer, no custom GL
state, so there is no untextured "flat colour" draw path available here the
way `DrawContext.fill` has for 2D GUI code). The standard, and here the only
clean-room-safe, way to draw a flat-coloured quad through a textured pipeline
is to sample a solid white texel and let per-vertex `.color(r,g,b,a)` tint it
— so this generates exactly that: a tiny, fully-opaque white square, as
unoriginal and reproducible an asset as exists, drawn fresh by this script
rather than lifted from anywhere.

Usage: python3 tools/gen_panel_fill.py
Requires Pillow (pip install pillow). Writes
mc-shared/src/main/resources/assets/socialcues/textures/gui/panel_fill.png.
"""

from pathlib import Path

from PIL import Image

SIZE = 4
WHITE = (255, 255, 255, 255)

OUTPUT_PATH = (
    Path(__file__).resolve().parent.parent
    / "mc-shared" / "src" / "main" / "resources"
    / "assets" / "socialcues" / "textures" / "gui" / "panel_fill.png"
)


def main() -> None:
    image = Image.new("RGBA", (SIZE, SIZE), WHITE)
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUTPUT_PATH)
    print(f"wrote {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
