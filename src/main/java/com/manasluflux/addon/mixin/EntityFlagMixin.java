package com.manasluflux.addon.mixin;

import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@code Entity#setFlag} is protected, but the elytra modules need to clear the gliding flag
 * locally the same way the server does when it rubberbands you.
 */
@Mixin(Entity.class)
public interface EntityFlagMixin {
    @Invoker("setFlag")
    void mf$setFlag(int index, boolean value);
}
