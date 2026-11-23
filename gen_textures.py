#!/usr/bin/env python3
"""
Generates the animated rainbow textures for Endless Portals.

Every texture is a vertical 16 x (16*FRAMES) strip, which is the format Minecraft
uses for animated textures (plus a .png.mcmeta next to it).

All motion uses INTEGER spatial/temporal frequencies, so the pattern tiles
seamlessly in x/y and loops perfectly in time (frame FRAMES-1 flows into frame 0).
"""
import colorsys, json, math, os, random
from PIL import Image

FRAMES = 32
SIZE = 16
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    "src/main/resources/assets/infinity_dimensions/textures/block")
os.makedirs(ROOT, exist_ok=True)

TAU = 2 * math.pi


def hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, max(0, min(1, s)), max(0, min(1, v)))
    return int(r * 255), int(g * 255), int(b * 255)


def write_mcmeta(png_name, frametime, interpolate=True):
    with open(os.path.join(ROOT, png_name + ".mcmeta"), "w") as f:
        json.dump({"animation": {"frametime": frametime, "interpolate": interpolate}}, f, indent=2)
        f.write("\n")


# --------------------------------------------------------------------------- portals
# Same 9 file names the existing models already point at, so no model/blockstate edits.
# Index i = colour index from InfinityPortalBlock.colorIndexForId(); each one is the SAME
# rainbow swirl, shifted in hue by i/9 so different destinations are still tell-apart-able.
PORTAL_FILES = [
    "infinity_portal_arcane_pink",      # color 0
    "infinity_portal_crimson",          # 1
    "infinity_portal_gold",             # 2
    "infinity_portal_infernal_orange",  # 3
    "infinity_portal_silver",           # 4
    "infinity_portal_soul_blue",        # 5
    "infinity_portal_toxic_green",      # 6
    "infinity_portal_void_black",       # 7
    "infinity_portal_void_teal",        # 8
]


def portal_strip(hue_offset):
    # fixed per-pixel sparkle phases so sparkles are stable between frames
    rng = random.Random(1337)
    sparkle = [[rng.random() for _ in range(SIZE)] for _ in range(SIZE)]
    sparkle_on = [[rng.random() < 0.10 for _ in range(SIZE)] for _ in range(SIZE)]

    img = Image.new("RGBA", (SIZE, SIZE * FRAMES))
    for f in range(FRAMES):
        t = f / FRAMES
        for y in range(SIZE):
            for x in range(SIZE):
                u, w = x / SIZE, y / SIZE
                # three interfering waves -> swirling plasma, rising like vanilla portals
                v = (0.50 * math.sin(TAU * (1 * u + 2 * w + 1 * t))
                     + 0.30 * math.sin(TAU * (2 * u - 1 * w - 2 * t))
                     + 0.20 * math.sin(TAU * (3 * u + 3 * w + 1 * t)))
                p = 0.5 + 0.5 * v                               # 0..1

                # smooth diagonal rainbow bands (u+w is integer-periodic -> tiles), gently
                # warped by the plasma so it reads as a swirl instead of noise.
                hue = hue_offset + t + 1.0 * (u + w) + 0.10 * v
                sat = 0.78 + 0.17 * (1 - p)
                val = 0.62 + 0.38 * p
                r, g, b = hsv(hue, sat, val)
                a = int(150 + 85 * p)                            # translucent: 150..235

                # sparkles (integer multiple of t -> loops)
                if sparkle_on[y][x]:
                    ph = (sparkle[y][x] + 2 * t) % 1.0
                    if ph < 0.12:
                        k = 1 - ph / 0.12
                        r = int(r + (255 - r) * k)
                        g = int(g + (255 - g) * k)
                        b = int(b + (255 - b) * k)
                        a = 255
                img.putpixel((x, f * SIZE + y), (r, g, b, a))
    return img


for i, name in enumerate(PORTAL_FILES):
    portal_strip(i / 9.0).save(os.path.join(ROOT, name + ".png"))
    write_mcmeta(name + ".png", frametime=2)

# --------------------------------------------------------------------------- bookshelf
# Vanilla-bookshelf layout: plank rails at rows 0-1, 7-8, 14-15; two rows of book spines.
# Each spine's hue = its position along the shelf + time, so a rainbow RIPPLES along the shelf.
PLANK_DARK = (92, 66, 36)
PLANK_LIGHT = (130, 96, 54)

# (row_start, row_end_inclusive) for the two book rows
BOOK_ROWS = [(2, 6), (9, 13)]


def shelf_side_strip():
    rng = random.Random(7)
    # build spine layout per row: list of (x_start, width, height_trim, row_phase)
    layouts = []
    for (r0, r1) in BOOK_ROWS:
        spines, x = [], 0
        while x < SIZE:
            wdt = rng.choice([1, 2, 2, 3])
            wdt = min(wdt, SIZE - x)
            spines.append((x, wdt, rng.choice([0, 0, 1]), rng.random() * 0.15))
            x += wdt
        layouts.append(spines)

    img = Image.new("RGBA", (SIZE, SIZE * FRAMES))
    for f in range(FRAMES):
        t = f / FRAMES
        oy = f * SIZE
        # planks
        for y in range(SIZE):
            for x in range(SIZE):
                is_rail = y in (0, 1, 7, 8, 14, 15)
                if is_rail:
                    shade = PLANK_LIGHT if (y in (0, 7, 14)) else PLANK_DARK
                    # faint rainbow shimmer travelling along the rails
                    k = 0.5 + 0.5 * math.sin(TAU * (x / SIZE - t))
                    rr, gg, bb = hsv(x / SIZE + t, 0.55, 0.55)
                    col = tuple(int(shade[c] * (1 - 0.35 * k) + (rr, gg, bb)[c] * 0.35 * k) for c in range(3))
                    img.putpixel((x, oy + y), col + (255,))
        # books
        for (r0, r1), spines in zip(BOOK_ROWS, layouts):
            for (sx, wdt, trim, ph) in spines:
                hue = (sx + wdt / 2) / SIZE + t + ph
                for x in range(sx, sx + wdt):
                    for y in range(r0 + trim, r1 + 1):
                        # shade: left edge lit, right edge dark, top band slightly different
                        edge = 1.0 if x == sx else (0.7 if x == sx + wdt - 1 and wdt > 1 else 0.88)
                        band = 1.12 if (y == r0 + trim) else 1.0
                        r, g, b = hsv(hue, 0.85, min(1.0, 0.85 * edge * band))
                        img.putpixel((x, oy + y), (r, g, b, 255))
                    # gold title band on wider spines
                    if wdt >= 2:
                        yy = (r0 + r1) // 2
                        img.putpixel((sx, oy + yy), (235, 205, 110, 255))
            # backdrop behind short books
            for x in range(SIZE):
                for y in range(r0, r1 + 1):
                    if img.getpixel((x, oy + y))[3] == 0:
                        img.putpixel((x, oy + y), (40, 28, 18, 255))
    return img


def shelf_top_strip():
    # top/bottom: dark wood planks with a rainbow ring shimmer around the border
    img = Image.new("RGBA", (SIZE, SIZE * FRAMES))
    rng = random.Random(21)
    grain = [[rng.random() for _ in range(SIZE)] for _ in range(SIZE)]
    for f in range(FRAMES):
        t = f / FRAMES
        for y in range(SIZE):
            for x in range(SIZE):
                base = PLANK_DARK if (y // 4) % 2 == 0 else PLANK_LIGHT
                gshade = 0.9 + 0.2 * grain[y][x]
                col = [base[c] * gshade for c in range(3)]
                border = min(x, y, SIZE - 1 - x, SIZE - 1 - y)
                if border <= 1:
                    # position around the ring (0..1) so the rainbow travels around the edge
                    ang = (math.atan2(y - 7.5, x - 7.5) / TAU) % 1.0
                    r, g, b = hsv(ang + t, 0.85, 0.95 if border == 0 else 0.75)
                    col = [r, g, b]
                img.putpixel((x, f * SIZE + y), tuple(int(c) for c in col) + (255,))
    return img


shelf_side_strip().save(os.path.join(ROOT, "rainbow_bookshelf_side.png"))
write_mcmeta("rainbow_bookshelf_side.png", frametime=3)
shelf_top_strip().save(os.path.join(ROOT, "rainbow_bookshelf_top.png"))
write_mcmeta("rainbow_bookshelf_top.png", frametime=3)

print("textures written to", ROOT)
