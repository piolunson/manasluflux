package com.manasluflux.addon.mixin;

import com.manasluflux.addon.modules.WoolReplace;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Swaps to wool around a block interaction so the placement vanilla builds (and sends) is a wool
 * placement while your obsidian stays where it is in the hotbar.
 *
 * <p>It has to happen here rather than on the packet: the item is not part of the placement packet,
 * the server reads it from your inventory, and swapping after the packet was built would let the
 * client predict obsidian where the server places wool - a ghost block.
 */
@Mixin(ClientPlayerInteractionManager.class)
public class WoolReplaceMixin {
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void mf$swapToWool(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                               CallbackInfoReturnable<ActionResult> cir) {
        WoolReplace module = Modules.get().get(WoolReplace.class);
        if (module != null) module.beforePlacement(hand);
    }

    @Inject(method = "interactBlock", at = @At("RETURN"))
    private void mf$restoreAfterPlacement(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
                                          CallbackInfoReturnable<ActionResult> cir) {
        WoolReplace module = Modules.get().get(WoolReplace.class);
        if (module != null) module.afterPlacement();
    }
}
