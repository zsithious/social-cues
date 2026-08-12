#!/usr/bin/env python3
"""Generates Social Cues' mod/project icon.

DESIGN.md §13 / CLEANROOM.md: same rule as tools/gen_icons.py — every shape
here is drawn from scratch with plain geometric primitives, and the
*generator* lives in the repo rather than just the PNG, so the artwork is
demonstrably ours and reproducible rather than lifted from anywhere.

Deliberately pixel art on a 32x32 logical grid, scaled up with
nearest-neighbour: it matches both Minecraft's own visual language and the
16x16 cue atlas this mod already draws (tools/gen_icons.py), and it stays
crisp at every size instead of going soft the way a downsampled smooth
render would. The subject is the typing cue's speech bubble, which is the
one glyph that says "this mod tells you what people are doing" on its own
at 32 pixels in a mod list.

Usage: python3 tools/gen_mod_icon.py
Requires Pillow. Writes:
  mc-shared/src/main/resources/assets/socialcues/icon.png   (128x128, in-jar)
  branding/icon-512.png                                     (512x512, store page)
"""

from pathlib import Path

from PIL import Image, ImageDraw

GRID = 32

REPO_ROOT = Path(__file__).resolve().parent.parent
JAR_ICON_PATH = (
    REPO_ROOT / "mc-shared" / "src" / "main" / "resources"
    / "assets" / "socialcues" / "icon.png"
)
STORE_ICON_PATH = REPO_ROOT / "branding" / "icon-512.png"

TRANSPARENT = (0, 0, 0, 0)
# A cool slate that reads as "UI chrome" rather than as any in-game material,
# so the icon does not get mistaken for a block/item mod at thumbnail size.
BACKDROP = (38, 42, 54, 255)
BACKDROP_EDGE = (58, 64, 82, 255)
BUBBLE = (245, 247, 250, 255)
# The three dots carry the one idea the whole mod is about -- that the cue is
# live and has a cadence -- so they are the only place colour is spent.
DOT_ACTIVE = (126, 200, 227, 255)
DOT_IDLE = (120, 130, 150, 255)


def rounded_square(draw: ImageDraw.ImageDraw) -> None:
    """Backdrop: a rounded square with a one-pixel lighter edge."""
    draw.rounded_rectangle([0, 0, GRID - 1, GRID - 1], radius=6, fill=BACKDROP_EDGE)
    draw.rounded_rectangle([1, 1, GRID - 2, GRID - 2], radius=5, fill=BACKDROP)


def speech_bubble(draw: ImageDraw.ImageDraw) -> None:
    """The body of the bubble plus the tail hanging off its lower left."""
    draw.rounded_rectangle([5, 6, 26, 19], radius=4, fill=BUBBLE)
    # Tail drawn as a triangle rather than a rotated shape: on a pixel grid a
    # polygon is exact, and anything rotated would land on half pixels.
    # Kept narrow -- a wide wedge stops reading as a tail and starts
    # reading as a folded corner.
    draw.polygon([(10, 18), (10, 25), (16, 18)], fill=BUBBLE)


def cadence_dots(draw: ImageDraw.ImageDraw) -> None:
    """
    Three 3x3 dots. The middle one is lit and the outer two are not, which
    reads as a cadence caught mid-beat rather than as a static "..." -- the
    same idea CueIconMotion animates in game.
    """
    for x, colour in ((9, DOT_IDLE), (14, DOT_ACTIVE), (19, DOT_IDLE)):
        draw.rectangle([x, 11, x + 2, 13], fill=colour)


def build_icon() -> Image.Image:
    icon = Image.new("RGBA", (GRID, GRID), TRANSPARENT)
    draw = ImageDraw.Draw(icon)
    rounded_square(draw)
    speech_bubble(draw)
    cadence_dots(draw)
    return icon


def main() -> None:
    icon = build_icon()
    for path, size in ((JAR_ICON_PATH, 128), (STORE_ICON_PATH, 512)):
        path.parent.mkdir(parents=True, exist_ok=True)
        # NEAREST on purpose: this is pixel art, and every other filter would
        # blur the one-pixel edge the backdrop is built from.
        icon.resize((size, size), Image.NEAREST).save(path)
        print(f"wrote {path.relative_to(REPO_ROOT)} ({size}x{size})")


if __name__ == "__main__":
    main()
