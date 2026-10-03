package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.Vec3;

/**
 * Fly while riding a boat. Jump = up, sneak = down.
 */
public class BoatFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Horizontal speed in blocks per tick.")
        .defaultValue(0.6)
        .range(0.1, 5.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Up/down speed in blocks per tick.")
        .defaultValue(0.4)
        .range(0.1, 3.0)
        .build()
    );

    public BoatFlight() {
        super(AddonTemplate.CATEGORY, "boat-flight", "Fly while riding a boat - horizontal and vertical speed settings, jump = up, sneak = down.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && mc.player.isPassenger()) {
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof AbstractBoat) vehicle.setNoGravity(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isPassenger()) return;

        Entity vehicle = mc.player.getVehicle();
        if (!(vehicle instanceof AbstractBoat)) return;

        vehicle.setNoGravity(true);

        double h = horizontalSpeed.get();
        double v = verticalSpeed.get();
        double yaw = Math.toRadians(mc.player.getYRot());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double lx = Math.cos(yaw), lz = -Math.sin(yaw);

        double vx = 0, vz = 0;
        if (mc.options.keyUp.isDown())    { vx += fx * h; vz += fz * h; }
        if (mc.options.keyDown.isDown())  { vx -= fx * h; vz -= fz * h; }
        if (mc.options.keyLeft.isDown())  { vx += lx * h * 0.7; vz += lz * h * 0.7; }
        if (mc.options.keyRight.isDown()) { vx -= lx * h * 0.7; vz -= lz * h * 0.7; }

        double vy = 0;
        if (mc.options.keyJump.isDown()) vy += v;
        if (mc.options.keyShift.isDown()) vy -= v;

        Vec3 motion = new Vec3(vx, vy, vz);
        vehicle.setDeltaMovement(motion);
        vehicle.lerpMotion(motion);
    }
}
