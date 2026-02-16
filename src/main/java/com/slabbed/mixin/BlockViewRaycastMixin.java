package com.slabbed.mixin;

import com.slabbed.client.ClientDy;
import com.slabbed.util.RaycastOffsetContext;
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
        return; // TEMP: disable slabbed raycast intervention for diagnosis
    }
}
