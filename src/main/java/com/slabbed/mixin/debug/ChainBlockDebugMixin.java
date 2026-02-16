package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.block.SideShapeType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMP debug logging for chain placement/survival under slabs.
 */
@Mixin(ChainBlock.class)
public abstract class ChainBlockDebugMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 100;

    private static boolean slabbed$shouldLog() {
        return slabbed$logCount++ < slabbed$logLimit;
    }

    private static void slabbed$log(String hook, WorldView world, BlockPos pos, Boolean result) {
        BlockPos supportPos = pos.up();
        BlockState support = world.getBlockState(supportPos);
        if (!SlabSupport.isCeilingSupportSurface(support) && !SlabSupport.isBottomSlab(support)) {
            return;
        }
        boolean sideSolid = support.isSideSolid(world, supportPos, Direction.DOWN, SideShapeType.CENTER);
        boolean fullSquare = support.isSideSolidFullSquare(world, supportPos, Direction.DOWN);
        boolean covers = Block.sideCoversSmallSquare(world, supportPos, Direction.DOWN);
        PistonBehavior piston = support.getPistonBehavior();
        Slabbed.LOGGER.info("[SLABBED][debug][Chain:{}] pos={} supportPos={} support={} slabType={} sideSolidDownCenter={} fullSquareDown={} coversSmallSquareDown={} piston={} result={}",
                hook,
                pos.toShortString(),
                supportPos.toShortString(),
                support.getBlock(),
                support.contains(net.minecraft.block.SlabBlock.TYPE) ? support.get(net.minecraft.block.SlabBlock.TYPE) : null,
                sideSolid,
                fullSquare,
                covers,
                piston,
                result);
    }

    @Inject(method = "getPlacementState", at = @At("HEAD"), cancellable = false)
    private void slabbed$logPlacementState(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (ctx == null || ctx.getWorld() == null) return;
        if (!slabbed$shouldLog()) return;
        slabbed$log("getPlacementState", ctx.getWorld(), ctx.getBlockPos(), null);
    }
}
