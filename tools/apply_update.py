#!/usr/bin/env python3
"""
Endless Portals - one-shot update script.

    python3 tools/apply_update.py            # run from the repo root
    python3 tools/apply_update.py --dry-run  # show what would change, write nothing

What it does (every step is idempotent: running it twice is safe):

  1. Fixes the build metadata that made the mod refuse to load:
       fabric.mod.json pinned  "minecraft": "26.1.2"  while the project targeted 26.2 / 26.3.
  2. Ports the Java to Minecraft 26.3 (skip with --target 26.2):
       fillFromNoise -> buildTerrain, getNoiseBiome -> createResolver, PushReaction.BLOCK ->
       IMMOVEABLE, playerDestroy(ServerLevel, ServerPlayer), Player#drop -> spawnAtLocation, ...
  3. Wires in the new features that ship as new files in this update:
       Rainbow Infinite Bookshelf (ModBlocks, lang, axe tag) and PortalBreakHandler.
  4. Hooks in the new chaotic terrain generator (ChaosTerrain) and the mod icon.
  5. Renames everything to Endless Portals: classes, constants, assets, display names AND the ids
       (mod id infinity_dimensions -> endless_portals, block id infinity_portal -> endless_portal,
        InfinityDimensions -> EndlessPortals).

  ! The id rename changes the namespace of saved data, so portals / dimensions created by an older
    version are not recognised in old worlds. Pass --keep-ids to rename only classes and display
    names and keep the old ids (fully save-compatible).

Every file is edited all-or-nothing: if an anchor can't be found the file is left untouched
and reported as FAILED, so a half-applied edit can never corrupt it.
"""
import argparse
import json
import pathlib
import re
import sys

p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
p.add_argument("root", nargs="?", default=".", help="repo root (default: current directory)")
p.add_argument("--dry-run", action="store_true", help="report only; write nothing")
p.add_argument("--target", choices=["26.3", "26.2"], default="26.3",
               help="Minecraft version to target (26.2 skips the API port and version bumps)")
p.add_argument("--keep-ids", action="store_true",
               help="keep the old mod id / block id (save-compatible); rename classes and display names only")
p.add_argument("--rename-registry-id", action="store_true", help=argparse.SUPPRESS)  # old flag, now the default
args = p.parse_args()

ROOT = pathlib.Path(args.root).resolve()
JAVA = ROOT / "src/main/java/com/jrock/infinity"
RES = ROOT / "src/main/resources"
if not JAVA.is_dir():
    sys.exit(f"error: {JAVA} not found - run this from the repository root")

RENAME_IDS = not args.keep_ids

ok, skipped, failed = [], [], []


# --------------------------------------------------------------------------------------- io helpers
class Doc:
    """Text file that is matched with \\n newlines but written back with its original ones."""

    def __init__(self, path):
        self.path = pathlib.Path(path)
        raw = self.path.read_bytes().decode("utf-8")
        self.crlf = "\r\n" in raw
        self.text = raw.replace("\r\n", "\n")
        self.orig = self.text

    def save(self):
        if self.text == self.orig:
            return False
        if not args.dry_run:
            out = self.text.replace("\n", "\r\n") if self.crlf else self.text
            self.path.write_bytes(out.encode("utf-8"))
        return True


def rel(path):
    return str(pathlib.Path(path).relative_to(ROOT)).replace("\\", "/")


class Fail(Exception):
    pass


def sub1(doc, desc, pattern, repl, flags=re.S):
    """Regex-replace exactly one occurrence or raise Fail."""
    new, n = re.subn(pattern, repl, doc.text, count=1, flags=flags)
    if n < 1:
        raise Fail(f"anchor not found: {desc}")
    doc.text = new


def lit1(doc, desc, old, new):
    if old not in doc.text:
        raise Fail(f"anchor not found: {desc}")
    doc.text = doc.text.replace(old, new, 1)


def pick(*candidates):
    """First candidate that exists (a file may already have been moved by an earlier run)."""
    for c in candidates:
        if pathlib.Path(c).exists():
            return pathlib.Path(c)
    return pathlib.Path(candidates[0])


def run_file(path, fn, already=None):
    """Run fn(doc) on a file all-or-nothing; `already` = marker meaning 'nothing to do'."""
    path = pathlib.Path(path)
    label = rel(path)
    if not path.exists():
        failed.append((label, "file not found"))
        return
    try:
        doc = Doc(path)
        if already and already(doc.text):
            skipped.append((label, "already applied"))
            return
        fn(doc)
        if doc.save():
            ok.append((label, "updated" + (" (dry run)" if args.dry_run else "")))
        else:
            skipped.append((label, "no change needed"))
    except Fail as e:
        failed.append((label, str(e)))
    except Exception as e:  # noqa: BLE001
        failed.append((label, f"{type(e).__name__}: {e}"))


# ------------------------------------------------------------------ 1+2. build metadata and 26.3 port
TARGET = args.target

if TARGET == "26.3":
    def gradle_props(d):
        for key, val in [
            ("minecraft_version", "26.3"),
            ("loader_version", "0.20.1"),
            ("loom_version", "1.19-SNAPSHOT"),
            ("fabric_api_version", "0.166.2+26.3"),
            ("mod_version", "1.2.0+mc26.3"),
            ("archives_base_name", "endless-portals"),
        ]:
            sub1(d, key, rf"^{key}=[^\n]*", f"{key}={val}", re.M)
        d.text = re.sub(r"# Fabric API 0\.\d+\.\d+ released[^\n]*",
                        "# Versions: keep in sync with https://fabricmc.net/develop", d.text)
        d.text = re.sub(r"# Java version[^\n]*", "# Java version (Minecraft 26.x requires Java 25)", d.text)

    run_file(ROOT / "gradle.properties", gradle_props)

    def wrapper(d):
        sub1(d, "gradle distribution", r"gradle-[\d.]+-bin\.zip", "gradle-9.8.2-bin.zip")

    run_file(ROOT / "gradle/wrapper/gradle-wrapper.properties", wrapper,
             already=lambda t: "gradle-9.8.2-bin.zip" in t)
else:
    def gradle_props_26_2(d):
        sub1(d, "archives_base_name", r"^archives_base_name=[^\n]*", "archives_base_name=endless-portals", re.M)

    run_file(ROOT / "gradle.properties", gradle_props_26_2)


def fabric_mod_json(d):
    data = json.loads(d.text)
    data["name"] = "Endless Portals"
    data["description"] = ("Throw a written book into a Nether Portal and walk into a world written by its words. "
                           "A Fabric port of the 20w14\u221e April Fools snapshot, with an infinite rainbow bookshelf.")
    data["icon"] = ("assets/endless_portals/icon.png" if RENAME_IDS
                    else "assets/infinity_dimensions/icon.png")
    data["contact"] = {
        "homepage": "https://github.com/elitexlite/Endless-portals",
        "sources": "https://github.com/elitexlite/Endless-portals",
        "issues": "https://github.com/elitexlite/Endless-portals/issues",
    }
    deps = data.setdefault("depends", {})
    # THE FIX: this used to be the exact string "26.1.2", so Fabric refused to load the mod on any
    # other Minecraft version ("Incompatible mod set!").
    deps["minecraft"] = "~26.3" if TARGET == "26.3" else "~26.2"
    deps["fabricloader"] = ">=0.20.1" if TARGET == "26.3" else ">=0.19.3"
    deps["fabric-api"] = "*"
    deps["java"] = ">=25"
    d.text = json.dumps(data, indent=2, ensure_ascii=False) + "\n"


run_file(RES / "fabric.mod.json", fabric_mod_json)


# --- Java API port (26.2 -> 26.3). Edits come from the migration primer for 26.3. ---------------
def port_chunk_generator(d):
    sub1(d, "applyCarvers override",
         r"    @Override\n    public void applyCarvers\([^{]*\{ \}\n\n", "")
    sub1(d, "buildSurface override",
         r"    @Override\n    public void buildSurface\([^{]*\{ \}\n\n", "")
    sub1(d, "fillFromNoise signature",
         r"public CompletableFuture<ChunkAccess> fillFromNoise\([^)]*\)\s*\{",
         "public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender,\n"
         "                                                       RandomState randomState, StructureManager structures,\n"
         "                                                       BiomeManager biomeManager,\n"
         "                                                       @Nullable WorldGenRegion carverBiomeRegion,\n"
         "                                                       Set<Holder<Biome>> possibleBiomes) {")
    sub1(d, "addDebugScreenInfo signature",
         r"(public void addDebugScreenInfo\(List<String> text, RandomState \w+, BlockPos pos)\)",
         r"\1, SamplerContext samplerContext)")
    lit1(d, "import BlockPos", "import net.minecraft.core.BlockPos;\n",
         "import net.minecraft.core.BlockPos;\nimport net.minecraft.core.Holder;\n")
    lit1(d, "import BiomeManager", "import net.minecraft.world.level.biome.BiomeManager;\n",
         "import net.minecraft.world.level.biome.Biome;\nimport net.minecraft.world.level.biome.BiomeManager;\n")
    lit1(d, "import Blender", "import net.minecraft.world.level.levelgen.blending.Blender;\n",
         "import net.minecraft.world.level.levelgen.blending.Blender;\n"
         "import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;\n"
         "import org.jetbrains.annotations.Nullable;\n")
    lit1(d, "import List", "import java.util.List;\n", "import java.util.List;\nimport java.util.Set;\n")


def port_biome_source(d):
    lit1(d, "getNoiseBiome",
         "    @Override\n"
         "    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {\n"
         "        if (biome == null) throw new IllegalStateException(\n"
         "                \"InfinityBiomeSource not fully initialised (seed=\" + seed + \")\");\n"
         "        return biome;\n"
         "    }\n",
         "    @Override\n"
         "    public BiomeResolver createResolver(Climate.Sampler sampler) {\n"
         "        if (biome == null) throw new IllegalStateException(\n"
         "                \"InfinityBiomeSource not fully initialised (seed=\" + seed + \")\");\n"
         "        return (x, y, z) -> biome;\n"
         "    }\n")
    lit1(d, "import Biomes", "import net.minecraft.world.level.biome.Biomes;\n",
         "import net.minecraft.world.level.biome.BiomeResolver;\nimport net.minecraft.world.level.biome.Biomes;\n")


def port_box_block(d):
    sub1(d, "playerDestroy signature", r"public void playerDestroy\(Level level, Player player,",
         "public void playerDestroy(ServerLevel level, ServerPlayer player,")
    lit1(d, "player.drop", "player.drop(book, false);", "player.spawnAtLocation((ServerLevel) world, book);")
    lit1(d, "import ServerLevel", "import net.minecraft.server.level.ServerLevel;\n",
         "import net.minecraft.server.level.ServerLevel;\nimport net.minecraft.server.level.ServerPlayer;\n")


def port_push_reaction(d):
    lit1(d, "PushReaction.BLOCK", "PushReaction.BLOCK", "PushReaction.IMMOVEABLE")


if TARGET == "26.3":
    for name in ("InfinityChunkGenerator.java", "NamedChunkGenerator.java"):
        run_file(JAVA / "worldgen" / name, port_chunk_generator, already=lambda t: "buildTerrain(" in t)
    run_file(JAVA / "worldgen/InfinityBiomeSource.java", port_biome_source, already=lambda t: "createResolver(" in t)
    run_file(JAVA / "block/BoxOfInfiniteBooksBlock.java", port_box_block, already=lambda t: "ServerPlayer player" in t)


# ------------------------------------------------------------------------ 3. wire in the new features
def modblocks(d):
    if "PushReaction.BLOCK" in d.text and TARGET == "26.3":
        port_push_reaction(d)
    lit1(d, "BOX field",
         "    public static Block INFINITY_PORTAL;\n",
         "    public static Block INFINITY_PORTAL;\n    public static Block RAINBOW_BOOKSHELF;\n")
    lit1(d, "import Block", "import net.minecraft.world.level.block.Block;\n",
         "import net.minecraft.world.level.block.Block;\nimport net.minecraft.world.level.block.SoundType;\n")
    shelf = (
        "        // Rainbow Infinite Bookshelf: right-click for a unique, signed, rainbow-ink book, forever.\n"
        "        // No block entity - the never-repeat counter lives in world SavedData (RainbowShelfData).\n"
        "        // Uses the normal loot table: data/infinity_dimensions/loot_table/blocks/rainbow_bookshelf.json\n"
        "        RAINBOW_BOOKSHELF = registerWithItem(\"rainbow_bookshelf\", key ->\n"
        "                new RainbowBookshelfBlock(BlockBehaviour.Properties.of()\n"
        "                        .mapColor(MapColor.WOOD)\n"
        "                        .strength(1.5f)\n"
        "                        .sound(SoundType.WOOD)\n"
        "                        .lightLevel(state -> 8)\n"
        "                        .setId(key)));\n\n"
    )
    # insert right before the portal registration comment (falls back to the portalKey line)
    m = re.search(r"        // InfinityPortalBlock is world-generated only[^\n]*\n", d.text)
    if m:
        d.text = d.text[:m.start()] + shelf + d.text[m.start():]
    else:
        lit1(d, "portalKey line", '        ResourceKey<Block> portalKey = blockKey("infinity_portal");\n',
             shelf + '        ResourceKey<Block> portalKey = blockKey("infinity_portal");\n')
    sub1(d, "REDSTONE_BLOCKS creative tab",
         r"(        CreativeModeTabEvents\.modifyOutputEvent\(CreativeModeTabs\.REDSTONE_BLOCKS\)"
         r"\.register\(output ->\n\s+output\.accept\(new ItemStack\(BOX_OF_INFINITE_BOOKS\)\)\n\s+\);\n)",
         r"\1        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(output ->\n"
         r"                output.accept(new ItemStack(RAINBOW_BOOKSHELF))\n        );\n")


run_file(JAVA / "block/ModBlocks.java", modblocks, already=lambda t: "RAINBOW_BOOKSHELF" in t)


def mod_main(d):
    lit1(d, "import ModBlocks", "import com.jrock.infinity.block.ModBlocks;\n",
         "import com.jrock.infinity.block.ModBlocks;\nimport com.jrock.infinity.block.PortalBreakHandler;\n")
    sub1(d, "ModItems.register()", r"(        ModItems\.register\(\);\n)",
         r"\1\n        // Breaking any Infinity Portal block (or its frame) removes the whole portal at once.\n"
         r"        PortalBreakHandler.register();\n")


run_file(pick(JAVA / "EndlessPortals.java", JAVA / "InfinityDimensions.java"), mod_main,
         already=lambda t: "PortalBreakHandler" in t)


def lang(d):
    sub1(d, "portal lang key",
         r'(  "block\.infinity_dimensions\.infinity_portal": "[^"\n]*",\n)',
         '  "block.infinity_dimensions.rainbow_bookshelf": "Rainbow Infinite Bookshelf",\n'
         '  "item.infinity_dimensions.rainbow_bookshelf": "Rainbow Infinite Bookshelf",\n'
         r"\1")


run_file(pick(RES / "assets/endless_portals/lang/en_us.json", RES / "assets/infinity_dimensions/lang/en_us.json"),
         lang, already=lambda t: "rainbow_bookshelf" in t)


def axe_tag(d):
    data = json.loads(d.text)
    values = data.setdefault("values", [])
    entry = "infinity_dimensions:rainbow_bookshelf"
    if entry not in values:
        values.append(entry)
    data.setdefault("replace", False)
    d.text = json.dumps(data, indent=2) + "\n"


run_file(RES / "data/minecraft/tags/block/mineable/axe.json", axe_tag,
         already=lambda t: "rainbow_bookshelf" in t)


# ------------------------------------------------------------ 3b. chaotic terrain in the chunk generator
def chaos_generation(d):
    lit1(d, "palette field", "    private final InfinityBlockPalette palette;\n",
         "    private final InfinityBlockPalette palette;\n"
         "    private final ChaosTerrain chaos;          // non-null when this dimension uses a chaos style\n"
         "    private final BlockState[] chaosStates;\n")
    sub1(d, "style assignment", r"this\.style\s*=\s*\(int\) \(Math\.abs\(seed\) % NUM_STYLES\);",
         "long chaosRoll = ChaosTerrain.mix(seed ^ 0x5DEECE66DL);\n"
         "        boolean chaotic = ((chaosRoll >>> 40) & 0xFF) < ChaosTerrain.CHAOS_CHANCE_256;\n"
         "        // ~65% of dimensions are chaotic (styles 18+); the rest keep their classic style.\n"
         "        this.style   = chaotic\n"
         "                ? NUM_STYLES + (int) (((chaosRoll >>> 8) & 0x7FFFFFFFL) % ChaosTerrain.STYLE_COUNT)\n"
         "                : (int) (Math.abs(seed) % NUM_STYLES);\n"
         "        this.chaos   = style >= NUM_STYLES ? new ChaosTerrain(seed, style - NUM_STYLES) : null;")
    sub1(d, "palette assignment", r"this\.palette\s*=\s*new InfinityBlockPalette\(seed\);",
         "this.palette = new InfinityBlockPalette(seed);\n"
         "        this.chaosStates = chaos != null\n"
         "                ? ChaosPalette.build(seed, palette.primary(), palette.surface(), palette.subsurface())\n"
         "                : null;")
    lit1(d, "terrain fill body",
         "        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();\n"
         "        int x0 = chunk.getPos().getMinBlockX();\n",
         "        if (chaos != null) return fillChaos(chunk);\n\n"
         "        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();\n"
         "        int x0 = chunk.getPos().getMinBlockX();\n")
    lit1(d, "getBaseColumn body", "        BlockState[] column = new BlockState[height];\n",
         "        if (chaos != null) {\n"
         "            int[] col = new int[ChaosTerrain.HEIGHT];\n"
         "            chaos.column(x, z, col);\n"
         "            BlockState[] states = new BlockState[height];\n"
         "            for (int i = 0; i < height; i++) {\n"
         "                int idx = bottom + i - ChaosTerrain.MIN_Y;\n"
         "                states[i] = idx >= 0 && idx < col.length\n"
         "                        ? chaosStates[col[idx]] : Blocks.AIR.defaultBlockState();\n"
         "            }\n"
         "            return new NoiseColumn(bottom, states);\n"
         "        }\n"
         "        BlockState[] column = new BlockState[height];\n")
    sub1(d, "getSurfaceY head", r"(    private int getSurfaceY\(int x, int z\) \{\n)",
         r"\1        if (chaos != null) return chaos.surfaceY(x, z);\n")
    sub1(d, "end of class", r"\}\s*\Z",
         "\n    // ── Chaos terrain (styles 18+) ────────────────────────────────────────────\n\n"
         "    private CompletableFuture<ChunkAccess> fillChaos(ChunkAccess chunk) {\n"
         "        int[] col = new int[ChaosTerrain.HEIGHT];\n"
         "        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();\n"
         "        int x0 = chunk.getPos().getMinBlockX();\n"
         "        int z0 = chunk.getPos().getMinBlockZ();\n"
         "        for (int lx = 0; lx < 16; lx++) {\n"
         "            for (int lz = 0; lz < 16; lz++) {\n"
         "                chaos.column(x0 + lx, z0 + lz, col);\n"
         "                for (int i = 0; i < ChaosTerrain.HEIGHT; i++) {\n"
         "                    int material = col[i];\n"
         "                    if (material == ChaosTerrain.AIR) continue;\n"
         "                    int y = ChaosTerrain.MIN_Y + i;\n"
         "                    if (y < getMinY() || y >= getMinY() + getGenDepth()) continue;\n"
         "                    chunk.setBlockState(mutable.set(x0 + lx, y, z0 + lz), chaosStates[material], 0);\n"
         "                }\n"
         "            }\n"
         "        }\n"
         "        return CompletableFuture.completedFuture(chunk);\n"
         "    }\n}\n")


run_file(JAVA / "worldgen/InfinityChunkGenerator.java", chaos_generation, already=lambda t: "ChaosTerrain" in t)

# ------------------------------------------------------------------------ 4. Endless Portal rename
SKIP_DIRS = {".git", ".gradle", "build", "run", ".github", "tools"}
TEXT_EXT = {".java", ".json", ".mcmeta"}


def walk_src():
    for f in sorted((ROOT / "src").rglob("*")):
        if f.is_file() and not (set(f.relative_to(ROOT).parts) & SKIP_DIRS):
            yield f


def keep_case(match, new_lower):
    s = match.group(0)
    if s.isupper():
        return new_lower.upper()          # INFINITY PORTAL -> ENDLESS PORTAL
    if s.istitle():
        return new_lower.title()          # Infinity Portal -> Endless Portal
    if s[0].isupper():
        return new_lower[0].upper() + new_lower[1:]   # Infinity portal -> Endless portal
    return new_lower


def rename_text(d):
    t = d.text
    t = t.replace("InfinityPortal", "EndlessPortal")
    t = t.replace("infinityPortal", "endlessPortal")
    t = t.replace("INFINITY_PORTAL", "ENDLESS_PORTAL")
    t = re.sub(r"(?i)infinity portal", lambda m: keep_case(m, "endless portal"), t)
    t = t.replace("Infinity Dimensions", "Endless Portals")
    t = t.replace("infinity_portal_", "endless_portal_")  # model + texture names (not the registry id)
    if RENAME_IDS:
        t = t.replace("InfinityDimensionsClient", "EndlessPortalsClient")
        t = re.sub(r"\bInfinityDimensions\b", "EndlessPortals", t)
        t = t.replace("infinity_dimensions", "endless_portals")
        t = re.sub(r"\binfinity_portal\b", "endless_portal", t)
    d.text = t


for f in walk_src():
    if f.suffix in TEXT_EXT:
        run_file(f, rename_text)

# file renames (after content edits so that references and names stay in sync)
renamed = []
for f in list(walk_src()):
    name = f.name
    new = name.replace("InfinityPortal", "EndlessPortal")
    if name.startswith("infinity_portal_"):
        new = "endless_portal_" + name[len("infinity_portal_"):]
    if RENAME_IDS:
        new = new.replace("infinity_dimensions", "endless_portals")
        new = new.replace("InfinityDimensionsClient", "EndlessPortalsClient")
        new = re.sub(r"^InfinityDimensions(?=\.java$)", "EndlessPortals", new)
        if name == "infinity_portal.json" or name.startswith("infinity_portal."):
            new = "endless_portal." + name.split(".", 1)[1]
    if new != name:
        target = f.with_name(new)
        if target.exists() and f.suffix == ".java":
            failed.append((rel(f), f"cannot rename: {rel(target)} already exists"))
            continue
        renamed.append((rel(f), rel(target)))
        if not args.dry_run:
            if target.exists():
                target.unlink()  # fresh art from this update replaces a stale copy
            f.rename(target)

# namespace folders: assets/infinity_dimensions -> assets/endless_portals (same for data/)
moved_dirs = []
if RENAME_IDS:
    import shutil
    for base in (RES / "assets", RES / "data"):
        old, new = base / "infinity_dimensions", base / "endless_portals"
        if old.is_dir():
            moved_dirs.append((rel(old), rel(new)))
            if not args.dry_run:
                if new.exists():
                    shutil.copytree(old, new, dirs_exist_ok=True)   # fresh files from this update win
                    shutil.rmtree(old)
                else:
                    old.rename(new)

# ----------------------------------------------------------------------------------------- report
w = max([len(x[0]) for x in ok + skipped + failed] + [10])
print()
for label, msg in ok:
    print(f"  ok       {label:<{w}}  {msg}")
for label, msg in skipped:
    if msg != "no change needed":
        print(f"  skipped  {label:<{w}}  {msg}")
for a, b in renamed:
    print(f"  renamed  {a}  ->  {b}")
for a, b in moved_dirs:
    print(f"  moved    {a}/  ->  {b}/")
for label, msg in failed:
    print(f"  FAILED   {label:<{w}}  {msg}")
print(f"\n{len(ok)} updated, {len(skipped)} unchanged, {len(renamed)} renamed, {len(failed)} failed"
      + ("   [dry run - nothing written]" if args.dry_run else ""))
if failed:
    print("\nFailed files were left untouched. The usual cause is a file that was already edited by hand;\n"
          "send me the FAILED lines above and I'll adjust the anchors.")
    sys.exit(1)
print("\nNext: ./gradlew build   (or push and let GitHub Actions build it)")
