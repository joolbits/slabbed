package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * TEMP debug logging for ceiling hanging sign placement/survival under slabs.
 */
@Mixin(HangingSignBlock.class)
public abstract class CeilingHangingSignDebugMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 100;

    private static boolean slabbed$shouldLog() {
        return slabbed$logCount++ < slabbed$logLimit;
    }

    private static void slabbed$log(String hook, WorldView world, BlockPos pos, Boolean result) {
        BlockPos above = pos.up();
        BlockState aboveState = world.getBlockState(above);
        if (!SlabSupport.isCeilingSupportSurface(aboveState)) {
            return;
        }
        boolean sideSolid = aboveState.isSideSolid(world, above, Direction.DOWN, SideShapeType.CENTER);
        boolean fullSquare = aboveState.isSideSolidFullSquare(world, above, Direction.DOWN);
        boolean covers = Block.sideCoversSmallSquare(world, above, Direction.DOWN);
        Slabbed.LOGGER.info("[SLABBED][debug][HangingSign:{}] pos={} abovePos={} aboveState={} slabType={} sideSolidDownCenter={} fullSquareDown={} coversSmallSquareDown={} result={}",
                hook,
                pos.toShortString(),
                above.toShortString(),
                aboveState.getBlock(),
                aboveState.contains(net.minecraft.block.SlabBlock.TYPE) ? aboveState.get(net.minecraft.block.SlabBlock.TYPE) : null,
                sideSolid,
                fullSquare,
                covers,
                result);
    }

    @Inject(method = "canPlaceAt", at = @At("HEAD"))
    private void slabbed$logCanPlace(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$shouldLog()) {
            slabbed$log("canPlaceAt", world, pos, null);
        }
    }

    @Inject(method = "canPlaceAt", at = @At("RETURN"))
    private void slabbed$logCanPlaceResult(BlockState state, WorldView world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$shouldLog()) {
            slabbed$log("canPlaceAt:return", world, pos, cir.getReturnValue());
        }
    }

    @Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = false, locals = LocalCapture.CAPTURE_FAILEXCEPTION)
    private void slabbed$logPlacementState(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (ctx == null || ctx.getWorld() == null) {
            return;
        }
        if (slabbed$shouldLog()) {
            slabbed$log("getPlacementState", ctx.getWorld(), ctx.getBlockPos(), null);
        }
    }

    @Inject(method = "getPlacementState", at = @At("RETURN"))
    private void slabbed$logPlacementStateReturn(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (ctx == null || ctx.getWorld() == null) {
            return;
        }
        if (!slabbed$shouldLog()) {
            return;
        }
        BlockState result = cir.getReturnValue();
        BlockPos pos = ctx.getBlockPos();
        BlockState aboveState = ctx.getWorld().getBlockState(pos.up());
        if (!SlabSupport.isCeilingSupportSurface(aboveState)) {
            return;
        }
        String kind = "null";
        if (result != null) {
            if (result.getBlock() instanceof WallHangingSignBlock) {
                kind = "wall";
            } else if (result.getBlock() instanceof HangingSignBlock) {
                kind = "ceiling";
            } else {
                kind = "other";
            }
        }
        Slabbed.LOGGER.info("[SLABBED][debug][HangingSign:getPlacementState:return] pos={} result={} kind={} props={}",
                pos.toShortString(),
                result == null ? null : result.getBlock(),
                kind,
                result == null ? null : result.getEntries());
    }

    @Inject(method = "getStateForNeighborUpdate(Lnet/minecraft/block/BlockState;Lnet/minecraft/world/WorldView;Lnet/minecraft/world/tick/ScheduledTickView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/random/Random;)Lnet/minecraft/block/BlockState;",
            at = @At("HEAD"))
    private void slabbed$logNeighbor(BlockState state, WorldView world, ScheduledTickView scheduledTickView,
                                     BlockPos pos, Direction direction, BlockPos neighborPos, BlockState neighborState,
                                     Random random, CallbackInfoReturnable<BlockState> cir) {
        if (direction == Direction.UP && slabbed$shouldLog()) {
            slabbed$log("getStateForNeighborUpdate", world, pos, null);
        }
    }
}
