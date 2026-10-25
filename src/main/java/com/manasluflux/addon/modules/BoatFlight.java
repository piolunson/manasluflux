package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;

/**
 * Fly while riding a boat. Jump = up, sneak = down.
 */
public class BoatFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal speed in blocks per tick.")
        .defaultValue(0.6)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Up/down speed in blocks per tick.")
        .defaultValue(0.4)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    public BoatFlight() {
        super(AddonTemplate.CATEGORY, "boat-flight", "Fly while riding a boat - horizontal and vertical speed settings, jump = up, sneak = down.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.player.hasVehicle()) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof AbstractBoatEntity) vehicle.setNoGravity(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.hasVehicle()) return;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof AbstractBoatEntity)) return;

        vehicle.setNoGravity(true);

        double h = horizontalSpeed.get();
        double v = verticalSpeed.get();
        double yaw = Math.toRadians(mc.player.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = -Math.sin(yaw);

        double vx = 0, vz = 0;
        if (mc.options.forwardKey.isPressed())    { vx += fx * h; vz += fz * h; }
        if (mc.options.backKey.isPressed())  { vx -= fx * h; vz -= fz * h; }
        if (mc.options.leftKey.isPressed())  { vx += lx * h * 0.7; vz += lz * h * 0.7; }
        if (mc.options.rightKey.isPressed()) { vx -= lx * h * 0.7; vz -= lz * h * 0.7; }

        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy += v;
        if (mc.options.sneakKey.isPressed()) vy -= v;

        Vec3d motion = new Vec3d(vx, vy, vz);
        vehicle.setVelocity(motion);
        vehicle.setVelocityClient(motion);
    }
}
