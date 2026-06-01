# Infinity Dimensions — Mod Guide

A port of Minecraft's 20w14∞ (April Fools 2020) snapshot for Minecraft **26.1.2** using Fabric.

---

## What the Mod Adds

### Box of Infinite Books
A craftable block that dispenses Written Books. Each book is tied to a specific dimension — throw it into a Nether Portal to open a portal to that dimension.

**Crafting:** Bookshelf surrounded by books (see recipe)

**Behaviour:**
- **Right-click** — gives you a Written Book with a randomly generated title. The title is deterministic based on the block's position, so the same block always gives the same book.
- **Right-click with an Echo Shard** — *binds* the box, locking in its current seed permanently. A bound box glows. When broken, a bound box drops as a glowing item that remembers its seed, so you can re-place it anywhere and still get the same books.
- An unbound box dropped from breaking carries no data and will generate books based on wherever it is re-placed.

---

### Infinity Portal
A converted Nether Portal that leads to a custom dimension. Each portal has a unique color matching its destination.

**How to create one:**
1. Build a standard Nether Portal and light it.
2. Write or obtain a Written Book.
3. Throw the book into the portal (drop it while standing inside, or toss it through).
4. The portal frame converts — the blocks turn into an Infinity Portal in a color unique to that dimension.

**Redirecting a portal:**
Throw a different Written Book into an existing Infinity Portal to redirect it to a new dimension. The portal color will update to match the new destination.

**Return portals:**
The first time you travel through an Infinity Portal, a return portal is automatically generated near the spawn point of the destination dimension. Step through it to come back.

---

## Getting Specific Dimensions

There are three ways a book can target a dimension:

### 1. Named Dimensions (Exact Title Match)
If a Written Book's title exactly matches one of the names below (case-insensitive), it will always go to that specific hand-crafted dimension. Write the title yourself using a Book and Quill, sign it, then throw it into a portal.

| Title | What it is |
|-------|------------|
| `ancient` | Deepslate/tuff/moss flat world |
| `ant` | White concrete floor (ant simulation placeholder) |
| `cave` | Dense stone with 3D-carved hollow voids |
| `checkers` | Black and white checkerboard floor (4×4 squares) |
| `chess` | Black and white checkerboard floor (4×4 squares) |
| `clubs` | Black and white checkerboard floor (4×4 squares) |
| `colors` | Four-quadrant floor: blue, red, green, yellow |
| `dark` | Stone below, obsidian surface |
| `farm` | Single bedrock floor with a normal day/night cycle — ideal for building farms. Pillager outposts spawn naturally for raid farms. Note: mobs can spawn on bedrock in darkness, so light up your build areas |
| `flat` | Standard grass flat world |
| `floating` | Circular grass islands floating at y=80, repeating every 32 blocks |
| `gallery` | Infinite bookshelf rooms (8×8 repeating) connected by small passages |
| `grid` | Stone pillars on a 4-block grid, open void between |
| `holes` | Flat world with circular holes punched through the floor every 48 blocks |
| `inverted` | Stone ceiling overhead, open void below |
| `library` | Infinite bookshelf rooms (8×8 repeating) connected by small passages |
| `llama` | Stone below, lime carpet surface |
| `message` | Nearly empty — one bedrock block at origin |
| `museum` | Infinite bookshelf rooms (8×8 repeating) connected by small passages |
| `patterns` | Bitcount-pattern black and white concrete floor |
| `skygrid` | Sparse grid of single blocks floating in the void |
| `slime` | Solid slime blocks from bedrock to y=64 |
| `sponge` | Menger sponge fractal in sponge blocks, repeating every 27 blocks |
| `underground` | Dense stone with 3D-carved hollow voids |
| `void` | Single bedrock block at origin, otherwise empty |
| `wall` | Stone pillars on a 4-block grid, open void between |

> **Tip:** Titles are matched after trimming whitespace and converting to lowercase, so `Library`, `LIBRARY`, and `library` all go to the same place.

---

### 2. Box of Infinite Books (Generated Books)
Books dispensed by the Box of Infinite Books store the dimension ID directly in their pages — 16 pages, each containing 2 hex characters, which together form a 32-character ID. It is the **page content** that determines the destination, not the title. The title is just a human-readable label; renaming the book would not change where the portal goes.

You don't need to do anything special with these books; just throw them in a portal.

---

### 3. Hand-Written Books (Book and Quill)
Sign a Book and Quill and throw it into a portal. The mod checks the title first:

- **If the title exactly matches a named dimension** (e.g. the title is just `library` and nothing else) → goes to that easter-egg dimension.
- **Otherwise** → the page contents are hashed to produce the dimension ID. The title is ignored entirely.

This means for any book that isn't a named dimension:
- **Whatever you write on the pages determines the dimension**, regardless of the title.
- Two books with different titles but identical page content go to the same dimension.
- The same page content always opens the same portal to the same dimension, across any world or server.
- The resulting dimension is one of 18 procedurally generated terrain styles (see below).

**Example:** A book whose pages say `the hollow mountain` will always go to the same procedural dimension no matter what its title is, or who throws it.

---

## Procedural Dimension Terrain Styles

Procedural dimensions (those reached by hashed titles or Box books) use one of 18 terrain styles, chosen deterministically from the dimension's seed:

| Style | Description |
|-------|-------------|
| Flat | Bedrock + stone + grass surface at y=64 |
| Hills | Gently rolling terrain, y=48–80 |
| Mountains | Tall noise-based peaks, y=40–140 |
| Ocean | Water-filled basin, sea floor at y=30 |
| Floating | Disconnected stone islands at y=80 |
| Void | Bedrock floor only, otherwise empty |
| Cave | Mostly solid stone with ellipsoid-carved hollow pockets |
| Desert | Flat sandstone and sand plains |
| Mushroom | Flat mycelium surface |
| Inverted | Stone ceiling slab, open void below |
| Pillars | Tall narrow spires of varied height, separated by void |
| Archipelago | Ocean basin with scattered islands above sea level |
| Canyon | Flat plateau with deep noise-carved chasms |
| Cavern | Dense solid mass with large 3D hollowed voids |
| Mesa | High plateau (y=100) with banded strata and canyon carving |
| Lava Sea | Variable terrain with lava fill at y≤31 |
| Frozen | Ocean floor terrain with solid packed-ice fill above |
| Amplified | Extreme mountains — same shape as Mountains but ×3 amplitude |

All procedural dimensions use a palette of blocks derived from the seed, so the exact blocks used will vary between dimensions even within the same style.

---

## Portal Colors

Each Infinity Portal displays one of 9 colors based on its destination dimension. The color is deterministic — the same dimension always gets the same color:

- Arcane Pink
- Crimson
- Gold
- Infernal Orange
- Silver
- Soul Blue
- Toxic Green
- Void Black
- Void Teal

---

## Notes

- Procedural dimensions are saved to the world and persist across restarts — you will always return to the same generated terrain.
- Named dimensions are always available and do not need to be "discovered" first.
- The Nether Portal itself is unaffected — only a thrown Written Book triggers the conversion. Walking through a normal Nether Portal works as usual.
- Return portals are placed near world spawn (x=8, z=8) in the destination dimension. On void or floating-island dimensions where there is no ground, a small stone platform is generated automatically.
