#!/usr/bin/env python3
"""Generates Social Cues' own icon atlas (mc-shared assets).

DESIGN.md §7 / CLEANROOM.md: none of this artwork is derived from WATUT in
any way — every shape below is drawn from scratch by this script using
plain geometric primitives (rectangles, polygons, arcs, ellipses). Keeping
the *generator* in the repo (rather than just the PNG) is the point: it is
the proof that the art is ours and reproducible, not lifted from anywhere.

Layout must stay in lockstep with core.client.CueIconAtlas (the Java side's
single source of truth for "which cell is which"): an 8x8 grid of 16x16
monochrome (white + alpha) cells. Cell N here corresponds exactly to
CueIconAtlas.cellFor(Activity.values()[N]) for N in 0..7 (Activity's
declared order: NORMAL, TYPING_CHAT, TYPING_COMMAND, TYPING_SIGN,
TYPING_BOOK, IN_SCREEN, AFK, SPEAKING), plus cell 8 for
CueIconAtlas.SLEEPY_CELL (the AFK+SLEEPY flag variant). Cells 9-63 are
reserved headroom and are left fully transparent.

Usage: python3 tools/gen_icons.py
Requires Pillow (pip install pillow). Writes
mc-shared/src/main/resources/assets/socialcues/textures/gui/cues.png.
"""

from pathlib import Path

from PIL import Image, ImageDraw

GRID_COLUMNS = 8
GRID_ROWS = 8
CELL_PIXELS = 16
TEXTURE_SIZE = GRID_COLUMNS * CELL_PIXELS

WHITE = (255, 255, 255, 255)
TRANSPARENT = (0, 0, 0, 0)

OUTPUT_PATH = (
    Path(__file__).resolve().parent.parent
    / "mc-shared" / "src" / "main" / "resources"
    / "assets" / "socialcues" / "textures" / "gui" / "cues.png"
)


def new_cell() -> Image.Image:
    return Image.new("RGBA", (CELL_PIXELS, CELL_PIXELS), TRANSPARENT)


def draw_normal(draw: ImageDraw.ImageDraw) -> None:
    # Cell 0, Activity.NORMAL: deliberately blank. "Nothing unusual to
    # report" is exactly what an empty cell means, and a render that ever
    # accidentally shows this cell fails safe (shows nothing) rather than
    # showing a wrong icon.
    return


def draw_typing_chat(draw: ImageDraw.ImageDraw) -> None:
    # Speech bubble outline with a tail, and three dots ("...") inside.
    draw.rounded_rectangle([1, 2, 13, 9], radius=2, outline=WHITE, width=1)
    draw.polygon([(3, 9), (3, 12), (6, 9)], fill=WHITE)
    for x in (4, 7, 10):
        draw.rectangle([x, 5, x + 1, 6], fill=WHITE)


def draw_typing_command(draw: ImageDraw.ImageDraw) -> None:
    # Same bubble as chat, but a "/" mark instead of dots — mirrors
    # Activity.TYPING_COMMAND being "a chat bubble whose first character is
    # a slash", DESIGN.md §6's CommandDraftDetector rule.
    draw.rounded_rectangle([1, 2, 13, 9], radius=2, outline=WHITE, width=1)
    draw.polygon([(3, 9), (3, 12), (6, 9)], fill=WHITE)
    draw.line([(5, 8), (9, 3)], fill=WHITE, width=1)


def draw_typing_sign(draw: ImageDraw.ImageDraw) -> None:
    # A signpost: a board (outline) on a post.
    draw.rectangle([2, 3, 13, 8], outline=WHITE, width=1)
    draw.rectangle([7, 9, 8, 14], fill=WHITE)


def draw_typing_book(draw: ImageDraw.ImageDraw) -> None:
    # An open book: two pages meeting at a spine.
    left_page = [(1, 4), (7, 3), (7, 12), (1, 13)]
    right_page = [(14, 4), (8, 3), (8, 12), (14, 13)]
    draw.polygon(left_page, outline=WHITE)
    draw.polygon(right_page, outline=WHITE)
    draw.line([(7, 3), (8, 3)], fill=WHITE, width=1)


def draw_in_screen(draw: ImageDraw.ImageDraw) -> None:
    # A small monitor/frame: screen rectangle + neck + base.
    draw.rectangle([1, 2, 14, 10], outline=WHITE, width=1)
    draw.rectangle([6, 11, 9, 12], fill=WHITE)
    draw.rectangle([4, 13, 11, 14], fill=WHITE)


def draw_afk(draw: ImageDraw.ImageDraw) -> None:
    # A crescent moon: a filled circle with a second, offset circle erased
    # out of it (drawing fully transparent pixels directly, not blended).
    draw.ellipse([2, 2, 13, 13], fill=WHITE)
    draw.ellipse([5, 1, 16, 12], fill=TRANSPARENT)


def draw_speaking(draw: ImageDraw.ImageDraw) -> None:
    # A speaker cone with two sound-wave arcs.
    draw.polygon([(1, 6), (4, 6), (8, 3), (8, 12), (4, 9), (1, 9)], fill=WHITE)
    draw.arc([7, 4, 11, 11], start=300, end=60, fill=WHITE, width=1)
    draw.arc([9, 2, 14, 13], start=300, end=60, fill=WHITE, width=1)


def draw_sleepy(draw: ImageDraw.ImageDraw) -> None:
    # CueFlags.SLEEPY variant of AFK (DESIGN.md §4): two stacked "Z" zigzags.
    draw.line([(6, 3), (12, 3)], fill=WHITE, width=1)
    draw.line([(12, 3), (6, 8)], fill=WHITE, width=1)
    draw.line([(6, 8), (12, 8)], fill=WHITE, width=1)
    draw.line([(2, 9), (6, 9)], fill=WHITE, width=1)
    draw.line([(6, 9), (2, 13)], fill=WHITE, width=1)
    draw.line([(2, 13), (6, 13)], fill=WHITE, width=1)


# Row-major cell index -> drawing function. Must stay in the same order as
# Activity.values() (see the module docstring) plus one trailing SLEEPY cell.
CELLS = [
    draw_normal,          # 0 Activity.NORMAL
    draw_typing_chat,     # 1 Activity.TYPING_CHAT
    draw_typing_command,  # 2 Activity.TYPING_COMMAND
    draw_typing_sign,     # 3 Activity.TYPING_SIGN
    draw_typing_book,     # 4 Activity.TYPING_BOOK
    draw_in_screen,       # 5 Activity.IN_SCREEN
    draw_afk,             # 6 Activity.AFK
    draw_speaking,        # 7 Activity.SPEAKING
    draw_sleepy,          # 8 CueIconAtlas.SLEEPY_CELL
]


def build_atlas() -> Image.Image:
    atlas = Image.new("RGBA", (TEXTURE_SIZE, TEXTURE_SIZE), TRANSPARENT)
    for index, draw_fn in enumerate(CELLS):
        cell = new_cell()
        draw_fn(ImageDraw.Draw(cell))
        column = index % GRID_COLUMNS
        row = index // GRID_COLUMNS
        atlas.paste(cell, (column * CELL_PIXELS, row * CELL_PIXELS), cell)
    return atlas


def main() -> None:
    atlas = build_atlas()
    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(OUTPUT_PATH)
    print(f"wrote {OUTPUT_PATH} ({atlas.width}x{atlas.height}, {len(CELLS)} cells drawn)")


if __name__ == "__main__":
    main()
