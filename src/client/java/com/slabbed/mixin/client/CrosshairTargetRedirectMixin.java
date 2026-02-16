package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Post-processes crosshair hit results to redirect overlap-zone slab hits
 * to the visually offset block above when appropriate.
 */
@Mixin(GameRenderer.class)
public abstract class CrosshairTargetRedirectMixin {

    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "updateCrosshairTarget", at = @At("TAIL"), require = 1)
    private void slabbed$redirectSlabTopHit(float tickDelta, CallbackInfo ci) {
        if (this.client == null || this.client.world == null) return;

        HitResult hit = this.client.crosshairTarget;
        if (!(hit instanceof BlockHitResult bhr)) return;

        // Only when aiming at the TOP face of a bottom slab.
        if (bhr.getSide() != Direction.UP) return;

        BlockPos slabPos = bhr.getBlockPos();
        BlockState slabState = this.client.world.getBlockState(slabPos);
        if (!(slabState.getBlock() instanceof SlabBlock)) return;

        // Only in the upper half of the slab block space (the overlap band).
        double localY = bhr.getPos().y - slabPos.getY();
        if (localY < 0.5D || localY >= 1.0D) return;

        BlockPos abovePos = slabPos.up();
        BlockState aboveState = this.client.world.getBlockState(abovePos);
        if (aboveState.isAir()) return;

        float dy = ClientDy.dyFor(this.client.world, abovePos, aboveState);

        // Only the bottom-slab lowering case.
        if (dy != -0.5f) return;

        Vec3d newHit = bhr.getPos().add(0.0D, 1.0D + dy, 0.0D);
        this.client.crosshairTarget = new BlockHitResult(
                newHit,
                Direction.DOWN,
                abovePos,
                bhr.isInsideBlock()
        );
    }
}
