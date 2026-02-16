package com.slabbed.mixin.debug.accessor;

import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemPlacementContext.class)
public interface ItemPlacementContextInvoker {
    @Invoker("getHitResult")
    BlockHitResult slabbed$invokeGetHitResult();
}
