package com.slabbed.client;

import com.slabbed.util.SlabSupport;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
        // Carpet special-case: SlabSupport skips thin layers, but carpets on bottom slabs must visually drop.
        if (state.getBlock() instanceof CarpetBlock && SlabSupport.hasBottomSlabBelow(world, pos)) {
            return -0.5f;
        }

        // Allowlisted full blocks that must follow slab dy (avoid ghosting on slab tops).
        boolean allowlistedFullBlock = (state.isOf(Blocks.CRAFTING_TABLE) || state.isOf(Blocks.OAK_TRAPDOOR))
                && SlabSupport.hasBottomSlabBelow(world, pos);
        if (allowlistedFullBlock) {
            return -0.5f;
        }

        double dy = SlabSupport.getYOffset(world, pos, state);
        if (dy < 0.0D) {
            return -0.5f;
        }
        return 0.0f;
    }
}
