package com.slabbed.debug;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;

/** Helper for debug logging of placement predicates. */
public final class BlockStatePlacementDebugHelper {
    private BlockStatePlacementDebugHelper() {}

    public static boolean sideCovers(ItemPlacementContext ctx) {
        if (ctx == null) return false;
        WorldView world = ctx.getWorld();
        if (world == null) return false;
        BlockPos supportPos = ctx.getBlockPos().up();
        return Block.sideCoversSmallSquare(world, supportPos, Direction.DOWN);
    }

    public static boolean sideSolidFullSquare(ItemPlacementContext ctx) {
        if (ctx == null) return false;
        WorldView world = ctx.getWorld();
        if (world == null) return false;
        BlockPos supportPos = ctx.getBlockPos().up();
        BlockState support = world.getBlockState(supportPos);
        return support.isSideSolidFullSquare(world, supportPos, Direction.DOWN);
    }
}
