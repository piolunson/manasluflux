package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Elytra bounce along a highway: the jump key is held whenever you are not gliding, so vanilla
 * jumps on the ground and recasts the elytra in the air - bounce after bounce, in a straight line
 * (the yaw is locked to the chosen highway axis).
 *
 * <p>When the bouncing stops - no forward speed for {@code stall-ticks}, or no elytra left - it
 * walks {@code walk-blocks} forward along the same axis, then hands control back to the bounce.
 * Walking is either Baritone (a goal on the same axis, through Meteor's path manager) or this
 * module's own walker: yaw at the target, forward held, sprint, jump only when actually blocked.
 */
public class ElytraBounce extends Module {
    public enum WalkMode {
        Baritone,
        Self
    }

    public enum Axis {
        Auto,
        PosX,
        NegX,
        PosZ,
        NegZ
    }

    private enum Phase {
        Bounce,
        Walk
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<WalkMode> mode = sgGeneral.add(new EnumSetting.Builder<WalkMode>()
        .name("walk-mode")
        .description("Baritone walks to the goal; Self is this module's own walker, made for a straight highway.")
        .defaultValue(WalkMode.Baritone)
        .build()
    );

    private final Setting<Integer> walkBlocks = sgGeneral.add(new IntSetting.Builder()
        .name("walk-blocks")
        .description("How far forward to walk once the bouncing stops.")
        .defaultValue(50)
        .range(1, 2000)
        .sliderRange(10, 500)
        .build()
    );

    private final Setting<Axis> axis = sgGeneral.add(new EnumSetting.Builder<Axis>()
        .name("axis")
        .description("Which way the highway runs. Auto locks to the axis you are facing when the module starts.")
        .defaultValue(Axis.Auto)
        .build()
    );

    private final Setting<Boolean> keepHeading = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-heading")
        .description("Turn you onto the highway axis while bouncing and walking, so a bounce cannot drift off the road.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> stallTicks = sgGeneral.add(new IntSetting.Builder()
        .name("stall-ticks")
        .description("Ticks without forward speed before the bounce counts as stopped and the walk starts.")
        .defaultValue(40)
        .range(5, 200)
        .sliderRange(10, 100)
        .build()
    );

    private Phase phase = Phase.Bounce;
    private int stall;
    private int failedWalks;
    private boolean pathing;
    private boolean pathStarted;

    private boolean alongX;
    private int direction = 1;

    private Vec3d walkStart = Vec3d.ZERO;
    private Vec3d walkLast = Vec3d.ZERO;
    private BlockPos walkTarget;
    private int walkStuck;

    private boolean forwardWasPressed;
    private boolean jumpWasPressed;

    public ElytraBounce() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "mf-elytra-bounce", "Bounces your elytra along a highway, and when the bouncing stops it walks a set distance forward - with Baritone or its own walker.");
    }

    @Override
    public void onActivate() {
        phase = Phase.Bounce;
        stall = 0;
        failedWalks = 0;
        pathing = false;
        pathStarted = false;
        forwardWasPressed = mc.options.forwardKey.isPressed();
        jumpWasPressed = mc.options.jumpKey.isPressed();
        lockAxis();
    }

    @Override
    public void onDeactivate() {
        release();
    }

    private void release() {
        if (pathing) {
            PathManagers.get().stop();
            pathing = false;
        }

        mc.options.forwardKey.setPressed(forwardWasPressed);
        mc.options.jumpKey.setPressed(jumpWasPressed);
        phase = Phase.Bounce;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;

        if (phase == Phase.Bounce) tickBounce();
        else tickWalk();
    }

    // ---------------------------------------------------------------- bounce

    private void tickBounce() {
        if (keepHeading.get()) mc.player.setYaw(axisYaw());

        // Jump on the ground and hold it in the air: vanilla jumps, then recasts the elytra as
        // soon as you are falling again - the whole bounce, without touching a packet.
        mc.options.jumpKey.setPressed(canBounce() && !mc.player.isGliding());

        // Forward stays held through landings, so the hop keeps the speed it landed with.
        mc.options.forwardKey.setPressed(true);

        if (!hasElytra()) {
            info("No elytra equipped - walking the set distance, then switching off.");
            startWalk();
            return;
        }

        Vec3d velocity = mc.player.getVelocity();
        if (Math.hypot(velocity.x, velocity.z) > 0.1) stall = 0;
        else if (++stall >= stallTicks.get()) startWalk();
    }

    // ---------------------------------------------------------------- walk

    private void startWalk() {
        lockAxis();
        phase = Phase.Walk;
        stall = 0;

        mc.options.jumpKey.setPressed(false);

        walkStart = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        walkLast = walkStart;
        walkStuck = 0;
        pathStarted = false;
        walkTarget = BlockPos.ofFloored(
            walkStart.x + (alongX ? direction * walkBlocks.get() : 0),
            mc.player.getY(),
            walkStart.z + (alongX ? 0 : direction * walkBlocks.get())
        );

        if (mode.get() == WalkMode.Baritone) {
            if (BaritoneUtils.IS_AVAILABLE) {
                // Exact goal, not just the XZ column: the walk keeps the height it started at,
                // which is what keeps it on the highway instead of diving for a lower route.
                PathManagers.get().moveTo(walkTarget);
                pathing = true;
                return;
            }

            info("Baritone is not available - walking it myself instead.");
        }
    }

    private void tickWalk() {
        if (pathing) {
            // Baritone needs a tick or two to pick the goal up; only a stop after it started
            // means it is done (or gave up).
            if (PathManagers.get().isPathing()) pathStarted = true;
            else if (pathStarted) finishWalk();

            return;
        }

        double dx = walkTarget.getX() + 0.5 - mc.player.getX();
        double dz = walkTarget.getZ() + 0.5 - mc.player.getZ();

        // The walker always faces its own target: that is what keeps the line straight.
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));

        mc.options.forwardKey.setPressed(true);
        mc.player.setSprinting(true);
        mc.options.jumpKey.setPressed(mc.player.horizontalCollision);

        double travelled = (mc.player.getX() - walkStart.x) * (alongX ? direction : 0)
            + (mc.player.getZ() - walkStart.z) * (alongX ? 0 : direction);

        // Stuck against something the jump cannot clear: stop walking instead of pushing forever.
        double moved = Math.hypot(mc.player.getX() - walkLast.x, mc.player.getZ() - walkLast.z);
        if (moved > 0.3) {
            walkLast = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            walkStuck = 0;
        } else if (++walkStuck > stallTicks.get()) {
            finishWalk();
            return;
        }

        if (travelled >= walkBlocks.get() || Math.abs(dx) + Math.abs(dz) < 1.0) finishWalk();
    }

    private void finishWalk() {
        if (pathing) {
            PathManagers.get().stop();
            pathing = false;
        }

        mc.options.forwardKey.setPressed(forwardWasPressed);
        mc.options.jumpKey.setPressed(false);

        phase = Phase.Bounce;
        stall = 0;

        if (!hasElytra()) {
            info("Walk finished and there is still no elytra - switching off.");
            toggle();
            return;
        }

        // A walk that never got anywhere would repeat forever, so give up after three.
        double progress = (mc.player.getX() - walkStart.x) * (alongX ? direction : 0)
            + (mc.player.getZ() - walkStart.z) * (alongX ? 0 : direction);

        if (progress >= 1.0) failedWalks = 0;
        else if (++failedWalks >= 3) {
            info("Can't get any further forward - blocked, switching off.");
            toggle();
        }
    }

    // ---------------------------------------------------------------- helpers

    private void lockAxis() {
        switch (axis.get()) {
            case PosX -> {
                alongX = true;
                direction = 1;
            }
            case NegX -> {
                alongX = true;
                direction = -1;
            }
            case PosZ -> {
                alongX = false;
                direction = 1;
            }
            case NegZ -> {
                alongX = false;
                direction = -1;
            }
            case Auto -> {
                Vec3d look = mc.player.getRotationVec(1.0f);
                alongX = Math.abs(look.x) >= Math.abs(look.z);
                direction = (alongX ? look.x : look.z) >= 0 ? 1 : -1;
            }
        }
    }

    private float axisYaw() {
        double dx = alongX ? direction : 0;
        double dz = alongX ? 0 : direction;

        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private boolean canBounce() {
        return !mc.player.hasVehicle() && !mc.player.isTouchingWater() && !mc.player.getAbilities().flying;
    }

    private boolean hasElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);
        if (!chest.isOf(Items.ELYTRA)) return false;

        return !chest.isDamageable() || chest.getDamage() < chest.getMaxDamage() - 1;
    }
}
