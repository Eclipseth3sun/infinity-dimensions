#!/usr/bin/env python3
"""Port elitexlite/Endless-portals from MC 26.2 to 26.3.
Usage: python3 port_to_26_3.py /path/to/Endless-portals
Every edit must match exactly once, otherwise the script stops without writing that file."""
import re, sys, pathlib

root = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".")
J = root / "src/main/java/com/jrock/infinity"

def edit(path, steps):
    p = pathlib.Path(path)
    s = p.read_text(encoding="utf-8")
    for desc, pat, rep, is_re in steps:
        if is_re:
            s, n = re.subn(pat, rep, s, count=1, flags=re.S)
        else:
            n = s.count(pat)
            s = s.replace(pat, rep, 1) if n else s
        if n < 1:
            sys.exit(f"FAILED: {p.name}: could not find: {desc}")
    p.write_text(s, encoding="utf-8")
    print(f"ok  {p.relative_to(root)}")

def S(desc, a, b): return (desc, a, b, False)
def R(desc, a, b, *_): return (desc, a, b, True)

# ---- gradle.properties ------------------------------------------------------
edit(root / "gradle.properties", [
    R("minecraft_version", r"minecraft_version=[^\n]*", "minecraft_version=26.3"),
    R("loader_version",    r"loader_version=[^\n]*",    "loader_version=0.20.1"),
    R("loom_version",      r"loom_version=[^\n]*",      "loom_version=1.18-SNAPSHOT"),
    R("fabric_api_version", r"fabric_api_version=[^\n]*", "fabric_api_version=0.166.2+26.3"),
    R("mod_version",       r"mod_version=[^\n]*",       "mod_version=1.2.0+mc26.3"),
    R("api comment",       r"# Fabric API 0\.153\.0[^\n]*", "# Fabric API for 26.3 - verify latest at https://fabricmc.net/develop/"),
])

edit(root / "gradle/wrapper/gradle-wrapper.properties", [
    R("Gradle 9.7", r"gradle-[0-9.]+-bin\.zip", "gradle-9.7.0-bin.zip"),
])

# ---- fabric.mod.json --------------------------------------------------------
edit(root / "src/main/resources/fabric.mod.json", [
    S("fabricloader", "\"fabricloader\": \">=0.19.2\"", "\"fabricloader\": \">=0.20.1\""),
    S("minecraft", "\"minecraft\": \"26.1.2\"", "\"minecraft\": \"26.3\""),
])

# ---- chunk generators (identical changes) -----------------------------------
for name in ("InfinityChunkGenerator.java", "NamedChunkGenerator.java"):
    edit(J / "worldgen" / name, [
        R("applyCarvers", r"    @Override\n    public void applyCarvers\([^{]*\{ \}\n\n", "", True),
        R("buildSurface", r"    @Override\n    public void buildSurface\([^{]*\{ \}\n\n", "", True),
        R("fillFromNoise signature",
          r"public CompletableFuture<ChunkAccess> fillFromNoise\([^)]*\)\s*\{",
          "public CompletableFuture<ChunkAccess> buildTerrain(ChunkAccess chunk, Blender blender,\n"
          "                                                       RandomState randomState, StructureManager structures,\n"
          "                                                       BiomeManager biomeManager,\n"
          "                                                       @Nullable WorldGenRegion carverBiomeRegion,\n"
          "                                                       Set<Holder<Biome>> possibleBiomes) {", True),
        R("addDebugScreenInfo",
          r"(public void addDebugScreenInfo\(List<String> text, RandomState \w+, BlockPos pos)\)",
          r"\1, SamplerContext samplerContext)", True),
        S("import BlockPos", "import net.minecraft.core.BlockPos;\n",
          "import net.minecraft.core.BlockPos;\nimport net.minecraft.core.Holder;\n"),
        S("import BiomeManager", "import net.minecraft.world.level.biome.BiomeManager;\n",
          "import net.minecraft.world.level.biome.Biome;\nimport net.minecraft.world.level.biome.BiomeManager;\n"),
        S("import Blender", "import net.minecraft.world.level.levelgen.blending.Blender;\n",
          "import net.minecraft.world.level.levelgen.blending.Blender;\n"
          "import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;\n"
          "import org.jetbrains.annotations.Nullable;\n"),
        S("import List", "import java.util.List;\n", "import java.util.List;\nimport java.util.Set;\n"),
    ])

# ---- biome source -----------------------------------------------------------
edit(J / "worldgen/InfinityBiomeSource.java", [
    S("getNoiseBiome",
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
      "    }\n"),
    S("import Biomes", "import net.minecraft.world.level.biome.Biomes;\n",
      "import net.minecraft.world.level.biome.BiomeResolver;\nimport net.minecraft.world.level.biome.Biomes;\n"),
])

# ---- ModBlocks --------------------------------------------------------------
edit(J / "block/ModBlocks.java", [S("PushReaction.BLOCK", "PushReaction.BLOCK", "PushReaction.IMMOVEABLE")])

# ---- BoxOfInfiniteBooksBlock ------------------------------------------------
edit(J / "block/BoxOfInfiniteBooksBlock.java", [
    R("playerDestroy signature",
      r"public void playerDestroy\(Level level, Player player,",
      "public void playerDestroy(ServerLevel level, ServerPlayer player,", True),
    S("player.drop", "player.drop(book, false);", "player.spawnAtLocation((ServerLevel) world, book);"),
    S("import ServerLevel", "import net.minecraft.server.level.ServerLevel;\n",
      "import net.minecraft.server.level.ServerLevel;\nimport net.minecraft.server.level.ServerPlayer;\n"),
])
print("\nDone. Next: ./gradlew wrapper --gradle-version latest && ./gradlew build")
