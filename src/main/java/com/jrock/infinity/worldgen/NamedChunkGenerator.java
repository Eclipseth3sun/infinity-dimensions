package com.jrock.infinity.worldgen;

import com.jrock.infinity.InfinityDimensions;
import com.jrock.infinity.dimension.NamedDimension;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.item.DyeColor;
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
 * Single parameterised chunk generator for all named easter-egg dimensions.
 * Registered as "infinity_dimensions:named".
 */
public class NamedChunkGenerator extends ChunkGenerator {

    public static final MapCodec<NamedChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
                    Codec.STRING.fieldOf("theme").forGetter(g -> g.theme.getId())
            ).apply(instance, NamedChunkGenerator::new)
    );

    private final NamedDimension theme;

    public NamedChunkGenerator(BiomeSource biomeSource, String themeId) {
        super(biomeSource);
        NamedDimension d = NamedDimension.byId(themeId);
        this.theme = d != null ? d : NamedDimension.FLAT;
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
                Identifier.fromNamespaceAndPath(InfinityDimensions.MOD_ID, "named"),
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
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                for (int y = getMinY(); y < getMinY() + getGenDepth(); y++) {
                    BlockState state = blockFor(x, y, z);
                    if (state != null) chunk.setBlockState(mut.set(x, y, z), state, 0);
                }
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type,
                             LevelHeightAccessor world, RandomState cfg) { return 65; }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState cfg) {
        int h = world.getHeight(), bot = world.getMinY();
        BlockState[] col = new BlockState[h];
        for (int i = 0; i < h; i++) {
            BlockState s = blockFor(x, bot + i, z);
            col[i] = s != null ? s : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(bot, col);
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) { }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState cfg, BlockPos pos) {
        text.add("NamedGen theme=" + theme.getId());
    }

    @Override public int getMinY()      { return -64; }
    @Override public int getGenDepth()  { return 384; }
    @Override public int getSeaLevel()  { return 63;  }

    // ── Theme dispatch ────────────────────────────────────────────────────────

    private BlockState blockFor(int x, int y, int z) {
        return switch (theme) {
            case FARM       -> farm(x, y, z);
            case ANT        -> ant(x, y, z);
            case CHESS, CLUBS, CHECKERS -> chess(x, y, z);
            case LIBRARY, GALLERY, MUSEUM -> library(x, y, z);
            case LLAMA      -> llama(x, y, z);
            case MESSAGE    -> message(x, y, z);
            case INVERTED   -> inverted(x, y, z);
            case PATTERNS   -> patterns(x, y, z);
            case SPONGE     -> sponge(x, y, z);
            case HOLES      -> holes(x, y, z);
            case COLORS     -> colors(x, y, z);
            case SKYGRID    -> skygrid(x, y, z);
            case VOID       -> voidDim(x, y, z);
            case CAVE, UNDERGROUND -> cave(x, y, z);
            case SLIME      -> slime(x, y, z);
            case DARK       -> dark(x, y, z);
            case FLOATING   -> floating(x, y, z);
            case GRID, WALL -> grid(x, y, z);
            case ANCIENT    -> ancient(x, y, z);
            default         -> flat(x, y, z);
        };
    }

    // ── Named dim implementations ─────────────────────────────────────────────

    private static BlockState farm(int x, int y, int z) {
        // Single bedrock layer at world floor — mobs cannot spawn on bedrock,
        // giving a naturally mob-free space ideal for farm building.
        return y == -64 ? Blocks.BEDROCK.defaultBlockState() : null;
    }

    private static BlockState ant(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y == 64)  return Blocks.CONCRETE.pick(DyeColor.WHITE).defaultBlockState();
        return null;
    }

    private static BlockState chess(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y <  60)  return Blocks.STONE.defaultBlockState();
        if (y <  64)  return Blocks.DIRT.defaultBlockState();
        if (y == 64) {
            boolean black = ((Math.floorDiv(x, 4) + Math.floorDiv(z, 4)) & 1) == 0;
            return Blocks.CONCRETE.pick(black ? DyeColor.BLACK : DyeColor.WHITE).defaultBlockState();
        }
        return null;
    }

    private static BlockState library(int x, int y, int z) {
        if (y == -64 || y == 128) return Blocks.BEDROCK.defaultBlockState();
        int ry = Math.floorMod(y, 8), rx = Math.floorMod(x, 8), rz = Math.floorMod(z, 8);
        if (ry == 0 || ry == 7) return Blocks.STONE_BRICKS.defaultBlockState();

        // X-axis bookshelf walls — punch a 2×2 hole in the centre so rooms connect.
        if (rx == 0 || rx == 7) {
            if (rz >= 3 && rz <= 4 && ry >= 2 && ry <= 3) return null;
            return Blocks.BOOKSHELF.defaultBlockState();
        }
        // Z-axis bookshelf walls — same treatment.
        if (rz == 0 || rz == 7) {
            if (rx >= 3 && rx <= 4 && ry >= 2 && ry <= 3) return null;
            return Blocks.BOOKSHELF.defaultBlockState();
        }
        return null;
    }

    private static BlockState llama(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y <  64)  return Blocks.STONE.defaultBlockState();
        if (y == 64)  return Blocks.CARPET.pick(DyeColor.LIME).defaultBlockState();
        return null;
    }

    private static BlockState message(int x, int y, int z) {
        if (y == 64 && x == 0 && z == 0) return Blocks.BEDROCK.defaultBlockState();
        return null;
    }

    private static BlockState inverted(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y >  200) return Blocks.STONE.defaultBlockState();
        return null;
    }

    private static BlockState patterns(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y == 64) {
            boolean dark = (Integer.bitCount(Math.abs(x) + Math.abs(z)) & 1) == 0;
            return Blocks.CONCRETE.pick(dark ? DyeColor.BLACK : DyeColor.WHITE).defaultBlockState();
        }
        if (y < 64) return y < 60 ? Blocks.STONE.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        return null;
    }

    private static BlockState sponge(int x, int y, int z) {
        if (isMengerSolid(Math.floorMod(x, 27), Math.floorMod(y + 64, 27), Math.floorMod(z, 27)))
            return y == -64 ? Blocks.BEDROCK.defaultBlockState() : Blocks.SPONGE.defaultBlockState();
        return null;
    }

    private static boolean isMengerSolid(int x, int y, int z) {
        while (x > 0 || y > 0 || z > 0) {
            if ((x % 3 == 1 ? 1 : 0) + (y % 3 == 1 ? 1 : 0) + (z % 3 == 1 ? 1 : 0) >= 2)
                return false;
            x /= 3; y /= 3; z /= 3;
        }
        return true;
    }

    private static BlockState holes(int x, int y, int z) {
        int hx = Math.floorMod(x, 48) - 24, hz = Math.floorMod(z, 48) - 24;
        if (hx * hx + hz * hz < 64) return null;
        return flat(x, y, z);
    }

    private static BlockState colors(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y <  60)  return Blocks.STONE.defaultBlockState();
        if (y <  64)  return Blocks.DIRT.defaultBlockState();
        if (y == 64) {
            boolean px = x >= 0, pz = z >= 0;
            if (px && pz)   return Blocks.CONCRETE.pick(DyeColor.BLUE).defaultBlockState();
            if (!px && pz)  return Blocks.CONCRETE.pick(DyeColor.RED).defaultBlockState();
            if (px)         return Blocks.CONCRETE.pick(DyeColor.GREEN).defaultBlockState();
            return Blocks.CONCRETE.pick(DyeColor.YELLOW).defaultBlockState();
        }
        return null;
    }

    private static BlockState skygrid(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if ((x & 3) == 0 && (y & 3) == 0 && (z & 3) == 0) {
            return switch (Math.abs(x * 31 + y * 17 + z * 7) % 8) {
                case 0 -> Blocks.STONE.defaultBlockState();
                case 1 -> Blocks.DIRT.defaultBlockState();
                case 2 -> Blocks.SAND.defaultBlockState();
                case 3 -> Blocks.GRAVEL.defaultBlockState();
                case 4 -> Blocks.OAK_LOG.defaultBlockState();
                case 5 -> Blocks.IRON_ORE.defaultBlockState();
                case 6 -> Blocks.OAK_LEAVES.defaultBlockState();
                default -> Blocks.COBBLESTONE.defaultBlockState();
            };
        }
        return null;
    }

    private static BlockState voidDim(int x, int y, int z) {
        return (y == -64 && x == 0 && z == 0) ? Blocks.BEDROCK.defaultBlockState() : null;
    }

    private static BlockState cave(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y < 128 && valueNoise(x * 0.1, y * 0.1, z * 0.1) < 0.35)
            return Blocks.STONE.defaultBlockState();
        return null;
    }

    private static BlockState flat(int x, int y, int z) {
        if (y == -64)      return Blocks.BEDROCK.defaultBlockState();
        if (y <  60)       return Blocks.STONE.defaultBlockState();
        if (y <  64)       return Blocks.DIRT.defaultBlockState();
        if (y == 64)       return Blocks.GRASS_BLOCK.defaultBlockState();
        return null;
    }

    private static BlockState slime(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y <= 64)  return Blocks.SLIME_BLOCK.defaultBlockState();
        return null;
    }

    private static BlockState dark(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y <  64)  return Blocks.STONE.defaultBlockState();
        if (y == 64)  return Blocks.OBSIDIAN.defaultBlockState();
        return null;
    }

    private static BlockState floating(int x, int y, int z) {
        int ix = Math.floorMod(x, 32) - 16, iz = Math.floorMod(z, 32) - 16;
        if (ix * ix + iz * iz < 36 && y >= 74 && y <= 80)
            return y == 80 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState();
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        return null;
    }

    private static BlockState grid(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        boolean onLine = Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0;
        if (y >= -63 && y <= 64 && onLine) return Blocks.STONE.defaultBlockState();
        return null;
    }

    private static BlockState ancient(int x, int y, int z) {
        if (y == -64) return Blocks.BEDROCK.defaultBlockState();
        if (y <  60)  return Blocks.DEEPSLATE.defaultBlockState();
        if (y <  64)  return Blocks.TUFF.defaultBlockState();
        if (y == 64)  return Blocks.MOSS_BLOCK.defaultBlockState();
        return null;
    }

    // ── Cheap 3D value noise ──────────────────────────────────────────────────

    private static double valueNoise(double x, double y, double z) {
        long ix = (long) Math.floor(x), iy = (long) Math.floor(y), iz = (long) Math.floor(z);
        double fx = x - ix, fy = y - iy, fz = z - iz;
        double v000 = rng3(ix,   iy,   iz);  double v100 = rng3(ix+1, iy,   iz);
        double v010 = rng3(ix,   iy+1, iz);  double v110 = rng3(ix+1, iy+1, iz);
        double v001 = rng3(ix,   iy,   iz+1);double v101 = rng3(ix+1, iy,   iz+1);
        double v011 = rng3(ix,   iy+1, iz+1);double v111 = rng3(ix+1, iy+1, iz+1);
        double tx = smooth(fx), ty = smooth(fy), tz = smooth(fz);
        return lerp(lerp(lerp(v000,v100,tx), lerp(v010,v110,tx), ty),
                    lerp(lerp(v001,v101,tx), lerp(v011,v111,tx), ty), tz);
    }

    private static double rng3(long x, long y, long z) {
        long h = (x * 0x9e3779b97f4a7c15L) ^ (y * 0x6c62272e07bb0142L) ^ (z * 0xd1b54a32d192ed03L);
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        return (h & 0xFFFFFFFFL) / (double) 0x100000000L;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + t * (b - a); }
}
