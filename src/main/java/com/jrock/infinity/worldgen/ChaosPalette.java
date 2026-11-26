package com.jrock.infinity.worldgen;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Turns {@link ChaosTerrain}'s abstract material codes into real blocks.
 *
 * <p>Each dimension shuffles a pool of unrelated blocks with its seed and takes 8 of them for the striped
 * "strata" and 4 for the "flora", so one dimension might be built of terracotta, amethyst and
 * moss while the next is basalt, honeycomb and blue ice.
 *
 * <p>Deliberately avoids anything that falls (sand, gravel), decays (leaves), or whose block names changed
 * across 26.x (dyed blocks, copper, wood sets).
 */
final class ChaosPalette {

    private ChaosPalette() {}

    private static List<Block> pool() {
        List<Block> p = new ArrayList<>();
        Collections.addAll(p,
                Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, Blocks.SANDSTONE, Blocks.GRANITE, Blocks.DIORITE,
                Blocks.ANDESITE, Blocks.CALCITE, Blocks.TUFF, Blocks.BASALT, Blocks.SMOOTH_BASALT,
                Blocks.BLACKSTONE, Blocks.NETHERRACK, Blocks.END_STONE, Blocks.AMETHYST_BLOCK,
                Blocks.PRISMARINE, Blocks.DARK_PRISMARINE, Blocks.MAGMA_BLOCK, Blocks.MOSS_BLOCK,
                Blocks.MYCELIUM, Blocks.PODZOL, Blocks.CLAY, Blocks.DRIPSTONE_BLOCK, Blocks.MUD,
                Blocks.BONE_BLOCK, Blocks.SOUL_SAND, Blocks.WARPED_WART_BLOCK, Blocks.NETHER_WART_BLOCK,
                Blocks.SLIME_BLOCK, Blocks.HONEYCOMB_BLOCK, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE,
                Blocks.BLUE_ICE, Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.RAW_IRON_BLOCK,
                Blocks.RAW_GOLD_BLOCK, Blocks.DEEPSLATE, Blocks.COARSE_DIRT);
        return p;
    }

    /** @return an array indexed by {@link ChaosTerrain} material code. */
    static BlockState[] build(long seed, BlockState primary, BlockState surface, BlockState subsurface) {
        BlockState[] s = new BlockState[ChaosTerrain.MATERIAL_COUNT];
        s[ChaosTerrain.AIR] = Blocks.AIR.defaultBlockState();
        s[ChaosTerrain.BEDROCK] = Blocks.BEDROCK.defaultBlockState();
        s[ChaosTerrain.WATER] = Blocks.WATER.defaultBlockState();
        s[ChaosTerrain.LAVA] = Blocks.LAVA.defaultBlockState();
        s[ChaosTerrain.ICE] = Blocks.PACKED_ICE.defaultBlockState();
        s[ChaosTerrain.PRIMARY] = primary;
        s[ChaosTerrain.SURFACE] = surface;
        s[ChaosTerrain.SUBSURFACE] = subsurface;

        Random rng = new Random(ChaosTerrain.mix(seed ^ 0x1CEB00DAL));
        Block[] glow = {Blocks.GLOWSTONE, Blocks.SEA_LANTERN, Blocks.SHROOMLIGHT};
        s[ChaosTerrain.GLOW] = glow[rng.nextInt(glow.length)].defaultBlockState();

        List<Block> blocks = pool();
        Collections.shuffle(blocks, rng);
        for (int i = 0; i < ChaosTerrain.STRATA_COUNT; i++) {
            s[ChaosTerrain.STRATA_BASE + i] = blocks.get(i).defaultBlockState();
        }
        for (int i = 0; i < ChaosTerrain.FLORA_COUNT; i++) {
            s[ChaosTerrain.FLORA_BASE + i] = blocks.get(ChaosTerrain.STRATA_COUNT + i).defaultBlockState();
        }
        return s;
    }
}
