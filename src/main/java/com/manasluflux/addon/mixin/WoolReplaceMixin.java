package com.manasluflux.addon.mixin;

import com.manasluflux.addon.modules.WoolReplace;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rewrites an obsidian placement on its way out: the held slot is spoofed to wool for the server
 * just long enough for the placement packet to be handled, then put back.
 *
 * <p>Hook point is the connection, not {@code interactBlock}, because the item is not part of the
 * placement packet - the server reads it from the inventory, so the packets either side of it are
 * what decide which block gets placed. The local inventory is deliberately untouched, which is what
 * makes the placed block on the server differ from the one your client shows.
 */
@Mixin(ClientConnection.class)
public class WoolReplaceMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void mf$woolBeforeSend(Packet<?> packet, CallbackInfo ci) {
        WoolReplace module = Modules.get().get(WoolReplace.class);
        if (module != null) module.beforeSend(packet);
    }

    @Inject(method = "send", at = @At("RETURN"))
    private void mf$woolAfterSend(Packet<?> packet, CallbackInfo ci) {
        WoolReplace module = Modules.get().get(WoolReplace.class);
        if (module != null) module.afterSend(packet);
    }
}
