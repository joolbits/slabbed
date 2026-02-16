package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import com.slabbed.debug.DebugTraceContext;
import com.slabbed.debug.BlockStatePlacementDebugHelper;
import com.slabbed.mixin.debug.accessor.ItemUsageContextAccessor;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChainBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMP tracer: logs placement attempts for chains and hanging signs via BlockItem.useOnBlock.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemPlacementDebugMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 50;

    private boolean slabbed$isTrackedItem(ItemUsageContext ctx) {
        if (ctx == null) return false;
        ItemStack stack = ctx.getStack();
        if (stack == null) return false;
        if (stack.getItem() instanceof HangingSignItem) return true;
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock) return true;
        return false;
    }

    @Inject(method = "useOnBlock", at = @At("HEAD"))
    private void slabbed$logPlacement(ItemUsageContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!slabbed$isTrackedItem(ctx)) return;
        DebugTraceContext.enter(ctx.getStack());
        if (slabbed$logCount++ >= slabbed$logLimit) return;
        World world = ctx.getWorld();
        if (world == null) return;
        BlockPos pos = ctx.getBlockPos();
        Direction face = ctx.getSide();
        BlockPos supportPos = pos.up();
        BlockState support = world.getBlockState(supportPos);
        boolean isSlab = SlabSupport.isSupportingSlab(support);
        Slabbed.LOGGER.info("[SLABBED][debug][BlockItem.useOnBlock] item={} pos={} face={} supportPos={} support={} slabType={} isSlab={} result?=pending",
                ctx.getStack().getItem(),
                pos.toShortString(),
                face,
                supportPos.toShortString(),
                support.getBlock(),
                support.contains(net.minecraft.block.SlabBlock.TYPE) ? support.get(net.minecraft.block.SlabBlock.TYPE) : null,
                isSlab);

        // Chain-only hit result diagnostics
        if (ctx.getStack().getItem() instanceof BlockItem bi && bi.getBlock() instanceof ChainBlock) {
            BlockHitResult hit = ((ItemUsageContextAccessor) ctx).slabbed$invokeGetHitResult();
            BlockPos hitPos = hit.getBlockPos();
            Direction hitSide = hit.getSide();
            Vec3d hitVec = hit.getPos();
            BlockPos ctxPos = pos;
            Direction ctxSide = face;
            if (ctx instanceof ItemPlacementContext placementCtx) {
                ctxPos = placementCtx.getBlockPos();
                ctxSide = placementCtx.getSide();
            }
            Slabbed.LOGGER.info("[SLABBED][debug][BlockItem.useOnBlock:hit] item={} hitPos={} hitSide={} hitVec={} ctx.blockPos={} ctx.side={}",
                    ctx.getStack().getItem(),
                    hitPos.toShortString(),
                    hitSide,
                    hitVec,
                    ctxPos.toShortString(),
                    ctxSide);
        }
    }

    @Inject(method = "useOnBlock", at = @At("RETURN"))
    private void slabbed$logPlacementReturn(ItemUsageContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!slabbed$isTrackedItem(ctx)) return;
        World world = ctx.getWorld();
        if (world == null) return;
        BlockPos pos = ctx.getBlockPos();
        ActionResult res = cir.getReturnValue();
        Slabbed.LOGGER.info("[SLABBED][debug][BlockItem.useOnBlock:return] item={} pos={} result={}",
                ctx.getStack().getItem(),
                pos.toShortString(),
                res);
        DebugTraceContext.exit();
    }

    // Chain-only: report getPlacementState result
    @Inject(method = "getPlacementState", at = @At("RETURN"))
    private void slabbed$logPlacementState(ItemPlacementContext ctx, CallbackInfoReturnable<BlockState> cir) {
        if (ctx == null) return;
        if (!(ctx.getStack().getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof ChainBlock)) return;
        BlockState state = cir.getReturnValue();
        if (state == null) {
            Slabbed.LOGGER.info("[SLABBED][debug][BlockItem.getPlacementState:return] item={} getPlacementState=null", ctx.getStack().getItem());
            return;
        }
        BlockPos supportPos = ctx.getBlockPos().up();
        BlockState support = ctx.getWorld().getBlockState(supportPos);
        Slabbed.LOGGER.info("[SLABBED][debug][BlockItem.getPlacementState:return] item={} state={} properties={} axis={} sideDownCenter={} coversDown={} fullSquareDown={} supportPos={} support={}",
                ctx.getStack().getItem(),
                state.getBlock(),
                state.getEntries(),
                state.contains(net.minecraft.state.property.Properties.AXIS) ? state.get(net.minecraft.state.property.Properties.AXIS) : null,
                support.isSideSolid(ctx.getWorld(), supportPos, Direction.DOWN, net.minecraft.block.SideShapeType.CENTER),
                BlockStatePlacementDebugHelper.sideCovers(ctx),
                BlockStatePlacementDebugHelper.sideSolidFullSquare(ctx),
                supportPos.toShortString(),
                support.getBlock());
    }
}
