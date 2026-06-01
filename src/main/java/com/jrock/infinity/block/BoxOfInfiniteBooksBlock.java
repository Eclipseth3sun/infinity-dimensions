package com.jrock.infinity.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Port of the 20w14∞ "Box of Infinite Books" block.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li><b>First placement (unbound item)</b>: seed is derived deterministically from the
 *       block's world position — the same position always gives the same book.</li>
 *   <li><b>Right-click</b>: gives the player a Written Book whose title is drawn from the
 *       stored seed.  Same seed → same title → same dimension.</li>
 *   <li><b>Breaking</b>: drops a <em>bound</em> item whose NBT contains the original seed
 *       and whose visual is marked with an enchantment glint (like a signed book).</li>
 *   <li><b>Re-placement (bound item)</b>: reads the seed from the item NBT so the block
 *       produces exactly the same books at any new location.</li>
 * </ol>
 */
public class BoxOfInfiniteBooksBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final String NBT_BOUND_SEED = "BoundSeed";

    private static final String[] ADJECTIVES = {
        "Abandoned", "Ancient", "Burning", "Celestial", "Corrupt", "Crimson",
        "Distant", "Echoing", "Endless", "Ethereal", "Fading", "Forgotten",
        "Frozen", "Glowing", "Hollow", "Infinite", "Inverted", "Jagged",
        "Lost", "Luminous", "Mossy", "Murky", "Obsidian", "Pale",
        "Peculiar", "Prismatic", "Quiet", "Radiant", "Ruined", "Shattered",
        "Silent", "Singing", "Spectral", "Sunken", "Twisted", "Undying",
        "Vast", "Verdant", "Wandering", "Weathered", "Whispering", "Withered",
        "Wooden", "Woven", "Yellow", "Zealous"
    };

    private static final String[] NOUNS = {
        "Abyss", "Archive", "Atlas", "Bastion", "Canopy", "Cavern",
        "Chronicle", "Citadel", "Convergence", "Corridor", "Depths", "Domain",
        "Dream", "Echo", "Edge", "Expanse", "Fracture", "Garden",
        "Gateway", "Grove", "Hall", "Horizon", "Isle", "Junction",
        "Labyrinth", "Lattice", "Library", "Margin", "Maze", "Mire",
        "Mountain", "Nexus", "Passage", "Peak", "Pinnacle", "Plane",
        "Realm", "Rift", "Rise", "Sanctum", "Sea", "Shade",
        "Shore", "Spire", "Summit", "Threshold", "Vault", "Void",
        "Wastes", "Wilds"
    };

    public BoxOfInfiniteBooksBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ── BlockEntity ───────────────────────────────────────────────────────────

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoxOfInfiniteBooksBlockEntity(pos, state);
    }

    /**
     * Called immediately after the block is placed.  Sets the block entity's seed either
     * from the item's bound data or from a deterministic position hash.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide()) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BoxOfInfiniteBooksBlockEntity box)) return;

        long seed;
        boolean wasBound = false;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains(NBT_BOUND_SEED)) {
                seed     = tag.getLongOr(NBT_BOUND_SEED, hashPos(pos));
                wasBound = true; // item carried a seed → it was previously bound
            } else {
                seed = hashPos(pos);
            }
        } else {
            seed = hashPos(pos);
        }
        box.setSeed(seed);
        if (wasBound) box.setBound(true);
    }

    // ── Binding interaction ───────────────────────────────────────────────────

    /**
     * Right-clicking the box with a Glow Ink Sac seals it: the current seed is locked,
     * the ink sac is consumed, and particles confirm the action.  A bound box will drop
     * a glowing bound item when broken; an unbound box drops a plain item with no data.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                          BlockPos pos, Player player, InteractionHand hand,
                                          net.minecraft.world.phys.BlockHitResult hit) {
        // Not an echo shard — fall through to useWithoutItem (book dispensing).
        // TRY_WITH_EMPTY_HAND is required in MC 26.1.2: PASS alone no longer triggers
        // useWithoutItem as a fallback.
        if (!stack.is(Items.ECHO_SHARD)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof BoxOfInfiniteBooksBlockEntity box)) return InteractionResult.PASS;
        if (box.isBound()) return InteractionResult.PASS; // already sealed, don't consume

        box.setBound(true);
        if (!player.isCreative()) stack.shrink(1);

        // Resonant chime — the shard locks the dimensional frequency into place
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0f, 0.6f + level.getRandom().nextFloat() * 0.2f);

        // Portal particles rise from the box, echoing the dimensional seal
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    20, 0.3, 0.3, 0.3, 0.05);
        }
        return InteractionResult.SUCCESS;
    }

    // ── Book dispensing ───────────────────────────────────────────────────────

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (world.isClientSide()) return InteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        long seed = (be instanceof BoxOfInfiniteBooksBlockEntity box) ? box.getSeed() : hashPos(pos);

        ItemStack book = generateBook(seed);
        if (!player.getInventory().add(book)) {
            player.drop(book, false);
        }

        world.playSound(null, pos, SoundEvents.BOOK_PUT,
                SoundSource.BLOCKS, 1.0f, world.getRandom().nextFloat() * 0.1f + 0.9f);

        return InteractionResult.SUCCESS;
    }

    // ── Breaking → bound item drop ─────────────────────────────────────────────

    /**
     * Called when a player finishes breaking the block.  Drops the bound item (with the
     * stored seed and enchantment glint) instead of going through the loot-table system.
     * Creative-mode players receive no drop, matching vanilla block-breaking behaviour.
     */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool); // loot table is empty
        if (!level.isClientSide() && !player.isCreative()) {
            // blockEntity is @Nullable in MC — fall back to a live lookup if null.
            BlockEntity be = blockEntity != null ? blockEntity : level.getBlockEntity(pos);
            if (be instanceof BoxOfInfiniteBooksBlockEntity box) {
                // Bound → glowing item with stored seed.  Unbound → plain item, no data.
                ItemStack drop = box.isBound()
                        ? createBoundItem(box.getSeed())
                        : new ItemStack(ModBlocks.BOX_OF_INFINITE_BOOKS.asItem());
                popResource(level, pos, drop);
            } else {
                // No block entity at all (shouldn't happen) — drop a plain item.
                popResource(level, pos, new ItemStack(ModBlocks.BOX_OF_INFINITE_BOOKS.asItem()));
            }
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    /**
     * Creates a bound item that carries {@code seed} in its NBT and displays an
     * enchantment glint to signal that its book data is fixed.
     */
    public static ItemStack createBoundItem(long seed) {
        ItemStack stack = new ItemStack(ModBlocks.BOX_OF_INFINITE_BOOKS.asItem());
        CompoundTag tag = new CompoundTag();
        tag.putLong(NBT_BOUND_SEED, seed);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return stack;
    }

    /**
     * Generates a Written Book whose title is deterministically derived from {@code seed}.
     * Identical seeds always produce identical titles and therefore identical dimensions.
     */
    public static ItemStack generateBook(long seed) {
        Random rng = new Random(seed);
        String adj  = ADJECTIVES[rng.nextInt(ADJECTIVES.length)];
        String noun = NOUNS[rng.nextInt(NOUNS.length)];
        String title = adj + " " + noun;
        return createWrittenBook(title, "The Box");
    }

    /**
     * Creates a Written Book encoded in the original 20w14∞ style:
     * 16 pages each containing 2 hex characters from the MD5 hash of the
     * lower-cased title.  Joining all 16 pages yields the 32-character hex
     * dimension ID used by {@link com.jrock.infinity.dimension.DimensionHasher}.
     */
    public static ItemStack createWrittenBook(String title, String author) {
        String hex = md5Hex(title.toLowerCase(Locale.ROOT));

        List<Filterable<Component>> pages = new ArrayList<>(16);
        for (int i = 0; i < 16; i++) {
            pages.add(Filterable.passThrough(
                    Component.literal(hex.substring(i * 2, i * 2 + 2))));
        }

        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough(title), author, 0, pages, true));
        return stack;
    }

    /** MD5 of {@code input} returned as a 32-character lower-case hex string. */
    public static String md5Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 unavailable", e);
        }
    }

    /**
     * Deterministic position hash — the same {@code BlockPos} always yields the same seed,
     * so an unbound box at a given location always produces the same book.
     */
    private static long hashPos(BlockPos pos) {
        long h = pos.asLong();
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }
}
