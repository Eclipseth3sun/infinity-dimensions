package com.jrock.infinity.block;

import com.jrock.infinity.InfinityDimensions;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {

    public static Block BOX_OF_INFINITE_BOOKS;
    public static Block INFINITY_PORTAL;
    public static BlockEntityType<BoxOfInfiniteBooksBlockEntity> BOX_OF_INFINITE_BOOKS_BLOCK_ENTITY;
    public static BlockEntityType<InfinityPortalBlockEntity>    INFINITY_PORTAL_BLOCK_ENTITY;

    private ModBlocks() {}

    public static void register() {
        BOX_OF_INFINITE_BOOKS = registerWithItem("box_of_infinite_books", key ->
                new BoxOfInfiniteBooksBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.WOOD)
                        .strength(1.5f)   // hand-breakable; faster with an axe (see axe tag)
                        .noLootTable()    // drops handled manually in BoxOfInfiniteBooksBlock.onRemove
                        .setId(key)));

        BOX_OF_INFINITE_BOOKS_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                Identifier.fromNamespaceAndPath(InfinityDimensions.MOD_ID, "box_of_infinite_books"),
                FabricBlockEntityTypeBuilder.create(
                        BoxOfInfiniteBooksBlockEntity::new, BOX_OF_INFINITE_BOOKS).build());

        // InfinityPortalBlock is world-generated only — no BlockItem.
        ResourceKey<Block> portalKey = blockKey("infinity_portal");
        INFINITY_PORTAL = Registry.register(BuiltInRegistries.BLOCK, portalKey,
                new InfinityPortalBlock(BlockBehaviour.Properties.of()
                        .noCollision()
                        .lightLevel(state -> 11)
                        .strength(-1.0f)
                        .pushReaction(PushReaction.BLOCK)
                        .noLootTable()
                        .setId(portalKey)));

        INFINITY_PORTAL_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                portalKey.identifier(),
                FabricBlockEntityTypeBuilder.create(InfinityPortalBlockEntity::new, INFINITY_PORTAL).build()
        );

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(output ->
                output.accept(new ItemStack(BOX_OF_INFINITE_BOOKS))
        );
    }

    private static ResourceKey<Block> blockKey(String name) {
        return ResourceKey.create(Registries.BLOCK,
                Identifier.fromNamespaceAndPath(InfinityDimensions.MOD_ID, name));
    }

    private static <T extends Block> T registerWithItem(String name, java.util.function.Function<ResourceKey<Block>, T> factory) {
        ResourceKey<Block> key = blockKey(name);
        T block = factory.apply(key);
        Registry.register(BuiltInRegistries.BLOCK, key, block);
        Registry.register(BuiltInRegistries.ITEM, key.identifier(),
                new BlockItem(block, new Item.Properties().setId(
                        ResourceKey.create(Registries.ITEM, key.identifier()))));
        return block;
    }
}
