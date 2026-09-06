package com.mousebeast.threehourtour.core;

import com.mousebeast.threehourtour.ThreeHourTour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Optional;

/**
 * Removes the Physics Assembler once its work is done.
 *
 * `simulated` allows a placed assembler to DISASSEMBLE the sub-level it built,
 * and with Primary Disassembly on that assembler is the only thing that can.
 * Leaving it aboard would leave a self-destruct button on the deck.
 *
 * The target block is a parameter so this is testable without the `simulated`
 * mod on the classpath - the dev GameTest server does not have it.
 */
public final class AssemblerConsumer {
    private AssemblerConsumer() {}

    /** Comfortably larger than a starting raft, small enough to be cheap. */
    public static final int SCAN_RADIUS = 24;

    public static final ResourceLocation PHYSICS_ASSEMBLER =
            ResourceLocation.fromNamespaceAndPath("simulated", "physics_assembler");

    /** @return how many blocks were removed */
    public static int consume(ServerLevel level, BlockPos centre, Block target) {
        if (level == null || centre == null || target == null) return 0;

        int removed = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dy = -SCAN_RADIUS; dy <= SCAN_RADIUS; dy++) {
                for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                    cursor.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    if (level.getBlockState(cursor).is(target)) {
                        // No drops: the assembler is consumed, not harvested.
                        level.setBlock(cursor.immutable(), Blocks.AIR.defaultBlockState(), 3);
                        removed++;
                    }
                }
            }
        }
        return removed;
    }

    /**
     * @return the number of assemblers removed, or -1 when `simulated` is not
     *         installed (the dev GameTest server, for instance)
     */
    public static int consumeAssemblers(ServerLevel level, BlockPos centre) {
        Optional<Block> assembler = BuiltInRegistries.BLOCK.getOptional(PHYSICS_ASSEMBLER);
        if (assembler.isEmpty()) {
            ThreeHourTour.LOG.debug("simulated:physics_assembler is not registered; nothing to consume");
            return -1;
        }
        return consume(level, centre, assembler.get());
    }
}
