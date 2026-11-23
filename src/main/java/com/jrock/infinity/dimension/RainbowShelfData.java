package com.jrock.infinity.dimension;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.security.SecureRandom;

/**
 * World-wide state for the Rainbow Infinite Bookshelf.
 *
 * <ul>
 *   <li>{@code counter} - ever-increasing; every book handed out by <i>any</i> rainbow shelf in this
 *       world takes the next number, so no two books ever share a serial.</li>
 *   <li>{@code salt} - random per world, created once. Makes each world's book sequence different.</li>
 * </ul>
 *
 * Persisted through the server's data storage, using the same pattern as {@link InfinityWorldData}.
 */
public class RainbowShelfData extends SavedData {

    private long counter;
    private final long salt;

    public RainbowShelfData(long counter, long salt) {
        this.counter = counter;
        this.salt = salt;
    }

    public static final Codec<RainbowShelfData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.LONG.fieldOf("counter").forGetter(d -> d.counter),
            Codec.LONG.fieldOf("salt").forGetter(d -> d.salt)
    ).apply(inst, RainbowShelfData::new));

    public static final SavedDataType<RainbowShelfData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("infinity_dimensions", "rainbow_bookshelf"),
            () -> new RainbowShelfData(0L, new SecureRandom().nextLong()),
            CODEC,
            DataFixTypes.LEVEL
    );

    /** Issues the next unused serial (1, 2, 3, ...). */
    public long nextSerial() {
        counter++;
        setDirty();
        return counter;
    }

    public long getSalt() { return salt; }
}
