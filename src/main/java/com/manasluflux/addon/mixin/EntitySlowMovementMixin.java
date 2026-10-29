package com.manasluflux.addon.mixin;

import com.manasluflux.addon.modules.CobwebBypass;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.11's {@code Entity#slowMovement} does no maths itself: it calls {@code onLanding()} and
 * stores the multiplier, which {@code Entity#move} then multiplies the tick's movement delta by.
 * So replacing the stored value is exactly "the same move, less sticky" - nothing else in the
 * movement pipeline is touched.
 *
 * <p>Only the local player is affected (CobwebBypass checks the entity), mobs and other players
 * keep vanilla behaviour.
 */
@Mixin(Entity.class)
public abstract class EntitySlowMovementMixin {
    @Shadow
    protected Vec3d movementMultiplier;

    @Inject(method = "slowMovement", at = @At("HEAD"), cancellable = true)
    private void mf$softenStuckSpeed(BlockState state, Vec3d multiplier, CallbackInfo ci) {
        Vec3d softened = CobwebBypass.modifyMultiplier((Entity) (Object) this, multiplier);

        // Nothing to change (other entity, module off, or the settings add up to vanilla):
        // let the vanilla body run untouched.
        if (softened == multiplier) return;

        // Same two steps as vanilla, just with our number.
        ((Entity) (Object) this).onLanding();
        this.movementMultiplier = softened;
        ci.cancel();
    }
}
