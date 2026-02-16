package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * TEMP debug: log {@link Block#sideCoversSmallSquare} queries against slabs to map placement predicates.
 */
@Mixin(Block.class)
public abstract class BlockStateSupportLogMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 100;

    @Inject(method = "sideCoversSmallSquare(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("HEAD"))
    private static void slabbed$logSideCover(WorldView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$logCount++ >= slabbed$logLimit) return;
        BlockState state = world.getBlockState(pos);
        if (!SlabSupport.isSupportingSlab(state)) return;
        Slabbed.LOGGER.info("[SLABBED][debug][sideCoversSmallSquare:head] pos={} dir={} block={} slabType={} result?=vanilla", pos.toShortString(), direction, state.getBlock(), state.get(net.minecraft.block.SlabBlock.TYPE));
    }

    @Inject(method = "sideCoversSmallSquare(Lnet/minecraft/world/WorldView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z", at = @At("RETURN"))
    private static void slabbed$logSideCoverReturn(WorldView world, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        if (slabbed$logCount++ >= slabbed$logLimit) return;
        BlockState state = world.getBlockState(pos);
        if (!SlabSupport.isSupportingSlab(state)) return;
        Slabbed.LOGGER.info("[SLABBED][debug][sideCoversSmallSquare:return] pos={} dir={} block={} slabType={} result={}", pos.toShortString(), direction, state.getBlock(), state.get(net.minecraft.block.SlabBlock.TYPE), cir.getReturnValue());
    }
}
