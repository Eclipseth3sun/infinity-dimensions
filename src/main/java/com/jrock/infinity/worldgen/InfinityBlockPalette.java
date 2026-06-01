package com.jrock.infinity.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * Derives a consistent randomised block palette for a procedural infinity dimension.
 *
 * <p>Three roles are filled:
 * <ul>
 *   <li>{@link #primary}    – replaces stone (and all stone-equivalent fills).</li>
 *   <li>{@link #surface}    – replaces the top-layer block (grass, mycelium, sand…).</li>
 *   <li>{@link #subsurface} – replaces dirt / the transitional layer just below the surface.
 *       Frequently the same as {@link #primary} for a more uniform look.</li>
 * </ul>
 *
 * <p>The palette is fully deterministic: the same seed always yields the same three blocks.
 * Bedrock and water are never substituted — they are structural.
 */
public final class InfinityBlockPalette {

    private final BlockState primary;
    private final BlockState surface;
    private final BlockState subsurface;

    public InfinityBlockPalette(long seed) {
        // Separate RNG stream from the style / noise RNGs — avoids palette correlating
        // with terrain shape.
        Random rng = new Random(Long.rotateLeft(seed, 13) ^ 0x9e3779b97f4a7c15L);

        primary = pick(rng, PRIMARY_POOL).defaultBlockState();

        // 1-in-4 chance the surface matches primary, creating monochrome worlds.
        surface = (rng.nextInt(4) == 0) ? primary : pick(rng, SURFACE_POOL).defaultBlockState();

        // Subsurface: half the time reuse primary, otherwise a softer transitional block.
        subsurface = rng.nextBoolean() ? primary : pick(rng, SUBSURFACE_POOL).defaultBlockState();
    }

    public BlockState primary()    { return primary;    }
    public BlockState surface()    { return surface;    }
    public BlockState subsurface() { return subsurface; }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static Block pick(Random rng, Block[] pool) {
        return pool[rng.nextInt(pool.length)];
    }

    // ── Pools ─────────────────────────────────────────────────────────────────

    /**
     * Everything that stone can become.  Skewed heavily toward unusual materials;
     * normal stone variants are present but individually rare (~1 % each).
     */
    private static final Block[] PRIMARY_POOL = {
        // Stone / cobblestone family
        Blocks.STONE, Blocks.COBBLESTONE, Blocks.SMOOTH_STONE,
        Blocks.GRANITE, Blocks.POLISHED_GRANITE,
        Blocks.DIORITE, Blocks.POLISHED_DIORITE,
        Blocks.ANDESITE, Blocks.POLISHED_ANDESITE,
        Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE,
        Blocks.POLISHED_DEEPSLATE, Blocks.CHISELED_DEEPSLATE,

        // Stone brick variants
        Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS,
        Blocks.MOSSY_STONE_BRICKS, Blocks.CHISELED_STONE_BRICKS,

        // Tuff family (1.21+)
        Blocks.TUFF, Blocks.POLISHED_TUFF, Blocks.TUFF_BRICKS, Blocks.CHISELED_TUFF,

        // Other natural rock
        Blocks.CALCITE, Blocks.DRIPSTONE_BLOCK,
        Blocks.BASALT, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT,

        // Sandstone
        Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CHISELED_SANDSTONE,
        Blocks.RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE, Blocks.CHISELED_RED_SANDSTONE,

        // Nether
        Blocks.NETHERRACK, Blocks.NETHER_BRICKS,
        Blocks.CRACKED_NETHER_BRICKS, Blocks.CHISELED_NETHER_BRICKS,
        Blocks.BLACKSTONE, Blocks.POLISHED_BLACKSTONE,
        Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.GILDED_BLACKSTONE,
        Blocks.SOUL_SAND, Blocks.SOUL_SOIL,

        // End / purpur
        Blocks.END_STONE, Blocks.END_STONE_BRICKS,
        Blocks.PURPUR_BLOCK, Blocks.PURPUR_PILLAR,

        // Obsidian
        Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN,

        // Prismarine
        Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.PRISMARINE_BRICKS,

        // Terracotta (uncoloured + all 16 colours)
        Blocks.TERRACOTTA,
        Blocks.WHITE_TERRACOTTA,      Blocks.ORANGE_TERRACOTTA,
        Blocks.MAGENTA_TERRACOTTA,    Blocks.LIGHT_BLUE_TERRACOTTA,
        Blocks.YELLOW_TERRACOTTA,     Blocks.LIME_TERRACOTTA,
        Blocks.PINK_TERRACOTTA,       Blocks.GRAY_TERRACOTTA,
        Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA,
        Blocks.PURPLE_TERRACOTTA,     Blocks.BLUE_TERRACOTTA,
        Blocks.BROWN_TERRACOTTA,      Blocks.GREEN_TERRACOTTA,
        Blocks.RED_TERRACOTTA,        Blocks.BLACK_TERRACOTTA,

        // Concrete (all 16 colours)
        Blocks.WHITE_CONCRETE,      Blocks.ORANGE_CONCRETE,
        Blocks.MAGENTA_CONCRETE,    Blocks.LIGHT_BLUE_CONCRETE,
        Blocks.YELLOW_CONCRETE,     Blocks.LIME_CONCRETE,
        Blocks.PINK_CONCRETE,       Blocks.GRAY_CONCRETE,
        Blocks.LIGHT_GRAY_CONCRETE, Blocks.CYAN_CONCRETE,
        Blocks.PURPLE_CONCRETE,     Blocks.BLUE_CONCRETE,
        Blocks.BROWN_CONCRETE,      Blocks.GREEN_CONCRETE,
        Blocks.RED_CONCRETE,        Blocks.BLACK_CONCRETE,

        // Wool (all 16 colours)
        Blocks.WHITE_WOOL,      Blocks.ORANGE_WOOL,
        Blocks.MAGENTA_WOOL,    Blocks.LIGHT_BLUE_WOOL,
        Blocks.YELLOW_WOOL,     Blocks.LIME_WOOL,
        Blocks.PINK_WOOL,       Blocks.GRAY_WOOL,
        Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL,
        Blocks.PURPLE_WOOL,     Blocks.BLUE_WOOL,
        Blocks.BROWN_WOOL,      Blocks.GREEN_WOOL,
        Blocks.RED_WOOL,        Blocks.BLACK_WOOL,

        // Mud family
        Blocks.MUD, Blocks.PACKED_MUD, Blocks.MUD_BRICKS,

        // Mineral / ore blocks
        Blocks.EMERALD_BLOCK,  Blocks.DIAMOND_BLOCK,  Blocks.GOLD_BLOCK,
        Blocks.IRON_BLOCK,     Blocks.LAPIS_BLOCK,    Blocks.COPPER_BLOCK,
        Blocks.AMETHYST_BLOCK,
        Blocks.RAW_IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_COPPER_BLOCK,

        // Weird / organic
        Blocks.SPONGE, Blocks.SLIME_BLOCK, Blocks.HONEY_BLOCK, Blocks.MAGMA_BLOCK,
        Blocks.SEA_LANTERN, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT,
        Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,
        Blocks.SCULK, Blocks.BOOKSHELF, Blocks.HAY_BLOCK, Blocks.HONEYCOMB_BLOCK,

        // Wood / fungus planks and logs
        Blocks.OAK_PLANKS,      Blocks.SPRUCE_PLANKS,   Blocks.BIRCH_PLANKS,
        Blocks.JUNGLE_PLANKS,   Blocks.ACACIA_PLANKS,   Blocks.DARK_OAK_PLANKS,
        Blocks.MANGROVE_PLANKS, Blocks.CHERRY_PLANKS,   Blocks.BAMBOO_PLANKS,
        Blocks.CRIMSON_PLANKS,  Blocks.WARPED_PLANKS,
        Blocks.OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.CRIMSON_HYPHAE, Blocks.WARPED_HYPHAE,
    };

    /**
     * Surface-layer pool — what normally sits at the top of terrain.
     * Grass is weighted slightly higher so there is a modest chance of a "normal" feel.
     */
    private static final Block[] SURFACE_POOL = {
        // Natural (grass appears twice for a slightly higher chance)
        Blocks.GRASS_BLOCK, Blocks.GRASS_BLOCK,
        Blocks.PODZOL, Blocks.MYCELIUM, Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK, Blocks.CLAY,

        // Sandy / gravelly
        Blocks.SAND, Blocks.RED_SAND, Blocks.GRAVEL,

        // Cold
        Blocks.SNOW_BLOCK, Blocks.ICE, Blocks.PACKED_ICE, Blocks.BLUE_ICE,

        // Nether surfaces
        Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM,
        Blocks.SOUL_SAND, Blocks.SOUL_SOIL,

        // Luminous
        Blocks.SEA_LANTERN, Blocks.GLOWSTONE, Blocks.SHROOMLIGHT,
        Blocks.OCHRE_FROGLIGHT, Blocks.VERDANT_FROGLIGHT, Blocks.PEARLESCENT_FROGLIGHT,

        // Wool (all 16)
        Blocks.WHITE_WOOL,      Blocks.ORANGE_WOOL,
        Blocks.MAGENTA_WOOL,    Blocks.LIGHT_BLUE_WOOL,
        Blocks.YELLOW_WOOL,     Blocks.LIME_WOOL,
        Blocks.PINK_WOOL,       Blocks.GRAY_WOOL,
        Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL,
        Blocks.PURPLE_WOOL,     Blocks.BLUE_WOOL,
        Blocks.BROWN_WOOL,      Blocks.GREEN_WOOL,
        Blocks.RED_WOOL,        Blocks.BLACK_WOOL,

        // Concrete powder (all 16)
        Blocks.WHITE_CONCRETE_POWDER,      Blocks.ORANGE_CONCRETE_POWDER,
        Blocks.MAGENTA_CONCRETE_POWDER,    Blocks.LIGHT_BLUE_CONCRETE_POWDER,
        Blocks.YELLOW_CONCRETE_POWDER,     Blocks.LIME_CONCRETE_POWDER,
        Blocks.PINK_CONCRETE_POWDER,       Blocks.GRAY_CONCRETE_POWDER,
        Blocks.LIGHT_GRAY_CONCRETE_POWDER, Blocks.CYAN_CONCRETE_POWDER,
        Blocks.PURPLE_CONCRETE_POWDER,     Blocks.BLUE_CONCRETE_POWDER,
        Blocks.BROWN_CONCRETE_POWDER,      Blocks.GREEN_CONCRETE_POWDER,
        Blocks.RED_CONCRETE_POWDER,        Blocks.BLACK_CONCRETE_POWDER,

        // Misc chaos
        Blocks.SCULK, Blocks.SPONGE, Blocks.SLIME_BLOCK,
        Blocks.HAY_BLOCK, Blocks.TARGET, Blocks.HONEYCOMB_BLOCK,
        Blocks.AMETHYST_BLOCK, Blocks.MUD, Blocks.PACKED_MUD,
    };

    /** Sub-surface pool: softer, earth-like materials that sit between surface and primary. */
    private static final Block[] SUBSURFACE_POOL = {
        Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
        Blocks.GRAVEL, Blocks.SAND, Blocks.CLAY,
        Blocks.MUD, Blocks.SOUL_SOIL, Blocks.MOSS_BLOCK, Blocks.SCULK,
    };
}
