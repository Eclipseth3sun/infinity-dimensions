package com.jrock.infinity.dimension;

import com.google.common.collect.ImmutableList;
import com.jrock.infinity.InfinityDimensions;
import com.jrock.infinity.block.InfinityPortalBlock;
import com.jrock.infinity.block.InfinityPortalBlockEntity;
import com.jrock.infinity.block.ModBlocks;
import com.jrock.infinity.mixin.MinecraftServerAccessor;
import com.jrock.infinity.worldgen.InfinityBiomeSource;
import com.jrock.infinity.worldgen.InfinityChunkGenerator;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.DerivedLevelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central manager for all infinity dimensions.
 *
 * - On server start: re-registers every procedural dimension persisted in InfinityWorldData.
 * - On book-thrown-in-portal: derives the dimension ID, ensures the world exists,
 *   converts the portal frame to InfinityPortalBlocks.
 * - On teleport request: finds or creates the target ServerLevel and sends the player there.
 *
 * <h3>Thread-safety note</h3>
 * {@link #ensureProceduralDimension} deliberately does NOT call {@code server.execute()} to
 * defer the {@code levels.put()} because {@code MinecraftServer.execute()} can run the task
 * synchronously when called from the server thread, which would mutate the levels map while
 * {@code tickChildren()} is iterating it (→ CME).  Instead we enqueue into
 * {@link #PENDING_CREATIONS} and drain it in {@link #processPendingCreations()}, which is
 * called from a {@code ServerTickEvents.END_SERVER_TICK} listener registered in
 * {@link com.jrock.infinity.InfinityDimensions} — guaranteed to run after
 * {@code tickChildren()} completes.
 */
public final class InfinityDimensionManager {

    private static final Map<String, ResourceKey<Level>> ACTIVE_KEYS = new HashMap<>();
    private static MinecraftServer server;

    /**
     * Maps dimension-ID path (e.g. "a3f7…" or "cave") → the GlobalPos of the most recently
     * activated entry portal in the source world.  Updated every time a book is thrown into
     * any portal targeting that dimension, so it always reflects the current portal location
     * within a server session.  Cleared on server stop; falls back to the BlockEntity's
     * stored arrivalPos across restarts.
     */
    private static final Map<String, GlobalPos> ENTRY_PORTAL_POSITIONS = new HashMap<>();

    /** Pending procedural-dimension creations — drained at END_SERVER_TICK. */
    private static final Deque<PendingCreation> PENDING_CREATIONS = new ArrayDeque<>();

    private record PendingCreation(String hexId, ResourceKey<Level> key, ChunkGenerator generator) {}

    private InfinityDimensionManager() {}

    // ── Server lifecycle ───────────────────────────────────────────────────────

    public static void onServerStart(MinecraftServer srv) {
        server = srv;
        ACTIVE_KEYS.clear();
        PENDING_CREATIONS.clear();

        // Named dimensions are registered via data pack — just cache keys for fast lookup.
        for (NamedDimension named : NamedDimension.values()) {
            ResourceKey<Level> key = worldKey(named.getId());
            if (srv.getLevel(key) != null) {
                ACTIVE_KEYS.put(named.getId(), key);
            }
        }

        // Re-register procedural dimensions from the previous session.
        InfinityWorldData data = srv.getDataStorage().computeIfAbsent(InfinityWorldData.TYPE);
        for (String id : data.getDimensionIds()) {
            ensureProceduralDimension(id);
        }

        // Drain immediately — tickChildren() has not started yet, so levels.put() is safe here.
        // This ensures all persisted dimensions are live before the first player logs in,
        // preventing players from being ejected to overworld spawn on rejoin.
        processPendingCreations();

        InfinityDimensions.LOGGER.info("Infinity Dimensions: {} world(s) active.", ACTIVE_KEYS.size());
    }

    public static void onServerStop(MinecraftServer srv) {
        ACTIVE_KEYS.clear();
        PENDING_CREATIONS.clear();
        ENTRY_PORTAL_POSITIONS.clear();
        server = null;
    }

    /**
     * Drains the pending-creation queue.  Must only be called after
     * {@code tickChildren()} has finished for the current tick (i.e., from
     * {@code ServerTickEvents.END_SERVER_TICK}).
     */
    public static void processPendingCreations() {
        if (server == null || PENDING_CREATIONS.isEmpty()) return;

        PendingCreation pending;
        while ((pending = PENDING_CREATIONS.poll()) != null) {
            final String hexId = pending.hexId();
            ServerLevel newWorld = createServerLevel(pending.key(), pending.generator());
            if (newWorld != null) {
                InfinityWorldData data = server.getDataStorage().computeIfAbsent(InfinityWorldData.TYPE);
                data.addDimension(hexId);
                ServerLevelEvents.LOAD.invoker().onLevelLoad(server, newWorld);
            } else {
                // Creation failed — remove the optimistic key so it can be retried.
                ACTIVE_KEYS.remove(hexId);
                InfinityDimensions.LOGGER.error("Failed to create procedural dimension '{}'", hexId);
            }
        }
    }

    // ── Entry-portal tracking ─────────────────────────────────────────────────

    /**
     * Records the current location of the portal that leads into {@code dimensionId}.
     * Called whenever a book is thrown into any portal (new or existing) so the map
     * always reflects where the portal currently is, not where it was originally built.
     *
     * @param world       the level containing the portal block
     * @param portalPos   any block position within the portal frame
     * @param dimensionId the path-only dimension ID (e.g. {@code "a3f7…"} or {@code "cave"})
     */
    public static void recordEntryPortal(ServerLevel world, BlockPos portalPos, String dimensionId) {
        ENTRY_PORTAL_POSITIONS.put(dimensionId, GlobalPos.of(world.dimension(), portalPos.immutable()));
    }

    /**
     * Returns the last recorded entry-portal location for {@code dimensionIdPath}, or
     * {@code null} if no book has been thrown into this dimension's portal this session.
     */
    public static @Nullable GlobalPos getEntryPortal(String dimensionIdPath) {
        return ENTRY_PORTAL_POSITIONS.get(dimensionIdPath);
    }

    // ── Book-in-portal entry point ────────────────────────────────────────────

    public static void onBookThrownIntoPortal(ServerLevel world, BlockPos portalPos, String dimensionId) {
        recordEntryPortal(world, portalPos, dimensionId);
        ensureDimension(dimensionId);
        convertPortalFrame(world, portalPos, dimensionId);
    }

    // ── Teleportation ─────────────────────────────────────────────────────────

    public static void teleportPlayer(ServerPlayer player, ServerLevel fromWorld, String dimensionId) {
        if (server == null) return;

        ResourceKey<Level> key = ACTIVE_KEYS.get(dimensionId);
        if (key == null) {
            ensureDimension(dimensionId);
            key = ACTIVE_KEYS.get(dimensionId);
        }
        if (key == null) {
            InfinityDimensions.LOGGER.warn("Dimension '{}' could not be created.", dimensionId);
            return;
        }

        ServerLevel target = server.getLevel(key);
        if (target == null) {
            InfinityDimensions.LOGGER.warn("ServerLevel for '{}' is null — creation may be pending.", dimensionId);
            return;
        }

        // Use the chunk generator to find the actual surface Y at (8, 8) so players never
        // spawn floating in air (the DerivedLevelData spawn position is copied from the
        // overworld and is meaningless for our custom dimensions).
        int spawnX = 8, spawnZ = 8;
        ChunkGenerator gen = target.getChunkSource().getGenerator();
        int surfaceY = gen.getBaseHeight(spawnX, spawnZ, Heightmap.Types.MOTION_BLOCKING,
                target, target.getChunkSource().randomState());
        // Clamp to a sane range; for void dimensions surfaceY ≈ minY so add 1 for safety.
        double spawnY = Math.max(target.getMinY() + 1, surfaceY);

        player.teleportTo(target, spawnX + 0.5, spawnY, spawnZ + 0.5,
                Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
    }

    // ── Internal: ensure dimension ────────────────────────────────────────────

    public static @Nullable ResourceKey<Level> getKey(String id) {
        return ACTIVE_KEYS.get(id);
    }

    public static void ensureDimension(String id) {
        if (ACTIVE_KEYS.containsKey(id)) return;

        if (NamedDimension.isNamed(id)) {
            ACTIVE_KEYS.put(id, worldKey(id));
        } else {
            ensureProceduralDimension(id);
        }
    }

    private static void ensureProceduralDimension(String hexId) {
        if (ACTIVE_KEYS.containsKey(hexId) || server == null) return;

        ResourceKey<Level> key = worldKey(hexId);
        if (server.getLevel(key) != null) {
            ACTIVE_KEYS.put(hexId, key);
            return;
        }

        // Pre-register the key so re-entrant calls won't queue a second creation
        // while the first is still pending in PENDING_CREATIONS.
        ACTIVE_KEYS.put(hexId, key);

        long seed = hexToSeed(hexId);
        ChunkGenerator generator = new InfinityChunkGenerator(
                new InfinityBiomeSource(server.registryAccess(), seed),
                seed
        );

        // Enqueue — actual levels.put() happens at END_SERVER_TICK, safely after tickChildren().
        PENDING_CREATIONS.offer(new PendingCreation(hexId, key, generator));
    }

    // ── Internal: ServerLevel creation ───────────────────────────────────────

    @Nullable
    private static ServerLevel createServerLevel(ResourceKey<Level> key, ChunkGenerator generator) {
        try {
            MinecraftServerAccessor accessor = (MinecraftServerAccessor) server;

            LevelStem stem = new LevelStem(
                    server.registryAccess().lookupOrThrow(Registries.DIMENSION_TYPE)
                            .getOrThrow(BuiltinDimensionTypes.OVERWORLD),
                    generator
            );

            DerivedLevelData derivedData = new DerivedLevelData(
                    accessor.getWorldData(), accessor.getWorldData().overworldData());

            ServerLevel world = new ServerLevel(
                    server,
                    accessor.getWorkerExecutor(),
                    accessor.getStorageSource(),
                    derivedData,
                    key,
                    stem,
                    false,
                    hexToSeed(key.identifier().getPath()),
                    ImmutableList.of(),
                    false
            );

            accessor.getLevels().put(key, world);
            return world;
        } catch (Exception e) {
            InfinityDimensions.LOGGER.error("Failed to create infinity dimension '{}'", key, e);
            return null;
        }
    }

    // ── Internal: portal frame conversion ─────────────────────────────────────

    private static void convertPortalFrame(ServerLevel world, BlockPos origin, String dimensionId) {
        List<BlockPos> frame = new ArrayList<>();
        collectPortalBlocks(world, origin, frame, new HashSet<>(), 64);

        Direction.Axis axis = world.getBlockState(origin)
                .getOptionalValue(net.minecraft.world.level.block.NetherPortalBlock.AXIS)
                .orElse(Direction.Axis.X);

        BlockState portalState = ModBlocks.INFINITY_PORTAL.defaultBlockState()
                .setValue(InfinityPortalBlock.AXIS, axis)
                .setValue(InfinityPortalBlock.COLOR, InfinityPortalBlock.colorIndexForId(dimensionId));

        for (BlockPos pos : frame) {
            world.setBlock(pos, portalState, 3);
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof InfinityPortalBlockEntity portal) {
                portal.setDimensionId(dimensionId);
            }
        }
    }

    private static void collectPortalBlocks(ServerLevel world, BlockPos pos,
                                             List<BlockPos> result,
                                             HashSet<BlockPos> visited, int limit) {
        if (visited.contains(pos) || result.size() >= limit) return;
        visited.add(pos);
        if (!world.getBlockState(pos).is(Blocks.NETHER_PORTAL)) return;
        result.add(pos.immutable());
        for (Direction dir : Direction.values()) {
            collectPortalBlocks(world, pos.relative(dir), result, visited, limit);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static ResourceKey<Level> worldKey(String id) {
        return ResourceKey.create(Registries.DIMENSION,
                Identifier.fromNamespaceAndPath(InfinityDimensions.MOD_ID, id));
    }

    private static long hexToSeed(String hex) {
        try {
            if (hex.length() >= 16) return Long.parseUnsignedLong(hex.substring(0, 16), 16);
        } catch (NumberFormatException ignored) {}
        return hex.hashCode();
    }
}
