package com.jrock.infinity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the dimension ID this portal frame leads to, and — for return portals —
 * the exact block position in the source world where the player should arrive.
 */
public class InfinityPortalBlockEntity extends BlockEntity {

    private static final String NBT_DIMENSION_ID = "DimensionId";
    /** Packed long (BlockPos.asLong) of where to arrive in the destination dimension. */
    private static final String NBT_ARRIVAL_POS  = "ArrivalPos";

    @Nullable private String   dimensionId;
    @Nullable private BlockPos arrivalPos;

    public InfinityPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.INFINITY_PORTAL_BLOCK_ENTITY, pos, state);
    }

    public @Nullable String   getDimensionId() { return dimensionId; }
    public @Nullable BlockPos getArrivalPos()  { return arrivalPos;  }

    public void setDimensionId(String id) {
        this.dimensionId = id;
        setChanged();
    }

    public void setArrivalPos(BlockPos pos) {
        this.arrivalPos = pos;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (dimensionId != null) output.putString(NBT_DIMENSION_ID, dimensionId);
        if (arrivalPos  != null) output.putLong(NBT_ARRIVAL_POS, arrivalPos.asLong());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        dimensionId = input.getString(NBT_DIMENSION_ID).orElse(null);
        arrivalPos  = input.getLong(NBT_ARRIVAL_POS).map(BlockPos::of).orElse(null);
    }
}
