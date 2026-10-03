package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoFish extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> castOnEnable = sgGeneral.add(new BoolSetting.Builder()
        .name("cast-on-enable")
        .description("Throws the rod as soon as the module is enabled.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> reelOnCatch = sgGeneral.add(new BoolSetting.Builder()
        .name("reel-on-catch")
        .description("Reels in when a fish bites (bobber hooks something).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lookUp = sgGeneral.add(new BoolSetting.Builder()
        .name("look-up")
        .description("Looks up after reeling in, before casting again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> lookUpPitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("look-up-pitch")
        .description("The pitch to look at when looking up (negative values = up, -90 is straight up).")
        .defaultValue(-60.0)
        .range(-90.0, 90.0)
        .build()
    );

    private final Setting<Boolean> recast = sgGeneral.add(new BoolSetting.Builder()
        .name("recast")
        .description("Casts again automatically after reeling in.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> recastDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("recast-delay")
        .description("Seconds to wait after reeling in before casting again.")
        .defaultValue(4.5)
        .range(0.0, 10.0)
        .build()
    );

    private boolean pendingCast;
    private int castTimer;
    private int settleTicks;

    public AutoFish() {
        super(AddonTemplate.CATEGORY, "auto-fish", "Automatically throws the rod, reels in fish, looks up and recasts.");
    }

    @Override
    public void onActivate() {
        resetState();
        if (castOnEnable.get()) scheduleCast(5);
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    private void resetState() {
        pendingCast = false;
        castTimer = 0;
        settleTicks = 0;
    }

    private void scheduleCast(int delayTicks) {
        pendingCast = true;
        castTimer = Math.max(0, delayTicks);
    }

    private int recastDelayTicks() {
        return (int) Math.round(recastDelay.get() * 20.0);
    }

    private boolean holdingRod() {
        return mc.player != null && mc.player.getMainHandItem().is(Items.FISHING_ROD);
    }

    private void useRod() {
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private void lookUp() {
        mc.player.setXRot(lookUpPitch.get().floatValue());
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null || !holdingRod()) return;

        // Grace window right after a cast so the bobber has time to appear.
        if (settleTicks > 0) {
            settleTicks--;
            return;
        }

        // Waiting to cast (on enable or after a reel-in).
        if (pendingCast) {
            if (castTimer > 0) {
                castTimer--;
                return;
            }
            useRod();
            pendingCast = false;
            settleTicks = 20;
            return;
        }

        FishingHook hook = mc.player.fishing;

        if (hook != null) {
            // A hooked entity means a bite: reel in, look up and schedule the recast.
            if (reelOnCatch.get() && hook.getHookedIn() != null) {
                useRod();
                if (lookUp.get()) lookUp();
                if (recast.get()) scheduleCast(recastDelayTicks());
            }
            return;
        }

        // Bobber is gone without a catch (broke on terrain, despawned, etc.) - recast if wanted.
        if (recast.get()) {
            scheduleCast(recastDelayTicks());
        }
    }
}
