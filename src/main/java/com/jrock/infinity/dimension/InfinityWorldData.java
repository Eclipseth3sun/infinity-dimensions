package com.jrock.infinity.dimension;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists the set of procedural dimension IDs across server restarts.
 *
 * Stored in {@code world/data/infinity_dimensions.dat}.
 */
public class InfinityWorldData extends SavedData {

    private final Set<String> dimensionIds = new LinkedHashSet<>();

    public InfinityWorldData() {}

    public static final Codec<InfinityWorldData> CODEC = Codec.STRING.listOf().xmap(
            list -> {
                InfinityWorldData data = new InfinityWorldData();
                data.dimensionIds.addAll(list);
                return data;
            },
            data -> List.copyOf(data.dimensionIds)
    );

    public static final SavedDataType<InfinityWorldData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("infinity_dimensions", "infinity_dimensions"),
            InfinityWorldData::new,
            CODEC,
            DataFixTypes.LEVEL
    );

    public Set<String> getDimensionIds() { return Collections.unmodifiableSet(dimensionIds); }

    public void addDimension(String id) {
        if (dimensionIds.add(id)) setDirty();
    }

    public boolean contains(String id) { return dimensionIds.contains(id); }
}
