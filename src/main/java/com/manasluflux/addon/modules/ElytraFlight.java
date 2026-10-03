package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

/**
 * Controlled elytra flight - constant thrust in your look direction with
 * jump/sneak as up/down. No fireworks needed.
 */
public class ElytraFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Flight speed in blocks per tick along your look direction.")
        .defaultValue(1.0)
        .range(0.1, 5.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Maximum up/down speed in blocks per tick when holding jump/sneak.")
        .defaultValue(0.75)
        .range(0.1, 3.0)
        .build()
    );

    public ElytraFlight() {
        super(AddonTemplate.CATEGORY, "elytra-flight", "Controlled elytra flight without fireworks - horizontal and vertical speed settings, jump = up, sneak = down.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isFallFlying()) return;

        double h = horizontalSpeed.get();
        Vec3 look = mc.player.getViewVector(1.0f);

        double vy = look.y * h;
        if (mc.options.keyJump.isDown()) vy = Math.max(vy, verticalSpeed.get());
        if (mc.options.keyShift.isDown()) vy = Math.min(vy, -verticalSpeed.get());

        mc.player.setDeltaMovement(new Vec3(look.x * h, vy, look.z * h));
    }
}
