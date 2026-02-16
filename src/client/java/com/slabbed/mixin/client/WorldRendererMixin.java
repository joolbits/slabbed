package com.slabbed.mixin.client;

import com.slabbed.Slabbed;
import com.slabbed.client.ClientDy;
import net.minecraft.block.CarpetBlock;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.state.WorldRenderState;
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
    private static final ThreadLocal<Boolean> SLABBED$OUTLINE_SHIFTED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "renderTargetBlockOutline", at = @At("HEAD"))
    private void slabbed$applyOutlineOffset(VertexConsumerProvider.Immediate immediate, MatrixStack matrices,
                                            boolean renderBlockOutline, WorldRenderState renderStates, CallbackInfo ci) {
        SLABBED$OUTLINE_SHIFTED.set(Boolean.FALSE);
        if (world == null || renderStates == null || renderStates.outlineRenderState == null) {
            return;
        }
        BlockPos pos = renderStates.outlineRenderState.pos();
        if (pos == null) {
            return;
        }
        var state = world.getBlockState(pos);
        double dy = ClientDy.dyFor(world, pos, state);
        if (state.getBlock() instanceof CarpetBlock) {
            Slabbed.LOGGER.info("[Slabbed] outline carpet dy={} pos={} block={} below={}", dy, pos, state.getBlock(), world.getBlockState(pos.down()).getBlock());
        }
        if (dy != 0.0D) {
            matrices.push();
            matrices.translate(0.0D, dy, 0.0D);
            SLABBED$OUTLINE_SHIFTED.set(Boolean.TRUE);
        }
    }

    @Inject(method = "renderTargetBlockOutline", at = @At("RETURN"))
    private void slabbed$popOutlineOffset(VertexConsumerProvider.Immediate immediate, MatrixStack matrices,
                                          boolean renderBlockOutline, WorldRenderState renderStates, CallbackInfo ci) {
        if (SLABBED$OUTLINE_SHIFTED.get()) {
            matrices.pop();
            SLABBED$OUTLINE_SHIFTED.set(Boolean.FALSE);
        }
    }
}
