package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow private ClientWorld world;

    @Unique
    private boolean slabbed$outlineTranslated;

    @Inject(
            method = "drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V",
            at = @At("HEAD")
    )
    private void slabbed$translateOutlineStart(MatrixStack matrices, VertexConsumer vertexConsumer,
                                               double cameraX, double cameraY, double cameraZ,
                                               OutlineRenderState outlineRenderState, int color, float alpha,
                                               CallbackInfo ci) {
        this.slabbed$outlineTranslated = false;
        if (this.world == null || outlineRenderState == null) {
            return;
        }
        BlockPos pos = outlineRenderState.pos();
        float dy = ClientDy.dyFor(this.world, pos, this.world.getBlockState(pos));
        if (dy == 0.0f) {
            return;
        }
        matrices.push();
        matrices.translate(0.0D, dy, 0.0D);
        this.slabbed$outlineTranslated = true;
    }

    @Inject(
            method = "drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V",
            at = @At("RETURN")
    )
    private void slabbed$translateOutlineEnd(MatrixStack matrices, VertexConsumer vertexConsumer,
                                             double cameraX, double cameraY, double cameraZ,
                                             OutlineRenderState outlineRenderState, int color, float alpha,
                                             CallbackInfo ci) {
        if (!this.slabbed$outlineTranslated) {
            return;
        }
        matrices.pop();
        this.slabbed$outlineTranslated = false;
    }
}
