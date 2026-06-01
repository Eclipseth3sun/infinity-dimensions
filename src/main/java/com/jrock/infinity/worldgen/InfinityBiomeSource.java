package com.jrock.infinity.worldgen;

import com.jrock.infinity.InfinityDimensions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * Biome source that picks a single fixed biome based on the dimension seed.
 * Each procedural infinity dimension therefore has its own distinct biome feel.
 */
public class InfinityBiomeSource extends BiomeSource {

    public static final MapCodec<InfinityBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.LONG.fieldOf("seed").forGetter(s -> s.seed)
            ).apply(instance, InfinityBiomeSource::fromCodec)
    );

    @SuppressWarnings("unchecked")
    private static final ResourceKey<Biome>[] BIOME_POOL = new ResourceKey[]{
            Biomes.PLAINS,             Biomes.DESERT,
            Biomes.FOREST,             Biomes.TAIGA,
            Biomes.SWAMP,              Biomes.JUNGLE,
            Biomes.SAVANNA,            Biomes.BADLANDS,
            Biomes.OCEAN,              Biomes.DEEP_OCEAN,
            Biomes.COLD_OCEAN,         Biomes.WARM_OCEAN,
            Biomes.FROZEN_OCEAN,       Biomes.RIVER,
            Biomes.FROZEN_RIVER,       Biomes.BEACH,
            Biomes.SNOWY_PLAINS,       Biomes.ICE_SPIKES,
            Biomes.MUSHROOM_FIELDS,    Biomes.DARK_FOREST,
            Biomes.BIRCH_FOREST,       Biomes.OLD_GROWTH_PINE_TAIGA,
            Biomes.FLOWER_FOREST,      Biomes.SUNFLOWER_PLAINS,
            Biomes.WINDSWEPT_HILLS,    Biomes.WINDSWEPT_GRAVELLY_HILLS,
            Biomes.STONY_SHORE,        Biomes.MEADOW,
            Biomes.CHERRY_GROVE,       Biomes.GROVE,
            Biomes.JAGGED_PEAKS,       Biomes.FROZEN_PEAKS,
    };

    private final long seed;
    private final Holder<Biome> biome;

    public InfinityBiomeSource(RegistryAccess registryAccess, long seed) {
        this.seed  = seed;
        this.biome = resolveBiome(registryAccess, seed);
    }

    /** Codec path — biome is null until the full registry constructor is used. */
    private InfinityBiomeSource(long seed) {
        this.seed  = seed;
        this.biome = null;
    }

    private static InfinityBiomeSource fromCodec(long seed) {
        return new InfinityBiomeSource(seed);
    }

    // ── BiomeSource ───────────────────────────────────────────────────────────

    @Override
    protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        if (biome == null) throw new IllegalStateException(
                "InfinityBiomeSource not fully initialised (seed=" + seed + ")");
        return biome;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biome != null ? Stream.of(biome) : Stream.empty();
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void register() {
        Registry.register(BuiltInRegistries.BIOME_SOURCE,
                Identifier.fromNamespaceAndPath(InfinityDimensions.MOD_ID, "infinity"),
                CODEC);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Holder<Biome> resolveBiome(RegistryAccess registryAccess, long seed) {
        HolderGetter<Biome> lookup = registryAccess.lookupOrThrow(Registries.BIOME);
        int index = (int) (Math.abs(seed) % BIOME_POOL.length);
        return lookup.getOrThrow(BIOME_POOL[index]);
    }

    public long getSeed() { return seed; }
}
