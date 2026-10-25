package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

/**
 * Controlled elytra flight - thrust in your look direction with jump/sneak as
 * up/down. No fireworks needed. Auto-forward can be toggled: on = constant
 * thrust, off = thrust only while holding the forward key.
 */
public class ElytraFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoForward = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-forward")
        .description("Constantly thrust forward in your look direction. When off, you only thrust while holding the forward key - jump = up and sneak = down still work anytime.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Flight speed in blocks per tick along your look direction.")
        .defaultValue(1.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Maximum up/down speed in blocks per tick when holding jump/sneak.")
        .defaultValue(0.75)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    public ElytraFlight() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "elytra-flight", "Controlled elytra flight without fireworks - horizontal and vertical speed settings, jump = up, sneak = down. Toggle auto-forward off to steer yourself with the forward key.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isGliding()) return;

        boolean thrust = autoForward.get() || mc.options.forwardKey.isPressed();

        double h = horizontalSpeed.get();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d cur = mc.player.getVelocity();

        double vx = thrust ? look.x * h : cur.x;
        double vz = thrust ? look.z * h : cur.z;

        double vy = thrust ? look.y * h : cur.y;
        if (mc.options.jumpKey.isPressed()) vy = Math.max(vy, verticalSpeed.get());
        if (mc.options.sneakKey.isPressed()) vy = Math.min(vy, -verticalSpeed.get());

        mc.player.setVelocity(new Vec3d(vx, vy, vz));
    }
}
