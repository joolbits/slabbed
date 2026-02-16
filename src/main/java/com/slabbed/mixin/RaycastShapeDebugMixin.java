package com.slabbed.mixin;

import com.slabbed.Slabbed;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class RaycastShapeDebugMixin {

    // Flip to true ONLY for a one-run diagnostic, then revert to false.
    private static final boolean DIAG_FALLBACK_TO_OUTLINE = true;

    @Inject(method = "getRaycastShape", at = @At("RETURN"), cancellable = true)
    private void slabbed$debugRaycastShape(BlockView world, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape ray = cir.getReturnValue();
        if (world == null || pos == null) return;

        BlockState state = ((BlockState) (Object) this);
        if (state.isAir()) return;

        if (ray != null && ray.isEmpty()) {
            VoxelShape outline = state.getOutlineShape(world, pos);
            VoxelShape collision = state.getCollisionShape(world, pos);

            Slabbed.LOGGER.info(
                    "[Slabbed][RAYCAST_EMPTY] pos={} block={} outlineEmpty={} collisionEmpty={}",
                    pos,
                    state.getBlock().getTranslationKey(),
                    outline.isEmpty(),
                    collision.isEmpty()
            );

            if (DIAG_FALLBACK_TO_OUTLINE && !outline.isEmpty()) {
                cir.setReturnValue(outline);
            }
        }
    }
}
