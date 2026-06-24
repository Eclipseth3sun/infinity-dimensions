package com.jrock.infinity.worldgen;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
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
        Blocks.DYED_TERRACOTTA.pick(DyeColor.WHITE),      Blocks.DYED_TERRACOTTA.pick(DyeColor.ORANGE),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.MAGENTA),    Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.YELLOW),     Blocks.DYED_TERRACOTTA.pick(DyeColor.LIME),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.PINK),       Blocks.DYED_TERRACOTTA.pick(DyeColor.GRAY),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.LIGHT_GRAY), Blocks.DYED_TERRACOTTA.pick(DyeColor.CYAN),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.PURPLE),     Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.BROWN),      Blocks.DYED_TERRACOTTA.pick(DyeColor.GREEN),
        Blocks.DYED_TERRACOTTA.pick(DyeColor.RED),        Blocks.DYED_TERRACOTTA.pick(DyeColor.BLACK),

        // Concrete (all 16 colours)
        Blocks.CONCRETE.pick(DyeColor.WHITE),      Blocks.CONCRETE.pick(DyeColor.ORANGE),
        Blocks.CONCRETE.pick(DyeColor.MAGENTA),    Blocks.CONCRETE.pick(DyeColor.LIGHT_BLUE),
        Blocks.CONCRETE.pick(DyeColor.YELLOW),     Blocks.CONCRETE.pick(DyeColor.LIME),
        Blocks.CONCRETE.pick(DyeColor.PINK),       Blocks.CONCRETE.pick(DyeColor.GRAY),
        Blocks.CONCRETE.pick(DyeColor.LIGHT_GRAY), Blocks.CONCRETE.pick(DyeColor.CYAN),
        Blocks.CONCRETE.pick(DyeColor.PURPLE),     Blocks.CONCRETE.pick(DyeColor.BLUE),
        Blocks.CONCRETE.pick(DyeColor.BROWN),      Blocks.CONCRETE.pick(DyeColor.GREEN),
        Blocks.CONCRETE.pick(DyeColor.RED),        Blocks.CONCRETE.pick(DyeColor.BLACK),

        // Wool (all 16 colours)
        Blocks.WOOL.pick(DyeColor.WHITE),      Blocks.WOOL.pick(DyeColor.ORANGE),
        Blocks.WOOL.pick(DyeColor.MAGENTA),    Blocks.WOOL.pick(DyeColor.LIGHT_BLUE),
        Blocks.WOOL.pick(DyeColor.YELLOW),     Blocks.WOOL.pick(DyeColor.LIME),
        Blocks.WOOL.pick(DyeColor.PINK),       Blocks.WOOL.pick(DyeColor.GRAY),
        Blocks.WOOL.pick(DyeColor.LIGHT_GRAY), Blocks.WOOL.pick(DyeColor.CYAN),
        Blocks.WOOL.pick(DyeColor.PURPLE),     Blocks.WOOL.pick(DyeColor.BLUE),
        Blocks.WOOL.pick(DyeColor.BROWN),      Blocks.WOOL.pick(DyeColor.GREEN),
        Blocks.WOOL.pick(DyeColor.RED),        Blocks.WOOL.pick(DyeColor.BLACK),

        // Mud family
        Blocks.MUD, Blocks.PACKED_MUD, Blocks.MUD_BRICKS,

        // Mineral / ore blocks
        Blocks.EMERALD_BLOCK,  Blocks.DIAMOND_BLOCK,  Blocks.GOLD_BLOCK,
        Blocks.IRON_BLOCK,     Blocks.LAPIS_BLOCK,
        Blocks.COPPER_BLOCK.weathering().pick(WeatheringCopper.WeatherState.UNAFFECTED),
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
        Blocks.WOOL.pick(DyeColor.WHITE),      Blocks.WOOL.pick(DyeColor.ORANGE),
        Blocks.WOOL.pick(DyeColor.MAGENTA),    Blocks.WOOL.pick(DyeColor.LIGHT_BLUE),
        Blocks.WOOL.pick(DyeColor.YELLOW),     Blocks.WOOL.pick(DyeColor.LIME),
        Blocks.WOOL.pick(DyeColor.PINK),       Blocks.WOOL.pick(DyeColor.GRAY),
        Blocks.WOOL.pick(DyeColor.LIGHT_GRAY), Blocks.WOOL.pick(DyeColor.CYAN),
        Blocks.WOOL.pick(DyeColor.PURPLE),     Blocks.WOOL.pick(DyeColor.BLUE),
        Blocks.WOOL.pick(DyeColor.BROWN),      Blocks.WOOL.pick(DyeColor.GREEN),
        Blocks.WOOL.pick(DyeColor.RED),        Blocks.WOOL.pick(DyeColor.BLACK),

        // Concrete powder (all 16)
        Blocks.CONCRETE_POWDER.pick(DyeColor.WHITE),      Blocks.CONCRETE_POWDER.pick(DyeColor.ORANGE),
        Blocks.CONCRETE_POWDER.pick(DyeColor.MAGENTA),    Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_BLUE),
        Blocks.CONCRETE_POWDER.pick(DyeColor.YELLOW),     Blocks.CONCRETE_POWDER.pick(DyeColor.LIME),
        Blocks.CONCRETE_POWDER.pick(DyeColor.PINK),       Blocks.CONCRETE_POWDER.pick(DyeColor.GRAY),
        Blocks.CONCRETE_POWDER.pick(DyeColor.LIGHT_GRAY), Blocks.CONCRETE_POWDER.pick(DyeColor.CYAN),
        Blocks.CONCRETE_POWDER.pick(DyeColor.PURPLE),     Blocks.CONCRETE_POWDER.pick(DyeColor.BLUE),
        Blocks.CONCRETE_POWDER.pick(DyeColor.BROWN),      Blocks.CONCRETE_POWDER.pick(DyeColor.GREEN),
        Blocks.CONCRETE_POWDER.pick(DyeColor.RED),        Blocks.CONCRETE_POWDER.pick(DyeColor.BLACK),

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
