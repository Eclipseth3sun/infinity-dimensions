package com.jrock.infinity.mixin;

import com.jrock.infinity.dimension.DimensionHasher;
import com.jrock.infinity.dimension.InfinityDimensionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts entities entering a Nether Portal.
 * Written Books are consumed and the portal frame is converted to an InfinityPortalBlock
 * pointing at the dimension derived from the book's content.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void infinityDimensions$onBookCollision(BlockState state, Level world,
                                                    BlockPos pos, Entity entity,
                                                    InsideBlockEffectApplier effectApplier,
                                                    boolean isPrecise, CallbackInfo ci) {
        if (world.isClientSide()) return;
        if (!(entity instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.is(Items.WRITTEN_BOOK)) return;

        String dimensionId = DimensionHasher.dimensionIdFor(stack);
        if (dimensionId == null) return;

        itemEntity.discard();
        InfinityDimensionManager.onBookThrownIntoPortal((ServerLevel) world, pos, dimensionId);
        ci.cancel();
    }
}
