package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.LanternBlock;
import net.minecraft.block.SideShapeType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMP debug logging for lantern hanging support under slabs.
 */
@Mixin(LanternBlock.class)
public abstract class LanternBlockDebugMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 100;

    private static boolean slabbed$shouldLog() {
        return slabbed$logCount++ < slabbed$logLimit;
    }

    private static void slabbed$log(String hook, WorldView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!state.contains(LanternBlock.HANGING) || !state.get(LanternBlock.HANGING)) return;
        BlockPos supportPos = pos.up();
        BlockState support = world.getBlockState(supportPos);
        if (!SlabSupport.isCeilingSupportSurface(support)) return;
        boolean sideSolid = support.isSideSolid(world, supportPos, Direction.DOWN, SideShapeType.CENTER);
        boolean fullSquare = support.isSideSolidFullSquare(world, supportPos, Direction.DOWN);
        boolean covers = Block.sideCoversSmallSquare(world, supportPos, Direction.DOWN);
        Slabbed.LOGGER.info("[SLABBED][debug][Lantern:{}] pos={} support={} slabType={} sideSolidDownCenter={} fullSquareDown={} coversSmallSquareDown={}",
                hook,
                pos.toShortString(),
                support.getBlock(),
                support.contains(net.minecraft.block.SlabBlock.TYPE) ? support.get(net.minecraft.block.SlabBlock.TYPE) : null,
                sideSolid,
                fullSquare,
                covers);
    }

    @Inject(method = "canPlaceAt", at = @At("HEAD"))
    private void slabbed$logCanPlace(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$shouldLog()) slabbed$log("canPlaceAt", world, pos);
    }

    @Inject(method = "getStateForNeighborUpdate(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/world/tick/ScheduledTickView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/random/Random;)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"))
    private void slabbed$logNeighbor(BlockState state, WorldView world, ScheduledTickView scheduledTickView, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, Random random,
                                     CallbackInfoReturnable<BlockState> cir) {
        if (direction == Direction.UP && slabbed$shouldLog()) {
            slabbed$log("getStateForNeighborUpdate", world, pos);
        }
    }
}
