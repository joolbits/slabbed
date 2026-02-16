package com.slabbed.mixin.debug;

import com.slabbed.Slabbed;
import com.slabbed.util.SlabSupport;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;

/**
 * TEMP tracer for BlockState#isFaceSturdy to see who queries slab ceilings.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateFaceSturdyDebugMixin {
    private static int slabbed$logCount = 0;
    private static final int slabbed$logLimit = 30;

    @Inject(method = "isFaceSturdy(Lnet/minecraft/world/BlockView;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)Z",
            at = @At("RETURN"))
    private void slabbed$logFaceSturdy(BlockView world, BlockPos pos, Direction direction,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (direction != Direction.DOWN) return;
        if (slabbed$logCount++ >= slabbed$logLimit) return;
        BlockState state = world.getBlockState(pos);
        if (!SlabSupport.isSupportingSlab(state)) return;
        boolean result = cir.getReturnValue();
        if (result) return; // log only failures
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        StackTraceElement[] snippet = Arrays.copyOfRange(trace, Math.min(4, trace.length), Math.min(10, trace.length));
        Slabbed.LOGGER.info("[SLABBED][debug][isFaceSturdy] pos={} slabType={} dir=DOWN result={} stack={}",
                pos.toShortString(),
                state.contains(net.minecraft.block.SlabBlock.TYPE) ? state.get(net.minecraft.block.SlabBlock.TYPE) : null,
                result,
                Arrays.toString(snippet));
    }
}
