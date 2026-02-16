package com.slabbed.mixin;

import com.slabbed.client.ClientDy;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockView.class)
public interface BlockViewRaycastMixin {

    @Inject(method = "raycastBlock", at = @At("HEAD"), cancellable = true)
    private void slabbed$raycastBlockAdjusted(Vec3d start, Vec3d end, BlockPos pos, VoxelShape shape, BlockState state,
                                              CallbackInfoReturnable<BlockHitResult> cir) {
        BlockPos originalPos = pos.toImmutable();
        BlockView world = (BlockView) (Object) this;
        float dy = ClientDy.dyFor(world, originalPos, state);
        if (dy != -0.5f) {
            return;
        }
        BlockPos adjustedPos = originalPos.down();
        VoxelShape rayShape = state.getRaycastShape(world, originalPos).offset(0.0D, 0.5D, 0.0D);
        BlockHitResult hit = rayShape.raycast(start, end, adjustedPos);
        if (hit == null) {
            return;
        }
        hit = new BlockHitResult(hit.getPos(), hit.getSide(), originalPos, hit.isInsideBlock());
        cir.setReturnValue(hit);
    }
}
