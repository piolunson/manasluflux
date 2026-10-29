package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * Port of RyanWare's ClickTP (SmilerRyan): hold use while looking at a block and you teleport
 * there in configurable steps. Optional safe-teleport check (solid ground, air at the target).
 * Rewritten for MC 26.2.
 */
public class ClickTp extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTeleport = settings.createGroup("Teleport");

    private final Setting<Double> maxDistance = sgTeleport.add(new DoubleSetting.Builder()
        .name("max-distance")
        .description("The maximum distance you can teleport.")
        .defaultValue(100)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> stepSize = sgTeleport.add(new DoubleSetting.Builder()
        .name("step-size")
        .description("Distance to move per step when teleporting.")
        .defaultValue(7)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> safeTeleport = sgTeleport.add(new BoolSetting.Builder()
        .name("safe-teleport")
        .description("Only teleport to positions with solid ground and air to stand in.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> teleportDelay = sgTeleport.add(new IntSetting.Builder()
        .name("teleport-delay")
        .description("Delay in ticks after a finished teleport before you can start the next one.")
        .defaultValue(20)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private Vec3d targetPos;
    private int teleportTimer;

    public ClickTp() {
        super(AddonTemplate.BYPASS_CATEGORY, "mf-click-tp", "Teleports you to the block you are looking at when you press use.");
    }

    @Override
    public void onActivate() {
        targetPos = null;
        teleportTimer = 0;
    }

    @Override
    public void onDeactivate() {
        targetPos = null;
        teleportTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (teleportTimer > 0) {
            teleportTimer--;
            return;
        }

        if (mc.options.useKey.isPressed()) handleTeleport();

        if (targetPos != null) stepTeleport();
    }

    private void handleTeleport() {
        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos blockPos = blockHit.getBlockPos();
        Vec3d hitPos = hit.getPos();

        Vec3d target = new Vec3d(hitPos.x, blockPos.getY() + 1, hitPos.z);

        if (safeTeleport.get() && !isSafePosition(target)) {
            error("Target position is not safe!");
            return;
        }

        double distance = Math.sqrt(
            squared(target.x - mc.player.getX())
                + squared(target.y - mc.player.getY())
                + squared(target.z - mc.player.getZ())
        );

        if (distance > maxDistance.get()) {
            error("Target is too far away!");
            return;
        }

        targetPos = target;
        stepTeleport();
    }

    private void stepTeleport() {
        if (targetPos == null) return;

        Vec3d playerPos = mc.player.getEntityPos();
        double distance = Math.sqrt(
            squared(targetPos.x - playerPos.x)
                + squared(targetPos.y - playerPos.y)
                + squared(targetPos.z - playerPos.z)
        );

        if (distance <= 0.1) {
            targetPos = null;
            teleportTimer = teleportDelay.get();
            return;
        }

        double scale = Math.min(stepSize.get(), distance) / distance;
        mc.player.setPos(
            playerPos.x + (targetPos.x - playerPos.x) * scale,
            playerPos.y + (targetPos.y - playerPos.y) * scale,
            playerPos.z + (targetPos.z - playerPos.z) * scale
        );

        if (distance <= stepSize.get()) {
            targetPos = null;
            teleportTimer = teleportDelay.get();
        } else {
            teleportTimer = 1;
        }
    }

    private boolean isSafePosition(Vec3d pos) {
        BlockPos blockPos = BlockPos.ofFloored(pos);
        BlockState below = mc.world.getBlockState(blockPos.down());
        BlockState at = mc.world.getBlockState(blockPos);
        BlockState above = mc.world.getBlockState(blockPos.up());

        return below.isSideSolidFullSquare(mc.world, blockPos.down(), Direction.UP) && at.isAir() && above.isAir();
    }

    private static double squared(double d) {
        return d * d;
    }
}
