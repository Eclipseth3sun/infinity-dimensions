#!/usr/bin/env python3
"""Generates the mod icon (128x128, pixel-art portal frame + rainbow portal + floating pages).

    python3 tools/gen_icon.py

Writes src/main/resources/assets/<modid>/icon.png, where <modid> is endless_portals once the
rename has been applied (infinity_dimensions before).
"""
import colorsys, math, os, random
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.normpath(os.path.join(HERE, "..", "src/main/resources/assets"))
NS = "endless_portals" if os.path.isdir(os.path.join(RES, "endless_portals")) else "infinity_dimensions"
OUT = os.path.join(RES, NS, "icon.png")
os.makedirs(os.path.dirname(OUT), exist_ok=True)

L = 32          # logical pixel grid; scaled x4 to 128 with nearest-neighbour for the blocky look
S = 4
TAU = 2 * math.pi
rng = random.Random(2026)


def hsv(h, s, v):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


# ---- background: deep violet gradient with faint stars -----------------------------------------
bg = Image.new("RGB", (L, L))
for y in range(L):
    for x in range(L):
        t = y / (L - 1)
        bg.putpixel((x, y), (int(14 + 22 * t), int(8 + 10 * t), int(30 + 36 * (1 - abs(t - 0.5) * 2) + 10)))
for _ in range(26):
    x, y = rng.randrange(L), rng.randrange(L)
    v = rng.randrange(90, 200)
    bg.putpixel((x, y), (v, v, min(255, v + 40)))

# ---- portal geometry (logical px): 6x7 block frame, 3 px per block -----------------------------
BLK = 4
FW, FH = 6 * BLK, 7 * BLK           # 24 x 28
FX, FY = (L - FW) // 2, 2           # top-left of the frame
inner = (FX + BLK, FY + BLK, FX + FW - BLK, FY + FH - BLK)   # x0,y0,x1,y1 (exclusive)

# ---- glow behind the portal (rendered at full res, then composited) ----------------------------
big = bg.resize((L * S, L * S), Image.NEAREST).convert("RGBA")
glow = Image.new("RGBA", big.size, (0, 0, 0, 0))
for y in range(inner[1], inner[3]):
    for x in range(inner[0], inner[2]):
        c = hsv((x + y) / 14.0, 0.85, 1.0)
        for dy in range(S):
            for dx in range(S):
                glow.putpixel((x * S + dx, y * S + dy), c + (255,))
glow = glow.filter(ImageFilter.GaussianBlur(16))
big.alpha_composite(glow)

img = big.resize((L, L), Image.NEAREST)   # back to logical for pixel drawing (glow survives as tint)
img = big.copy()                          # keep full-res glow; draw pixel art on top at scale S


def px(x, y, color):
    for dy in range(S):
        for dx in range(S):
            img.putpixel((x * S + dx, y * S + dy), color + (255,))


# ---- rainbow portal interior ------------------------------------------------------------------
t = 0.18
for y in range(inner[1], inner[3]):
    for x in range(inner[0], inner[2]):
        u, w = x / 10.0, y / 10.0
        v = (0.5 * math.sin(TAU * (u + 2 * w + t)) + 0.3 * math.sin(TAU * (2 * u - w - 2 * t))
             + 0.2 * math.sin(TAU * (3 * u + 3 * w + t)))
        p = 0.5 + 0.5 * v
        hue = t + (x - inner[0]) / 12.0 + (y - inner[1]) / 14.0 + 0.10 * v
        px(x, y, hsv(hue, 0.78 + 0.17 * (1 - p), 0.70 + 0.30 * p))
for _ in range(10):                                   # sparkles
    sx = rng.randrange(inner[0], inner[2]); sy = rng.randrange(inner[1], inner[3])
    px(sx, sy, (255, 255, 255))

# ---- obsidian frame ---------------------------------------------------------------------------
OBS = [(22, 12, 38), (30, 18, 52), (38, 24, 64), (16, 8, 28)]
for by in range(7):
    for bx in range(6):
        on_frame = bx in (0, 5) or by in (0, 6)
        if not on_frame or (bx in (0, 5) and by in (0, 6) and False):
            continue
        if by in (0, 6) and bx in (0, 5):
            pass                                         # corners are drawn like the rest (they exist in-game)
        for dy in range(BLK):
            for dx in range(BLK):
                x, y = FX + bx * BLK + dx, FY + by * BLK + dy
                shade = OBS[(bx * 7 + by * 3 + dx + dy * 2) % 4]
                if dx == 0 or dy == 0:                   # bevel highlight on top/left
                    shade = (min(255, shade[0] + 18), min(255, shade[1] + 12), min(255, shade[2] + 30))
                if dx == BLK - 1 or dy == BLK - 1:       # shadow on bottom/right
                    shade = (shade[0] // 2, shade[1] // 2, shade[2] // 2 + 6)
                px(x, y, shade)
# a few magenta "purple flecks" like real obsidian
for _ in range(10):
    bx, by = rng.choice([(0, rng.randrange(7)), (5, rng.randrange(7)), (rng.randrange(6), 0), (rng.randrange(6), 6)])
    px(FX + bx * BLK + rng.randrange(BLK), FY + by * BLK + rng.randrange(BLK), (120, 60, 190))

# ---- floating pages (the "book" half of the idea) -----------------------------------------------
def page(x, y, tint):
    ink = (tint[0] // 2, tint[1] // 2, tint[2] // 2 + 20)
    for dy in range(4):
        for dx in range(3):
            px(x + dx, y + dy, tint if dy in (0, 3) or dx == 2 else ink if dx == 0 else tint)
    px(x, y, (255, 255, 255))

page(0, 5, (240, 230, 205))
page(29, 9, (232, 224, 255))
page(0, 21, (255, 238, 210))
page(29, 24, (222, 242, 255))

img = img.convert("RGB")
img.save(OUT)
print("wrote", OUT, img.size)
