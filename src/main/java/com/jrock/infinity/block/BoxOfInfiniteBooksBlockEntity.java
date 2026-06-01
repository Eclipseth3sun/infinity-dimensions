package com.jrock.infinity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Stores the seed that this placed Box of Infinite Books uses to generate books.
 *
 * <p>When a fresh (unbound) box is placed the seed is derived from its world position so
 * the same block always produces the same title.  When a bound box (previously broken)
 * is placed the seed from the item's saved data is used instead, allowing the player to
 * reproduce the exact same books — and therefore the exact same dimension — anywhere.
 */
public class BoxOfInfiniteBooksBlockEntity extends BlockEntity {

    private static final String NBT_SEED  = "BoundSeed";
    private static final String NBT_BOUND = "Bound";

    private long    seed;
    private boolean bound;

    public BoxOfInfiniteBooksBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BOX_OF_INFINITE_BOOKS_BLOCK_ENTITY, pos, state);
    }

    public long    getSeed()  { return seed;  }
    public boolean isBound()  { return bound; }

    public void setSeed(long seed) {
        this.seed = seed;
        setChanged();
    }

    /** Seals this box, locking its seed so it survives breaking and re-placement. */
    public void setBound(boolean bound) {
        this.bound = bound;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putLong(NBT_SEED, seed);
        output.putBoolean(NBT_BOUND, bound);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        seed  = input.getLongOr(NBT_SEED,  0L);
        bound = input.getBooleanOr(NBT_BOUND, false);
    }
}
