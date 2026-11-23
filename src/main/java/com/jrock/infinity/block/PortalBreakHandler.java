package com.jrock.infinity.block;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes Infinity Portals behave like a single object when they are broken.
 *
 * <p>Before: breaking one portal block removed only that block, leaving a ragged, still-working portal.
 * <br>Now:
 * <ul>
 *   <li>Breaking <b>any portal block</b> removes the <b>entire connected portal plane</b> at once.</li>
 *   <li>Breaking a <b>frame block</b> (obsidian) next to a portal also collapses that portal,
 *       the same way a vanilla Nether Portal does.</li>
 * </ul>
 *
 * <p>The plane is found with a flood fill that only crosses the portal's own plane (up/down and along
 * its axis), never through its thin side, so two separate portals that merely touch are not merged.
 */
public final class PortalBreakHandler {

    /** Vanilla portals max out at 21x21 = 441 interior blocks; leave generous headroom. */
    private static final int MAX_BLOCKS = 1024;

    private PortalBreakHandler() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel server)) return;

            List<BlockPos> seeds = new ArrayList<>(6);
            if (state.is(ModBlocks.INFINITY_PORTAL)) {
                // The broken block is already gone: seed from its in-plane neighbours.
                Direction.Axis axis = state.getValue(InfinityPortalBlock.AXIS);
                for (Direction d : Direction.values()) {
                    if (inPlane(d, axis)) seeds.add(pos.relative(d).immutable());
                }
            } else {
                // A non-portal block was broken. If it was part of a portal's frame, collapse the portal.
                for (Direction d : Direction.values()) {
                    BlockPos neighbour = pos.relative(d).immutable();
                    BlockState ns = server.getBlockState(neighbour);
                    // Only counts as a frame block when it sits IN the portal's plane, not in front of / behind it.
                    if (ns.is(ModBlocks.INFINITY_PORTAL)
                            && inPlane(d.getOpposite(), ns.getValue(InfinityPortalBlock.AXIS))) {
                        seeds.add(neighbour);
                    }
                }
            }
            if (!seeds.isEmpty()) removeConnected(server, pos, seeds);
        });
    }

    /** True if moving in direction {@code d} stays inside a portal plane that extends along {@code axis}. */
    private static boolean inPlane(Direction d, Direction.Axis axis) {
        return d.getAxis() == Direction.Axis.Y || d.getAxis() == axis;
    }

    /** Removes every Infinity Portal block connected (within its plane) to any of the seeds. */
    private static void removeConnected(ServerLevel level, BlockPos origin, List<BlockPos> seeds) {
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> found = new ArrayList<>();

        for (BlockPos s : seeds) {
            if (level.getBlockState(s).is(ModBlocks.INFINITY_PORTAL) && seen.add(s)) queue.add(s);
        }

        while (!queue.isEmpty() && found.size() < MAX_BLOCKS) {
            BlockPos p = queue.poll();
            found.add(p);
            Direction.Axis axis = level.getBlockState(p).getValue(InfinityPortalBlock.AXIS);
            for (Direction d : Direction.values()) {
                if (!inPlane(d, axis)) continue;
                BlockPos n = p.relative(d).immutable();
                if (seen.add(n) && level.getBlockState(n).is(ModBlocks.INFINITY_PORTAL)) queue.add(n);
            }
        }

        if (found.isEmpty()) return;

        for (BlockPos p : found) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0.05);
        }
        level.playSound(null, origin, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.0f, 0.7f);
    }
}
