package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.CarpetBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * Single source of truth for client-side dy offsets (model, outline, raycast).
 */
public final class ClientDy {
    private ClientDy() {
    }

    public static float dyFor(BlockView world, BlockPos pos, BlockState state) {
        if (world == null || pos == null || state == null) {
            return 0.0f;
        }
        float dy = (float) SlabSupport.getYOffset(world, pos, state);
        // Carpet special-case: SlabSupport skips thin layers, but carpets on bottom slabs must visually drop.
        if (dy == 0.0f && state.getBlock() instanceof CarpetBlock && SlabSupport.hasBottomSlabBelow(world, pos)) {
            dy = -0.5f;
        }
        return dy;
    }
}
