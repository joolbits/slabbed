package com.slabbed.mixin.client;

import com.slabbed.client.ClientDy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.state.OutlineRenderState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixin {

    @Shadow private ClientWorld world;

    @Unique
    private static long SLABBED$LAST_OUTLINE_LOG = 0L;

    @Unique
    private static String slabbed$shapeSummary(ClientWorld w, BlockPos p) {
        var st = w.getBlockState(p);
        VoxelShape outline = st.getOutlineShape(w, p);
        VoxelShape collision = st.getCollisionShape(w, p);
        VoxelShape ray = st.getRaycastShape(w, p);
        return "pos=" + p +
                " state=" + st.getBlock().getTranslationKey() +
                " outlineEmpty=" + outline.isEmpty() +
                " collisionEmpty=" + collision.isEmpty() +
                " rayEmpty=" + ray.isEmpty() +
                " outlineBoxes=" + outline.getBoundingBoxes().size() +
                " collisionBoxes=" + collision.getBoundingBoxes().size() +
                " rayBoxes=" + ray.getBoundingBoxes().size();
    }

    @Redirect(
            method = "drawBlockOutline(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDLnet/minecraft/client/render/state/OutlineRenderState;IF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/state/OutlineRenderState;shape()Lnet/minecraft/util/shape/VoxelShape;")
    )
    private VoxelShape slabbed$offsetOutlineShape(OutlineRenderState outlineRenderState) {
        VoxelShape shape = outlineRenderState.shape();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) {
            return shape;
        }
        ClientWorld clientWorld = mc.world;
        HitResult hit = mc.crosshairTarget;
        if (clientWorld == null || !(hit instanceof BlockHitResult blockHit)) {
            return shape;
        }
        BlockPos pos = blockHit.getBlockPos();
        long now = System.currentTimeMillis();
        if (now - SLABBED$LAST_OUTLINE_LOG > 750L) {
            SLABBED$LAST_OUTLINE_LOG = now;

            String b0 = slabbed$shapeSummary(clientWorld, pos);
            String bU = slabbed$shapeSummary(clientWorld, pos.up());
            String bD = slabbed$shapeSummary(clientWorld, pos.down());

            double dy0 = ClientDy.dyFor(clientWorld, pos, clientWorld.getBlockState(pos));
            double dyU = ClientDy.dyFor(clientWorld, pos.up(), clientWorld.getBlockState(pos.up()));

            com.slabbed.Slabbed.LOGGER.info(
                    "[Slabbed][SHAPES] dy(pos)={} dy(pos.up)={} | {} | UP {} | DOWN {}",
                    dy0, dyU, b0, bU, bD
            );
        }
        double dy = ClientDy.dyFor(clientWorld, pos, clientWorld.getBlockState(pos));
        return dy != 0.0D ? shape.offset(0.0D, dy, 0.0D) : shape;
    }
}
