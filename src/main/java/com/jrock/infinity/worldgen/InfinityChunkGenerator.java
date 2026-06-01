package com.jrock.infinity.worldgen;

import com.jrock.infinity.InfinityDimensions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Procedural chunk generator for infinity dimensions.
 * Terrain style is derived from: abs(seed) % NUM_STYLES
 *
 *  0  FLAT        – bedrock + stone + grass at y=64
 *  1  HILLS       – rolling hills y=48–80
 *  2  MOUNTAINS   – tall noise peaks y=40–140
 *  3  OCEAN       – water-filled basin, sea floor at y=30
 *  4  FLOATING    – disconnected stone islands at y=80
 *  5  VOID        – bedrock floor only
 *  6  CAVE        – mostly solid with ellipsoid-carved bubbles
 *  7  DESERT      – flat sandstone + sand plains
 *  8  MUSHROOM    – flat mycelium surface
 *  9  INVERTED    – stone ceiling slab (portal falls back to floating platform)
 * 10  PILLARS     – tall spire columns separated by void
 * 11  ARCHIPELAGO – ocean basin with scattered islands above sea level
 * 12  CANYON      – flat terrain with deep noise-carved chasms
 * 13  CAVERN      – dense solid mass with 3-D hollowed voids
 * 14  MESA        – high plateau with Y-banded strata and canyon carving (badlands-inspired)
 * 15  LAVA_SEA    – variable terrain with a lava fill at y≤31 (nether-inspired)
 * 16  FROZEN      – ocean-floor terrain with a packed-ice fill instead of water
 * 17  AMPLIFIED   – extreme mountains; same shape as MOUNTAINS but ×3 amplitude
 */
public class InfinityChunkGenerator extends ChunkGenerator {

    public static final MapCodec<InfinityChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.LONG.fieldOf("seed").forGetter(g -> g.seed)
            ).apply(instance, InfinityChunkGenerator::new)
    );

    private static final int NUM_STYLES = 18;

    private final long seed;
    private final int style;
    private final InfinityBlockPalette palette;

    public InfinityChunkGenerator(BiomeSource biomeSource, long seed) {
        super(biomeSource);
        this.seed    = seed;
        this.style   = (int) (Math.abs(seed) % NUM_STYLES);
        this.palette = new InfinityBlockPalette(seed);
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.fromNamespaceAndPath(InfinityDimensions.MOD_ID, "infinity"),
                CODEC);
    }

    // ── ChunkGenerator contract ───────────────────────────────────────────────

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() { return CODEC; }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
                             BiomeManager biomeManager, StructureManager structures, ChunkAccess chunk) { }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures,
                             RandomState randomState, ChunkAccess chunk) { }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structures, ChunkAccess chunk) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX();
        int z0 = chunk.getPos().getMinBlockZ();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                int surfaceY = getSurfaceY(x, z);
                for (int y = getMinY(); y < getMinY() + getGenDepth(); y++) {
                    BlockState state = getBlockForStyle(x, y, z, surfaceY);
                    if (state != null) chunk.setBlockState(mutable.set(x, y, z), state, 0);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmapType,
                             LevelHeightAccessor world, RandomState randomState) {
        return getSurfaceY(x, z) + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState randomState) {
        int height = world.getHeight();
        int bottom = world.getMinY();
        BlockState[] column = new BlockState[height];
        int surfaceY = getSurfaceY(x, z);

        for (int i = 0; i < height; i++) {
            int y = bottom + i;
            BlockState state = getBlockForStyle(x, y, z, surfaceY);
            column[i] = state != null ? state : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(bottom, column);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) { }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState randomState, BlockPos pos) {
        text.add("InfinityGen style=" + style + " seed=" + seed);
    }

    @Override public int getMinY()      { return -64; }
    @Override public int getGenDepth()  { return 384; }
    @Override public int getSeaLevel()  { return 63;  }

    // ── Style dispatch ────────────────────────────────────────────────────────

    private int getSurfaceY(int x, int z) {
        return switch (style) {
            case 1  -> 64 + (int) (simplexHeight(x, z, seed, 0.015) * 16);
            case 2  -> 60 + (int) (simplexHeight(x, z, seed, 0.008) * 50);
            case 3  -> 30;
            case 4  -> 80 + (int) (simplexHeight(x, z, seed, 0.05) * 12);
            case 5  -> getMinY();
            case 6  -> 200;
            case 9  -> getMinY();
            case 10 -> pillarSurfaceY(x, z);
            case 11 -> archipelagoSurfaceY(x, z);
            case 12 -> 72;    // CANYON     – flat plateau, chasms cut below
            case 13 -> 200;   // CAVERN     – dense to y=200, carved inside
            case 14 -> 100;   // MESA       – high plateau, strata visible in canyon walls
            case 15 -> Math.max(31, 40 + (int)((simplexHeight(x, z, seed, 0.04) + 1.0) * 0.5 * 40));
            case 16 -> 30;    // FROZEN     – ocean floor; ice fills above
            case 17 -> 60 + (int)(simplexHeight(x, z, seed, 0.005) * 120); // AMPLIFIED
            default -> 64;
        };
    }

    private BlockState getBlockForStyle(int x, int y, int z, int surfaceY) {
        return switch (style) {
            case 0  -> flatBlock(y, surfaceY);
            case 1  -> flatBlock(y, surfaceY);
            case 2  -> mountainBlock(y, surfaceY);
            case 3  -> oceanBlock(y, surfaceY);
            case 4  -> floatingBlock(x, y, z, surfaceY);
            case 5  -> voidBlock(y);
            case 6  -> caveBlock(x, y, z);
            case 7  -> desertBlock(y, surfaceY);
            case 8  -> mushroomBlock(y, surfaceY);
            case 9  -> invertedBlock(y);
            case 10 -> pillarsBlock(y, surfaceY);
            case 11 -> archipelagoBlock(y, surfaceY);
            case 12 -> canyonBlock(x, y, z, surfaceY);
            case 13 -> cavernBlock(x, y, z);
            case 14 -> mesaBlock(x, y, z, surfaceY);
            case 15 -> lavaSeaBlock(y, surfaceY);
            case 16 -> frozenBlock(y, surfaceY);
            case 17 -> amplifiedBlock(y, surfaceY);
            default -> flatBlock(y, 64);
        };
    }

    // ── Per-style block helpers ───────────────────────────────────────────────

    private BlockState flatBlock(int y, int surface) {
        if (y == getMinY())   return Blocks.BEDROCK.defaultBlockState();
        if (y < surface - 4) return palette.primary();
        if (y < surface)      return palette.subsurface();
        if (y == surface)     return palette.surface();
        return null;
    }

    private BlockState mountainBlock(int y, int surface) {
        if (y == getMinY())   return Blocks.BEDROCK.defaultBlockState();
        if (y < surface - 8) return palette.primary();
        if (y <= surface)     return y > 110 ? palette.surface() : palette.primary();
        return null;
    }

    private BlockState oceanBlock(int y, int surface) {
        if (y == getMinY())   return Blocks.BEDROCK.defaultBlockState();
        if (y < surface - 2) return palette.primary();
        if (y <= surface)     return palette.subsurface();
        if (y <= 63)          return Blocks.WATER.defaultBlockState(); // water stays water
        return null;
    }

    private BlockState floatingBlock(int x, int y, int z, int islandTop) {
        int islandBot = islandTop - 6;
        if (y > islandBot && y <= islandTop) {
            double n = simplexHeight(x, z + y * 0.5, seed, 0.12);
            if (n > 0.3) return y == islandTop ? palette.surface() : palette.primary();
        }
        return null;
    }

    private BlockState voidBlock(int y) {
        return y == getMinY() ? Blocks.BEDROCK.defaultBlockState() : null;
    }

    private BlockState caveBlock(int x, int y, int z) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y > getMinY() && y <= 200) {
            return simplexHeight(x, z + y * 0.5, seed, 0.06) >= 0.25
                    ? palette.primary() : null;
        }
        return null;
    }

    private BlockState desertBlock(int y, int surface) {
        if (y == getMinY())   return Blocks.BEDROCK.defaultBlockState();
        if (y < surface - 3) return palette.primary();
        if (y <= surface)     return palette.subsurface();
        return null;
    }

    private BlockState mushroomBlock(int y, int surface) {
        if (y == getMinY())   return Blocks.BEDROCK.defaultBlockState();
        if (y < surface - 3) return palette.primary();
        if (y < surface)      return palette.subsurface();
        if (y == surface)     return palette.surface();
        return null;
    }

    private BlockState invertedBlock(int y) {
        int top = getMinY() + getGenDepth() - 1;
        if (y == top)        return Blocks.BEDROCK.defaultBlockState();
        if (y > top - 5)     return palette.surface();
        if (y > top - 20)    return palette.primary();
        return null;
    }

    // ── Style 10 – PILLARS ───────────────────────────────────────────────────

    /**
     * Returns the top of the spire at (x,z), or {@code getMinY()} if this column is
     * void (gap between pillars).  Used by both {@code getSurfaceY} and
     * {@code pillarsBlock} via the pre-computed {@code surfaceY} parameter.
     */
    private int pillarSurfaceY(int x, int z) {
        // Cluster noise: below threshold → void gap between spires
        if (simplexHeight(x, z, seed, 0.09) < 0.25) return getMinY();
        // Height noise: taller and shorter spires based on a second sample
        return 20 + (int)((simplexHeight(x, z, seed + 1, 0.06) + 1.0) * 0.5 * 130);
    }

    private BlockState pillarsBlock(int y, int surfaceY) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (surfaceY == getMinY()) return null;   // void gap between spires
        if (y > surfaceY) return null;
        return y == surfaceY ? palette.surface() : palette.primary();
    }

    // ── Style 11 – ARCHIPELAGO ────────────────────────────────────────────────

    /**
     * Returns the surface Y for archipelago terrain.
     * Positions with high island noise break above sea level; everywhere else
     * is the ocean floor (y=30).
     */
    private int archipelagoSurfaceY(int x, int z) {
        double n = simplexHeight(x, z, seed, 0.025);
        return n > 0.5 ? 64 + (int)((n - 0.5) / 0.5 * 22) : 30;
    }

    private BlockState archipelagoBlock(int y, int surfaceY) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y > surfaceY && y > 63) return null;
        if (y > surfaceY) return Blocks.WATER.defaultBlockState();  // flooded below surface
        if (y < surfaceY - 3) return palette.primary();
        // Top 3 blocks of islands above water: subsurface then surface
        return (surfaceY > 63 && y == surfaceY) ? palette.surface() : palette.subsurface();
    }

    // ── Style 12 – CANYON ─────────────────────────────────────────────────────

    private BlockState canyonBlock(int x, int y, int z, int surfaceY) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y > surfaceY) return null;

        // Canyon carved where noise exceeds threshold — cuts straight down to a noise-based floor
        double canyonNoise = simplexHeight(x, z, seed, 0.018);
        if (canyonNoise > 0.35) {
            int canyonFloor = 5 + (int)((1.0 - canyonNoise) / 0.65 * 40);
            if (y > canyonFloor) return null;  // inside the chasm
        }

        if (y == surfaceY)     return palette.surface();
        if (y > surfaceY - 4) return palette.subsurface();
        return palette.primary();
    }

    // ── Style 13 – CAVERN ─────────────────────────────────────────────────────

    private BlockState cavernBlock(int x, int y, int z) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y > 200) return null;  // open void above the dense mass

        // Thin surface layer at the top
        if (y > 196) return palette.surface();
        if (y > 192) return palette.subsurface();

        // 3-D ellipsoidal carving — large hollow voids in the solid mass
        double carve = simplexHeight(x, z + y * 0.28, seed + 5, 0.022);
        if (carve > 0.55 && y > getMinY() + 8) return null;  // hollow void
        return palette.primary();
    }

    // ── Style 14 – MESA ──────────────────────────────────────────────────────

    /**
     * High plateau (surfaceY=100) with deep canyon carving and horizontal strata bands.
     * The bands alternate between {@code primary} and {@code subsurface} every 4 blocks,
     * exposing geological layering in canyon walls — badlands-inspired.
     */
    private BlockState mesaBlock(int x, int y, int z, int surfaceY) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y > surfaceY)   return null;

        // Canyon carving — deeper and wider cuts than CANYON style
        double cn = simplexHeight(x, z, seed, 0.014);
        if (cn > 0.28) {
            int canyonFloor = 4 + (int)((1.0 - cn) / 0.72 * 88);
            if (y > canyonFloor) return null;
        }

        if (y == surfaceY) return palette.surface();

        // Y-banded strata: alternating 4-block bands of primary / subsurface
        return (((y - getMinY()) / 4) % 2 == 0) ? palette.primary() : palette.subsurface();
    }

    // ── Style 15 – LAVA_SEA ──────────────────────────────────────────────────

    /**
     * Variable terrain (y=31–80) with a lava fill at y≤31 wherever the terrain dips
     * below the lava level — nether-inspired.  The {@code surfaceY} is clamped to ≥31 so
     * the return portal always lands above the lava.
     */
    private BlockState lavaSeaBlock(int y, int surfaceY) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y > surfaceY) {
            return (y <= 31) ? Blocks.LAVA.defaultBlockState() : null;
        }
        if (y == surfaceY) return palette.surface();
        if (y > surfaceY - 3) return palette.subsurface();
        return palette.primary();
    }

    // ── Style 16 – FROZEN ────────────────────────────────────────────────────

    /**
     * Ocean-floor terrain at y=30 with a solid packed-ice fill from y=31 to y=63
     * instead of water — frozen ocean inspired.  Players walk on the ice surface.
     */
    private BlockState frozenBlock(int y, int surfaceY) {
        if (y == getMinY()) return Blocks.BEDROCK.defaultBlockState();
        if (y < surfaceY - 2) return palette.primary();
        if (y <= surfaceY)    return palette.subsurface();
        if (y <= 63)          return Blocks.PACKED_ICE.defaultBlockState();
        return null;
    }

    // ── Style 17 – AMPLIFIED ─────────────────────────────────────────────────

    /**
     * Extreme mountains with ×3 the amplitude of {@code MOUNTAINS}.
     * Peaks can reach y≈180, valleys can dip near or below bedrock.
     * Reuses {@link #mountainBlock} — same visual language, vastly different scale.
     */
    private BlockState amplifiedBlock(int y, int surfaceY) {
        return mountainBlock(y, surfaceY);  // same block logic, palette handles the rest
    }

    // ── Noise helpers ─────────────────────────────────────────────────────────

    private static double simplexHeight(double x, double z, long seed, double scale) {
        double sx = x * scale, sz = z * scale;
        long ix = (long) Math.floor(sx), iz = (long) Math.floor(sz);
        double fx = sx - ix, fz = sz - iz;
        double v00 = rng2(ix,   iz,   seed), v10 = rng2(ix+1, iz,   seed);
        double v01 = rng2(ix,   iz+1, seed), v11 = rng2(ix+1, iz+1, seed);
        double tx = smooth(fx), tz = smooth(fz);
        return lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz) * 2.0 - 1.0;
    }

    private static double rng2(long x, long z, long seed) {
        long h = seed ^ (x * 0x9e3779b97f4a7c15L) ^ (z * 0x6c62272e07bb0142L);
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        h ^= (h >>> 31);
        return (h & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + t * (b - a); }
}
