package com.jrock.infinity.block;

import com.jrock.infinity.dimension.DimensionHasher;
import com.jrock.infinity.dimension.InfinityDimensionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Coloured portal block that replaces a Nether Portal frame after a Written Book
 * is thrown through it.  Teleports entities to the dimension stored in its BlockEntity.
 *
 * <p>Additional behaviour vs. the original:
 * <ul>
 *   <li>Accepts a new Written Book thrown into it to redirect the portal to a different dimension.</li>
 *   <li>Generates a return portal in the target dimension on first arrival.</li>
 *   <li>Uses the actual chunk heightmap for spawn Y so players never land in mid-air.</li>
 * </ul>
 */
public class InfinityPortalBlock extends Block implements EntityBlock, Portal {

    public static final EnumProperty<Direction.Axis> AXIS  = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty             COLOR = IntegerProperty.create("color", 0, 8);

    /** Number of color variants — must match the number of textures. */
    private static final int NUM_COLORS = 9;

    public InfinityPortalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(COLOR, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, COLOR);
    }

    /** Deterministic color index (0–8) for any dimension ID string. */
    public static int colorIndexForId(String dimId) {
        if (dimId == null || dimId.isEmpty()) return 0;
        long h = 0;
        for (char c : dimId.toCharArray()) h = h * 31L + c;
        return (int) (Math.abs(h) % NUM_COLORS);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InfinityPortalBlockEntity(pos, state);
    }

    // ── Entity collision ──────────────────────────────────────────────────────

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (level.isClientSide()) return;

        // Written book thrown into the portal → redirect all connected portal blocks
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack stack = itemEntity.getItem();
            if (stack.is(Items.WRITTEN_BOOK)) {
                String newDimId = DimensionHasher.dimensionIdFor(stack);
                if (newDimId != null) {
                    itemEntity.discard();
                    updateConnectedPortals((ServerLevel) level, pos, newDimId);
                    // Record the current portal position so return portals always resolve
                    // back to wherever THIS portal now lives, not where it originally was.
                    InfinityDimensionManager.recordEntryPortal((ServerLevel) level, pos, newDimId);
                    InfinityDimensionManager.ensureDimension(newDimId);
                }
                return;
            }
        }

        if (!entity.canUsePortal(false)) return;
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof InfinityPortalBlockEntity portal && portal.getDimensionId() != null) {
            entity.setAsInsidePortal(this, pos);
        }
    }

    // ── Portal teleportation ──────────────────────────────────────────────────

    @Override
    @Nullable
    public TeleportTransition getPortalDestination(ServerLevel currentLevel, Entity entity, BlockPos portalEntryPos) {
        BlockEntity be = currentLevel.getBlockEntity(portalEntryPos);
        if (!(be instanceof InfinityPortalBlockEntity portal)) return null;
        String dimId = portal.getDimensionId();
        if (dimId == null || dimId.isEmpty()) return null;

        ServerLevel target = resolveLevel(currentLevel, dimId);
        if (target == null) return null;

        // The fully-qualified ID of the dimension we're leaving — stored in the return portal.
        String sourceDimId = currentLevel.dimension().identifier().toString();

        // A dimension ID containing ":" is a vanilla/external dimension (minecraft:overworld etc.).
        // We return to world spawn without touching that world's terrain.
        if (dimId.contains(":")) {
            // Returning to a vanilla/external dimension.
            // Priority 1: live entry-portal position tracked this session (handles moved portals).
            // Priority 2: arrivalPos saved when the return portal was first generated.
            // Priority 3: world spawn (last resort / first login ever).

            // The path portion of our dimension key matches what is stored in portal block entities.
            // e.g. "infinity_dimensions:a3f7…" → path "a3f7…"
            String currentDimPath = currentLevel.dimension().identifier().getPath();

            net.minecraft.core.GlobalPos liveEntry =
                    InfinityDimensionManager.getEntryPortal(currentDimPath);
            if (liveEntry != null && liveEntry.dimension().equals(target.dimension())) {
                BlockPos ep = liveEntry.pos();
                // Verify the portal block is still there and still targets this dimension.
                BlockEntity epBe = target.getBlockEntity(ep);
                if (epBe instanceof InfinityPortalBlockEntity epPortal
                        && currentDimPath.equals(epPortal.getDimensionId())) {
                    return new TeleportTransition(target,
                            new Vec3(ep.getX() + 0.5, ep.getY(), ep.getZ() + 1.5),
                            Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                            TeleportTransition.PLAY_PORTAL_SOUND);
                }
            }

            // Session map miss (e.g. after a restart) — fall back to stored arrival hint.
            BlockPos arrivalHint = portal.getArrivalPos();
            if (arrivalHint != null) {
                return new TeleportTransition(target,
                        new Vec3(arrivalHint.getX() + 0.5, arrivalHint.getY(), arrivalHint.getZ() + 1.5),
                        Vec3.ZERO, entity.getYRot(), entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND);
            }

            // Last resort: world spawn.
            BlockPos worldSpawn = target.getRespawnData().pos();
            int wx = worldSpawn.getX(), wz = worldSpawn.getZ();
            target.getChunk(wx >> 4, wz >> 4);
            int surface = target.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
            int ground  = Math.max(target.getMinY(), surface - 1);
            return new TeleportTransition(target,
                    new Vec3(wx + 0.5, ground + 1, wz + 0.5), Vec3.ZERO,
                    entity.getYRot(), entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND);
        }

        // Arriving at an infinity dimension: ensure a return portal exists near spawn.
        int spawnX = 8, spawnZ = 8;
        target.getChunk(spawnX >> 4, spawnZ >> 4);
        int surface = target.getHeight(Heightmap.Types.MOTION_BLOCKING, spawnX, spawnZ);

        // Inverted / ceiling styles report a surface near the world top (e.g. y=319).
        // Placing a portal there would push blocks out of bounds.  Force the fallback
        // platform path by treating near-ceiling heights as "no ground found".
        int worldTop = target.getMinY() + target.getHeight(); // e.g. 320
        if (surface >= worldTop - 20) {
            surface = target.getMinY(); // triggers scan / platform creation below
        }

        // Sparse / void-style dimensions (e.g. floating islands) may have no solid block at
        // the default spawn column.  Scan outward for the nearest actual surface so the
        // return portal lands on real terrain rather than the void floor.
        // foundGround tracks whether we located a real surface above the world floor —
        // used by placeReturnPortal to decide whether a stone platform is needed.
        boolean foundGround = surface > target.getMinY();
        if (!foundGround) {
            int[] found = scanForGround(target, spawnX, spawnZ, 32);
            if (found != null) {
                // Ignore any results still near the world ceiling (same inverted problem).
                if (found[2] < worldTop - 20) {
                    spawnX      = found[0];
                    spawnZ      = found[1];
                    surface     = found[2];
                    foundGround = true;
                }
            }
            // If still no usable surface, placeReturnPortal creates a stone platform.
        }

        int ground = Math.max(target.getMinY(), surface - 1);

        if (!hasPortalNearby(target, spawnX, ground, spawnZ)) {
            ground = placeReturnPortal(target, spawnX, ground, spawnZ, sourceDimId, portalEntryPos, foundGround);
        }

        // Arrive just in front of the return portal (one block in +Z from the portal face)
        return new TeleportTransition(target,
                new Vec3(spawnX + 0.5, ground + 1, spawnZ + 1.5), Vec3.ZERO,
                entity.getYRot(), entity.getXRot(), TeleportTransition.PLAY_PORTAL_SOUND);
    }

    @Override
    public int getPortalTransitionTime(ServerLevel level, Entity entity) {
        return 0;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Resolves a stored dimension ID to a live ServerLevel.
     * IDs containing ":" are fully-qualified resource locations (handled directly).
     * IDs without ":" are managed by InfinityDimensionManager.
     */
    private static @Nullable ServerLevel resolveLevel(ServerLevel current, String dimId) {
        if (dimId.contains(":")) {
            String[] parts = dimId.split(":", 2);
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION,
                    Identifier.fromNamespaceAndPath(parts[0], parts[1]));
            return current.getServer().getLevel(key);
        }
        InfinityDimensionManager.ensureDimension(dimId);
        ResourceKey<Level> key = InfinityDimensionManager.getKey(dimId);
        if (key == null) return null;
        return current.getServer().getLevel(key);
    }

    /**
     * Returns true if any InfinityPortalBlock exists within ±8 blocks horizontally and
     * −4 / +8 blocks vertically of (cx, cy, cz).
     */
    private static boolean hasPortalNearby(ServerLevel world, int cx, int cy, int cz) {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dy = -4; dy <= 8; dy++) {
                for (int dz = -8; dz <= 8; dz++) {
                    if (world.getBlockState(new BlockPos(cx + dx, cy + dy, cz + dz))
                            .is(ModBlocks.INFINITY_PORTAL)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Builds a 4-wide × 5-tall obsidian-framed InfinityPortal at (cx, groundLevel, cz).
     * The portal blocks use AXIS=X so the open face points in the ±Z direction.
     *
     * <pre>
     *   y+4:  O O O O       O = obsidian
     *   y+3:  O P P O       P = InfinityPortalBlock (returnDimId)
     *   y+2:  O P P O
     *   y+1:  O P P O
     *   y+0:  O O O O       ← groundLevel
     *   x: cx-1 cx cx+1 cx+2
     * </pre>
     *
     * Player arrives at (cx+0.5, groundLevel+1, cz+1.5), one block in front of the portal face.
     */
    /**
     * Builds a 4-wide × 5-tall obsidian-framed InfinityPortal at (cx, groundLevel, cz).
     * Also lays a solid landing strip one block in front of the portal so players cannot
     * fall into the void immediately after arriving on sparse terrain.
     *
     * @return the groundLevel actually used — may differ from the input when a platform
     *         is created for void / no-island dimensions
     */
    private static int placeReturnPortal(ServerLevel world, int cx, int groundLevel,
                                         int cz, String returnDimId, @Nullable BlockPos arrivalPos,
                                         boolean foundGround) {
        // Void / no-island case: no solid terrain was found near spawn.
        // Create a stone platform at a safe height so the portal has something to stand on.
        if (!foundGround) {
            groundLevel = 80;
            for (int dx = -2; dx <= 3; dx++) {
                for (int dz = -1; dz <= 2; dz++) {
                    world.setBlock(new BlockPos(cx + dx, groundLevel - 1, cz + dz),
                            Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        // Clear existing blocks in the portal volume (+ one row above for head room)
        for (int dx = -1; dx <= 2; dx++) {
            for (int dy = 0; dy <= 5; dy++) {
                BlockPos p = new BlockPos(cx + dx, groundLevel + dy, cz);
                if (!world.getBlockState(p).is(Blocks.OBSIDIAN)) {
                    world.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        // Make sure there is a solid block directly under the frame bottom
        for (int dx = -1; dx <= 2; dx++) {
            BlockPos floor = new BlockPos(cx + dx, groundLevel - 1, cz);
            if (world.getBlockState(floor).isAir()) {
                world.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), 3);
            }
        }

        // Obsidian frame
        BlockState obs = Blocks.OBSIDIAN.defaultBlockState();
        for (int dx = -1; dx <= 2; dx++)            // bottom row
            world.setBlock(new BlockPos(cx + dx, groundLevel,     cz), obs, 3);
        for (int dx = -1; dx <= 2; dx++)            // top row
            world.setBlock(new BlockPos(cx + dx, groundLevel + 4, cz), obs, 3);
        for (int dy = 1; dy <= 3; dy++)             // left column
            world.setBlock(new BlockPos(cx - 1,   groundLevel + dy, cz), obs, 3);
        for (int dy = 1; dy <= 3; dy++)             // right column
            world.setBlock(new BlockPos(cx + 2,   groundLevel + dy, cz), obs, 3);

        // Portal interior (2 × 3, AXIS=X)
        BlockState portalState = ModBlocks.INFINITY_PORTAL.defaultBlockState()
                .setValue(InfinityPortalBlock.AXIS, Direction.Axis.X)
                .setValue(InfinityPortalBlock.COLOR, InfinityPortalBlock.colorIndexForId(returnDimId));
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 1; dy <= 3; dy++) {
                BlockPos pp = new BlockPos(cx + dx, groundLevel + dy, cz);
                world.setBlock(pp, portalState, 3);
                BlockEntity pbe = world.getBlockEntity(pp);
                if (pbe instanceof InfinityPortalBlockEntity portalBe) {
                    portalBe.setDimensionId(returnDimId);
                    if (arrivalPos != null) portalBe.setArrivalPos(arrivalPos);
                }
            }
        }

        // Landing strip: guarantee solid floor at z+1 (where the player arrives) and
        // two blocks of head room above it.  On sparse terrain this row would be void air.
        for (int dx = -1; dx <= 2; dx++) {
            BlockPos landingFloor = new BlockPos(cx + dx, groundLevel, cz + 1);
            if (world.getBlockState(landingFloor).isAir()) {
                world.setBlock(landingFloor, Blocks.STONE.defaultBlockState(), 3);
            }
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos above = new BlockPos(cx + dx, groundLevel + dy, cz + 1);
                if (!world.getBlockState(above).isAir()) {
                    world.setBlock(above, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        return groundLevel;
    }

    /** Walks connected InfinityPortalBlock faces and updates each one's stored dimension ID. */
    private static void updateConnectedPortals(ServerLevel world, BlockPos origin, String newDimId) {
        List<BlockPos> connected = new ArrayList<>();
        collectConnected(world, origin, connected, new HashSet<>(), 64);
        int newColor = colorIndexForId(newDimId);
        for (BlockPos pp : connected) {
            BlockState current = world.getBlockState(pp);
            if (current.is(ModBlocks.INFINITY_PORTAL)) {
                world.setBlock(pp, current.setValue(COLOR, newColor), 3);
            }
            BlockEntity be = world.getBlockEntity(pp);
            if (be instanceof InfinityPortalBlockEntity p) {
                p.setDimensionId(newDimId);
            }
        }
    }

    private static void collectConnected(ServerLevel world, BlockPos pos,
                                         List<BlockPos> result, Set<BlockPos> visited, int limit) {
        if (visited.contains(pos) || result.size() >= limit) return;
        visited.add(pos);
        if (!world.getBlockState(pos).is(ModBlocks.INFINITY_PORTAL)) return;
        result.add(pos.immutable());
        for (Direction dir : Direction.values()) {
            collectConnected(world, pos.relative(dir), result, visited, limit);
        }
    }

    /**
     * Scans a grid of columns around (cx, cz) at 8-block intervals up to maxRadius,
     * returning {x, z, surfaceY} for the nearest column that has actual solid blocks.
     * Returns null if no solid ground is found within the search area.
     */
    @Nullable
    private static int[] scanForGround(ServerLevel world, int cx, int cz, int maxRadius) {
        for (int dx = -maxRadius; dx <= maxRadius; dx += 8) {
            for (int dz = -maxRadius; dz <= maxRadius; dz += 8) {
                int tx = cx + dx, tz = cz + dz;
                world.getChunk(tx >> 4, tz >> 4);
                int h = world.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz);
                if (h > world.getMinY()) {
                    return new int[]{tx, tz, h};
                }
            }
        }
        return null;
    }
}
