package com.slabbed.mixin;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.SideShapeType;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes ceiling attachment support for top slabs by mutating the pre-computed
 * {@code solidSides[]} cache in {@code ShapeCache} at construction time.
 *
 * <p>Without this, {@code isSideSolid(DOWN, CENTER)} returns {@code false} for
 * top slabs because the collision shape ({@code [0,0.5,0 → 1,1,1]}) does not
 * cover the DOWN face at y=0. The cache is built once per {@code BlockState}
 * and all callers read from it, so injecting into the constructor is the only
 * reliable fix.
 */
@Mixin(targets = "net.minecraft.block.AbstractBlock$AbstractBlockState$ShapeCache")
public class SlabCeilingSupportCacheMixin {

    private static boolean slabbed$debugLogged = false;

    @Shadow
    private boolean[] solidSides;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void slabbed$fixCeilingSolidSides(BlockState state, CallbackInfo ci) {
        if (SlabSupport.isCeilingSupportSurface(state)) {
            // Index formula: direction.ordinal() * SideShapeType.values().length + shapeType.ordinal()
            // Direction.DOWN = 0, SideShapeType.CENTER = 1, SideShapeType count = 3
            // → index = 0 * 3 + 1 = 1
            int index = Direction.DOWN.ordinal() * SideShapeType.values().length
                        + SideShapeType.CENTER.ordinal();
            boolean before = this.solidSides[index];
            this.solidSides[index] = true;

            if (!slabbed$debugLogged) {
                slabbed$debugLogged = true;
                SlabType type = state.contains(SlabBlock.TYPE) ? state.get(SlabBlock.TYPE) : null;
                System.out.println("[SLABBED][ShapeCache ctor] block=" + state.getBlock()
                        + " slabType=" + type
                        + " solidSides.length=" + (solidSides == null ? -1 : solidSides.length)
                        + " dirDownOrd=" + Direction.DOWN.ordinal()
                        + " centerOrd=" + SideShapeType.CENTER.ordinal()
                        + " index=" + index
                        + " before=" + before
                        + " after=" + this.solidSides[index]);
            }
        }
    }
}
