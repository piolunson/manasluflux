package com.manasluflux.addon.mixin;

import com.manasluflux.addon.modules.ClientBlockEntities;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntityRenderManager.class)
public abstract class BlockEntityDispatcherMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <S extends BlockEntityRenderState> void onRender(S renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
        BlockEntityType<?> type = ClientBlockEntities.rendererType(renderState);
        if (type != null && ClientBlockEntities.shouldSkip(type)) ci.cancel();
    }
}
