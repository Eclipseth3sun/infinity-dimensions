<div align="center">

# ᦠ 𝔼𝕟𝕕𝕝𝕖𝕤𝕤 ℙ𝕠𝕣𝕥𝕒𝕝𝕤

**Throw a book into a Nether Portal. Walk into a world written by its words.**

A Fabric port of Minecraft's *20w14∞* April Fools snapshot for modern Minecraft.

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A?style=flat-square)](https://www.minecraft.net)
[![Fabric](https://img.shields.io/badge/Mod%20Loader-Fabric-DBD0B4?style=flat-square)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-25-ED8B00?style=flat-square)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](#license)
[![Download](https://img.shields.io/github/v/release/Eclipseth3sun/infinity-dimensions?label=Download&color=blue&style=flat-square)](https://github.com/Eclipseth3sun/infinity-dimensions/releases/latest)

</div>

---

## Contents

- [Overview](#overview)
- [Installation](#installation)
- [Quick start](#quick-start)
- [Features](#features)
  - [Box of Infinite Books](#box-of-infinite-books)
  - [Infinity Portal](#infinity-portal)
- [How books choose a dimension](#how-books-choose-a-dimension)
- [Named dimensions](#named-dimensions)
- [Procedural terrain styles](#procedural-terrain-styles)
- [Portal colors](#portal-colors)
- [Technical notes](#technical-notes)
- [Building from source](#building-from-source)
- [Troubleshooting](#troubleshooting)
- [Credits](#credits)
- [License](#license)

---

## Overview

In 2020, Mojang's April Fools snapshot **20w14∞** let players throw a written book into a Nether Portal and arrive in a dimension generated from the book's contents. Infinity Dimensions brings that idea back.

- **Deterministic.** The same words always lead to the same dimension, in any world or on any server.
- **Practically unlimited.** Every distinct book text maps to its own procedural dimension.
- **Hand-made easter eggs.** 26 special titles open hand-built dimensions such as a library, a Menger sponge, a llama world and a raid-farm-ready flatland.
- **Persistent.** Dimensions are saved with your world, so you always return to the same terrain.

---

## Installation

| Requirement | Version |
|---|---|
| Minecraft: Java Edition | 26.3 |
| Java | 25 or newer |
| [Fabric Loader](https://fabricmc.net/use/) | 0.20.1 or newer |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.166.2+26.3 or newer |

1. Install Fabric Loader for Minecraft 26.3.
2. Download **Fabric API** and place it in your `mods` folder.
3. Download the latest Infinity Dimensions `.jar` from the [releases page](https://github.com/Eclipseth3sun/infinity-dimensions/releases/latest). If it is inside a `.zip`, extract it first.
4. Put the `.jar` in your `.minecraft/mods` folder and launch the game.

The mod is needed on both client and server. It adds blocks and custom world generators, so a vanilla client cannot join a server that runs it.

---

## Quick start

1. **Craft a Box of Infinite Books** (see [recipe](#box-of-infinite-books)).
2. Place it and **right-click** to get a Written Book.
3. Build and light a standard **Nether Portal**.
4. **Throw the book** into the portal. The portal frame converts to an Infinity Portal.
5. Step through. A return portal waits for you near spawn.

Prefer a specific place? Write a book titled `library`, sign it, and throw that in instead. See [Named dimensions](#named-dimensions).

---

## Features

### Box of Infinite Books

A craftable block that dispenses Written Books. Each book is tied to one dimension.

**Recipe**

```
B B B        B = Book
B S B        S = Bookshelf
B B B
```

**Behavior**

| Action | Result |
|---|---|
| Right-click | Gives a Written Book with a generated title. The book is determined by the block's position, so the same block always gives the same book. |
| Right-click with an **Echo Shard** | **Binds** the box, locking its current seed permanently. A bound box glows. |
| Break a bound box | Drops as a glowing item that remembers its seed. Place it anywhere and it gives the same books. |
| Break an unbound box | Drops with no data. It generates books from wherever it is placed next. |

The box can be broken by hand, and an axe is faster.

### Infinity Portal

A converted Nether Portal that leads to a custom dimension. Each portal takes a color tied to its destination.

**Creating one**

1. Build and light a standard Nether Portal.
2. Get a Written Book, from a Box of Infinite Books or one you wrote yourself.
3. Throw the book into the portal. It is consumed, and the frame blocks convert to an Infinity Portal.

**Redirecting.** Throw a different Written Book into an existing Infinity Portal to point it somewhere new. The color updates to match.

**Return portals.** The first time you travel through a portal, a return portal is generated near the destination's spawn point. Step through it to come home.

Normal Nether Portals are unaffected. Only a thrown Written Book triggers conversion.

---

## How books choose a dimension

When a Written Book enters a portal, the mod picks a destination using the first rule that matches:

1. **Generated book.** A book with exactly 16 pages, each holding two lowercase hex characters, spells out a 32-character dimension ID. This is the format the Box of Infinite Books produces. The pages decide the destination, not the title.
2. **Named dimension.** If the book's title, trimmed and lowercased, exactly matches a [named dimension](#named-dimensions), that dimension is used.
3. **Content hash.** Otherwise, all page text is joined, lowercased and hashed with MD5. The hash becomes the dimension ID. **The title is ignored.**

What this means in practice:

- Two books with different titles but identical pages go to the same dimension.
- Changing a single letter on a page sends you somewhere completely different.
- A book with a blank title is rejected.
- Capitalization does not matter, so `Library`, `LIBRARY` and `library` are the same.

> **Example.** A book whose pages say `the hollow mountain` always opens the same procedural dimension, whatever it is titled and whoever throws it.

---

## Named dimensions

Write the title with a Book and Quill, sign the book, and throw it into a portal. Pages can say anything.

| Title | What you get |
|---|---|
| `ancient` | Deepslate, tuff and moss flat world |
| `ant` | White concrete floor (ant simulation placeholder) |
| `cave` | Dense stone with 3D-carved hollow voids |
| `checkers` | Black and white checkerboard floor, 4×4 squares |
| `chess` | Black and white checkerboard floor, 4×4 squares |
| `clubs` | Black and white checkerboard floor, 4×4 squares |
| `colors` | Four-quadrant floor: blue, red, green, yellow |
| `dark` | Stone below, obsidian surface |
| `farm` | A single bedrock floor with a normal day/night cycle. Built for farms, and pillager outposts spawn for raid farms. Mobs can spawn on bedrock in darkness, so light up your build area. |
| `flat` | Standard grass flat world |
| `floating` | Circular grass islands at y=80, repeating every 32 blocks |
| `gallery` | Infinite 8×8 bookshelf rooms linked by small passages |
| `grid` | Stone pillars on a 4-block grid over open void |
| `holes` | Flat world with circular holes punched through the floor every 48 blocks |
| `inverted` | Stone ceiling overhead, open void below |
| `library` | Infinite 8×8 bookshelf rooms linked by small passages |
| `llama` | Stone below, lime carpet surface |
| `message` | Nearly empty: one bedrock block at the origin |
| `museum` | Infinite 8×8 bookshelf rooms linked by small passages |
| `patterns` | Black and white concrete floor in a bitcount pattern |
| `skygrid` | Sparse grid of single blocks floating in the void |
| `slime` | Solid slime blocks from bedrock up to y=64 |
| `sponge` | Menger sponge fractal in sponge blocks, repeating every 27 blocks |
| `underground` | Dense stone with 3D-carved hollow voids |
| `void` | Single bedrock block at the origin, otherwise empty |
| `wall` | Stone pillars on a 4-block grid over open void |

Named dimensions are always available and never need to be discovered first.

---

## Procedural terrain styles

Any book that is not a named dimension leads to a procedural dimension. Its terrain style is chosen deterministically from the dimension's seed, out of 18 styles:

| Style | Description |
|---|---|
| Flat | Bedrock, stone and a grass surface at y=64 |
| Hills | Gently rolling terrain, y=48 to 80 |
| Mountains | Tall noise-based peaks, y=40 to 140 |
| Ocean | Water-filled basin with the sea floor at y=30 |
| Floating | Disconnected stone islands at y=80 |
| Void | Bedrock floor only, otherwise empty |
| Cave | Mostly solid stone with ellipsoid-carved hollow pockets |
| Desert | Flat sandstone and sand plains |
| Mushroom | Flat mycelium surface |
| Inverted | Stone ceiling slab, open void below |
| Pillars | Tall narrow spires of varied height, separated by void |
| Archipelago | Ocean basin with scattered islands above sea level |
| Canyon | Flat plateau cut by deep, noise-carved chasms |
| Cavern | Dense solid mass with large 3D hollowed voids |
| Mesa | High plateau at y=100 with banded strata and canyon carving |
| Lava Sea | Variable terrain with lava fill at y≤31 |
| Frozen | Ocean-floor terrain filled with packed ice |
| Amplified | Mountains at triple amplitude |

Each dimension also gets a **seed-derived block palette**, with a primary block replacing stone, a surface block and a subsurface block. Two dimensions with the same style can look very different. Bedrock and water are never substituted.

---

## Portal colors

Each Infinity Portal shows one of nine colors, chosen from its destination. The same dimension always gets the same color.

Arcane Pink · Crimson · Gold · Infernal Orange · Silver · Soul Blue · Toxic Green · Void Black · Void Teal

---

## Technical notes

- **Persistence.** Procedural dimensions are saved in world data and re-registered on every server start.
- **Runtime creation.** Dimensions are created while the server runs. Creation requests are queued and processed at the end of the server tick, to avoid modifying the level map while the server iterates over it.
- **Weather.** Infinity dimensions use the overworld's day/night cycle but do not advance the weather themselves. They follow the overworld's weather, which avoids rain flickering when many are loaded.
- **Return portals.** Placed near x=8, z=8 in the destination. Void and floating dimensions get a small stone platform automatically.
- **Custom worldgen.** The mod registers its own `ChunkGenerator` and `BiomeSource` types, plus the `farm_outposts` structure set.
- **Mixins.** Three: `NetherPortalMixin` (book detection), `WeatherCycleMixin` and `MinecraftServerAccessor` (runtime dimension access).

### Project layout

```
src/main/java/com/jrock/infinity/
├── block/       Box of Infinite Books, Infinity Portal, block entities
├── dimension/   Dimension manager, book hashing, named-dimension list, saved data
├── item/        Item registration
├── mixin/       Portal, weather and server-access mixins
└── worldgen/    Chunk generators, biome source, block palette
src/main/resources/
├── assets/infinity_dimensions/   Models, blockstates, textures, lang
└── data/infinity_dimensions/     Dimensions, dimension types, recipe, structure set
```

---

## Building from source

Requirements: **JDK 25**. Gradle is provided by the wrapper.

```bash
git clone https://github.com/Eclipseth3sun/infinity-dimensions.git
cd infinity-dimensions
./gradlew build          # Windows: gradlew.bat build
```

The built jar is written to `build/libs/`. Use the file without the `-sources` suffix.

Versions are set in `gradle.properties`: `minecraft_version`, `loader_version`, `fabric_api_version` and `loom_version`. When porting to a new Minecraft version, update those first, then fix any compile errors in the mixins and worldgen classes. They touch the most vanilla internals.

To test in a dev client, run `./gradlew runClient`.

---

## Troubleshooting

**The game says the mod requires a different Minecraft version.**
Use Minecraft 26.3, Fabric Loader 0.20.1+ and Fabric API 0.166.2+26.3 or newer.

**The game won't start on an older Java.**
Minecraft 26.x requires Java 25. Check which Java your launcher is using.

**Nothing happens when I throw a book in.**
The book must be a *Written Book*, so a Book and Quill has to be signed first. It must have a title, and it must be thrown into a lit Nether Portal.

**My book goes somewhere different than my friend's.**
Only the *page text* decides the destination, apart from the exact named titles. Check for stray spaces, punctuation and line breaks.

**The `farm` dimension has mobs on the floor.**
Mobs spawn on bedrock in the dark. Light up your build area.

**I'm stuck in a void dimension.**
Use the return portal at the destination spawn, which is on an automatic stone platform near x=8, z=8.

Found a bug? Open an [issue](https://github.com/Eclipseth3sun/infinity-dimensions/issues) with your Minecraft, Fabric Loader and Fabric API versions and the `latest.log`.

---

## Credits

- Concept and original implementation: Mojang Studios, in the *20w14∞* April Fools snapshot. This mod is an unofficial fan port and is not affiliated with Mojang or Microsoft.
- Port and mod: **jrock**.
- Built with [Fabric](https://fabricmc.net).

## License

Released under the **MIT License**.
