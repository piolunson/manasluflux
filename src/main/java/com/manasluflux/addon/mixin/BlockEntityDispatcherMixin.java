package com.manasluflux.addon.mixin;

import com.manasluflux.addon.modules.ClientBlockEntities;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityDispatcherMixin {
    @Inject(
        method = "tryExtractRenderState",
        at = @At("HEAD"),
        cancellable = true
    )
    private <E extends BlockEntity> void onTryExtract(BlockEntity blockEntity, float tickDelta, Object crumblingOverlay, boolean shouldRenderOffScreen, CallbackInfoReturnable<Object> cir) {
        if (ClientBlockEntities.shouldSkip(blockEntity.getType())) cir.setReturnValue(null);
    }
}
