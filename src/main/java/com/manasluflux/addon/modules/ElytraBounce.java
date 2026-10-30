package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.mixin.EntityFlagMixin;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.pathing.BaritoneUtils;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Elytra bounce along a highway. The bounce itself is Meteor Client's {@code ElytraFly} "Bounce"
 * mode (Meteor Development, GPL-3.0), ported one to one: an explicit start-gliding packet on the
 * jump key instead of the vanilla double jump, yaw and pitch locks, the sprint / FOV handling,
 * and rubberband recovery that stops the local glide and recasts it after a delay.
 *
 * <p>Added on top: when the bouncing stops - no forward speed for {@code stall-ticks}, or no
 * elytra left - it walks {@code walk-blocks} forward along the chosen highway axis, then hands
 * control back to the bounce. Walking is either Baritone (a goal on the same axis, through
 * Meteor's path manager) or this module's own walker, made for a straight highway: it keeps the
 * line, sprint-jumps over steps, slows before the goal and stops instead of walking off an edge.
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

    /** Meteor's rotation lock modes, plus the highway axis. */
    public enum YawLock {
        None,
        Smart,
        Simple,
        Axis
    }

    private enum Phase {
        Bounce,
        Walk
    }

    /** {@code Entity#GLIDING_FLAG_INDEX} - the tracked-data bit {@code startGliding()} flips. */
    private static final int GLIDING_FLAG = 7;

    // ------------------------------------------------------------------ settings

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgWalk = settings.createGroup("Walk");

    private final Setting<YawLock> yawLock = sgGeneral.add(new EnumSetting.Builder<YawLock>()
        .name("yaw-lock")
        .description("None keeps your yaw, Smart rounds it to 45 degree steps, Simple holds the yaw below, Axis holds the highway axis.")
        .defaultValue(YawLock.Smart)
        .build()
    );

    private final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw")
        .description("The yaw held when yaw-lock is Simple.")
        .defaultValue(0)
        .range(0, 360)
        .sliderRange(0, 360)
        .visible(() -> yawLock.get() == YawLock.Simple)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-lock")
        .description("Holds your pitch while bouncing, like Meteor's bounce mode.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("The pitch held while bouncing. Looking down is what makes the bounce skim the ground.")
        .defaultValue(85)
        .range(0, 90)
        .sliderRange(0, 90)
        .visible(lockPitch::get)
        .build()
    );

    private final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Holds the jump key for you, which is what bounces you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sprintConstantly = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-constantly")
        .description("Sprints all the time. If off, it only sprints while touching the ground, which stops the elytra rubberbanding on some anticheats.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> restart = sgGeneral.add(new BoolSetting.Builder()
        .name("restart")
        .description("Recasts the elytra after a rubberband instead of dropping out of flight.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> restartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("restart-delay")
        .description("Ticks to wait after a rubberband before recasting the elytra.")
        .defaultValue(7)
        .min(0)
        .sliderRange(0, 20)
        .visible(restart::get)
        .build()
    );

    private final Setting<Boolean> manualTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("manual-takeoff")
        .description("Does not send the start-gliding packet when jump is held.")
        .defaultValue(false)
        .build()
    );

    private final Setting<WalkMode> mode = sgWalk.add(new EnumSetting.Builder<WalkMode>()
        .name("walk-mode")
        .description("Baritone walks to the goal; Self is this module's own walker, made for a straight highway.")
        .defaultValue(WalkMode.Baritone)
        .build()
    );

    private final Setting<Integer> walkBlocks = sgWalk.add(new IntSetting.Builder()
        .name("walk-blocks")
        .description("How far forward to walk once the bouncing stops.")
        .defaultValue(50)
        .range(1, 2000)
        .sliderRange(10, 500)
        .build()
    );

    private final Setting<Axis> axis = sgWalk.add(new EnumSetting.Builder<Axis>()
        .name("axis")
        .description("Which way the highway runs. Auto locks to the axis you are facing when the module starts.")
        .defaultValue(Axis.Auto)
        .build()
    );

    private final Setting<Integer> stallTicks = sgWalk.add(new IntSetting.Builder()
        .name("stall-ticks")
        .description("Ticks without forward speed before the bounce counts as stopped and the walk starts.")
        .defaultValue(40)
        .range(5, 200)
        .sliderRange(10, 100)
        .build()
    );

    private final Setting<Boolean> stopAtEdge = sgWalk.add(new BoolSetting.Builder()
        .name("stop-at-edge")
        .description("Self mode: stops at the end of the road instead of walking off it.")
        .defaultValue(true)
        .visible(() -> mode.get() == WalkMode.Self)
        .build()
    );

    // ------------------------------------------------------------------ state

    private Phase phase = Phase.Bounce;
    private int stall;
    private int failedWalks;
    private boolean pathing;
    private boolean pathStarted;

    private boolean rubberbanded;
    private int tickDelay;
    private double prevFov;

    private boolean alongX;
    private int direction = 1;

    private Vec3d walkStart = Vec3d.ZERO;
    private Vec3d walkLast = Vec3d.ZERO;
    private BlockPos walkTarget;

    private int walkStuck;

    private boolean forwardWasPressed;
    private boolean jumpWasPressed;

    public ElytraBounce() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "mf-elytra-bounce", "Bounces your elytra along a highway like Meteor's bounce mode, and when the bouncing stops it walks a set distance forward - with Baritone or its own walker.");
    }

    @Override
    public void onActivate() {
        phase = Phase.Bounce;
        stall = 0;
        failedWalks = 0;
        pathing = false;
        pathStarted = false;

        rubberbanded = false;
        tickDelay = restartDelay.get();
        prevFov = mc.options.getFovEffectScale().getValue();

        forwardWasPressed = mc.options.forwardKey.isPressed();
        jumpWasPressed = mc.options.jumpKey.isPressed();

        lockAxis();
    }

    @Override
    public void onDeactivate() {
        release();

        rubberbanded = false;
        if (prevFov != 0 && !sprintConstantly.get()) mc.options.getFovEffectScale().setValue(prevFov);
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

    // ------------------------------------------------------------------ events

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;

        if (phase == Phase.Bounce) tickBounce();
        else tickWalk();
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;

        if (phase == Phase.Bounce && sprintConstantly.get() && canBounce()) mc.player.setSprinting(true);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (phase != Phase.Bounce || !(event.packet instanceof PlayerPositionLookS2CPacket)) return;

        // Rubberbanded - the server does not think you are flying any more, so neither do we.
        rubberbanded = true;
        setGliding(false);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || phase != Phase.Bounce || sprintConstantly.get()) return;

        if (event.packet instanceof ClientCommandC2SPacket packet
            && packet.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            mc.player.setSprinting(true);
        }
    }

    // ------------------------------------------------------------------ bounce

    private void tickBounce() {
        // Held jump while not gliding is sent as a start-gliding packet: that single press is the
        // recast. Vanilla would want a double jump here.
        if (mc.options.jumpKey.isPressed() && !mc.player.isGliding() && !manualTakeoff.get() && hasElytra()) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        if (!canBounce()) return;

        if (!rubberbanded) {
            // Without sprint the elytra FOV effect pumps on and off with every bounce; flatten it.
            if (prevFov != 0 && !sprintConstantly.get()) mc.options.getFovEffectScale().setValue(0.0);

            if (autoJump.get()) mc.options.jumpKey.setPressed(true);
            mc.options.forwardKey.setPressed(true);
            mc.player.setYaw(yawDirection());
            if (lockPitch.get()) mc.player.setPitch(pitch.get().floatValue());
        }

        if (!sprintConstantly.get()) {
            // Sprinting in the air rubberbands on some anticheats; only sprint on the ground.
            if (mc.player.isGliding()) mc.player.setSprinting(mc.player.isOnGround());
            else mc.player.setSprinting(true);
        }

        if (rubberbanded && restart.get()) {
            if (tickDelay > 0) tickDelay--;
            else {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                rubberbanded = false;
                tickDelay = restartDelay.get();
            }
        }

        tickStall();
    }

    private void tickStall() {
        if (!hasElytra()) {
            info("No elytra equipped - walking the set distance, then switching off.");
            startWalk();
            return;
        }

        Vec3d velocity = mc.player.getVelocity();
        if (Math.hypot(velocity.x, velocity.z) > 0.1) stall = 0;
        else if (++stall >= stallTicks.get()) startWalk();
    }

    private float yawDirection() {
        return switch (yawLock.get()) {
            case None -> mc.player.getYaw();
            case Smart -> Math.round((mc.player.getYaw() + 1f) / 45f) * 45f;
            case Simple -> yaw.get().floatValue();
            case Axis -> axisYaw();
        };
    }

    // ------------------------------------------------------------------ walk

    private void startWalk() {
        lockAxis();
        phase = Phase.Walk;
        stall = 0;

        setGliding(false);
        mc.options.jumpKey.setPressed(false);
        mc.player.setSprinting(false);

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
                PathManagers.get().moveTo(walkTarget, true);
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

        double targetX = walkTarget.getX() + 0.5;
        double targetZ = walkTarget.getZ() + 0.5;
        double dx = targetX - mc.player.getX();
        double dz = targetZ - mc.player.getZ();
        double remaining = Math.hypot(dx, dz);

        if (remaining < 0.7) {
            finishWalk();
            return;
        }

        // Aim a short way ahead on the line rather than at the far goal: the walk stays straight
        // and does not weave when it gets close.
        double aimX = dx;
        double aimZ = dz;
        if (remaining > 2.5) {
            aimX = dx / remaining * 2.5;
            aimZ = dz / remaining * 2.5;
        }
        mc.player.setYaw((float) Math.toDegrees(Math.atan2(-aimX, aimZ)));

        boolean edge = stopAtEdge.get() && mc.player.isOnGround() && !groundAhead();
        boolean blocked = mc.player.horizontalCollision;

        mc.options.forwardKey.setPressed(!edge);
        mc.player.setSprinting(!edge && remaining > 3);
        mc.options.jumpKey.setPressed(blocked);

        if (edge) {
            info("End of the highway - stopping the walk here.");
            finishWalk();
            return;
        }

        trackProgress();
    }

    private void trackProgress() {
        double moved = Math.hypot(mc.player.getX() - walkLast.x, mc.player.getZ() - walkLast.z);
        if (moved > 0.3) {
            walkLast = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            walkStuck = 0;
        } else if (++walkStuck > stallTicks.get()) {
            finishWalk();
            return;
        }

        double travelled = (mc.player.getX() - walkStart.x) * (alongX ? direction : 0)
            + (mc.player.getZ() - walkStart.z) * (alongX ? 0 : direction);

        if (travelled >= walkBlocks.get() - 0.7) finishWalk();
    }

    private boolean groundAhead() {
        double aheadX = mc.player.getX() + (alongX ? direction : 0) * 1.2;
        double aheadZ = mc.player.getZ() + (alongX ? 0 : direction) * 1.2;
        BlockPos below = BlockPos.ofFloored(aheadX, mc.player.getY() - 0.6, aheadZ);

        if (!mc.world.getFluidState(below).isEmpty()) return true;

        return !mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty();
    }

    private void finishWalk() {
        if (pathing) {
            PathManagers.get().stop();
            pathing = false;
        }

        mc.options.forwardKey.setPressed(forwardWasPressed);
        mc.options.jumpKey.setPressed(false);
        mc.player.setSprinting(false);

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

    // ------------------------------------------------------------------ helpers

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

    /** Meteor's bounce conditions: no creative flight, no vehicle, not climbing, not in water. */
    private boolean canBounce() {
        BlockState state = mc.player.getSteppingBlockState();
        boolean climbing = state.isIn(BlockTags.CLIMBABLE) && !state.isIn(BlockTags.CAN_GLIDE_THROUGH);

        return !mc.player.getAbilities().flying
            && !mc.player.hasVehicle()
            && !climbing
            && !mc.player.isTouchingWater()
            && !mc.player.hasStatusEffect(StatusEffects.LEVITATION);
    }

    private void setGliding(boolean gliding) {
        if (mc.player.isGliding() == gliding) return;

        ((EntityFlagMixin) mc.player).mf$setFlag(GLIDING_FLAG, gliding);
    }

    private boolean hasElytra() {
        ItemStack chest = mc.player.getEquippedStack(EquipmentSlot.CHEST);

        return LivingEntity.canGlideWith(chest, EquipmentSlot.CHEST)
            && (!chest.isDamageable() || chest.getDamage() < chest.getMaxDamage() - 1);
    }
}
