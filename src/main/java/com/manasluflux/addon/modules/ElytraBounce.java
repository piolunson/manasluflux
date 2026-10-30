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
import meteordevelopment.meteorclient.systems.modules.player.Rotation;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Meteor Client's ElytraFly "Bounce" mode, verbatim - same settings, defaults and tick logic -
 * with one addition: when a block stops you, Baritone walks you a few blocks forward and the
 * bounce takes over again.
 *
 * <p>Bounce code: Meteor Client (https://github.com/MeteorDevelopment/meteor-client),
 * {@code systems/modules/movement/elytrafly/modes/Bounce.java}, GPL-3.0, Copyright (c) Meteor
 * Development. Credit to Luna for the original Elytra Recast mod.
 */
public class ElytraBounce extends Module {
    /** {@code Entity#GLIDING_FLAG_INDEX} - the tracked-data bit {@code startGliding()} flips. */
    private static final int GLIDING_FLAG = 7;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgNudge = settings.createGroup("Nudge");

    // ------------------------------------------------------------------ Meteor's settings

    private final Setting<Rotation.LockMode> yawLockMode = sgGeneral.add(new EnumSetting.Builder<Rotation.LockMode>()
        .name("yaw-lock")
        .description("Whether to enable yaw lock or not")
        .defaultValue(Rotation.LockMode.Smart)
        .build()
    );

    private final Setting<Double> yaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("yaw")
        .description("The yaw angle to look at when using simple rotation lock in bounce mode.")
        .defaultValue(0)
        .range(0, 360)
        .sliderRange(0, 360)
        .visible(() -> yawLockMode.get() == Rotation.LockMode.Simple)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch-lock")
        .description("Whether to lock your pitch angle.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("The pitch angle to look at when using the bounce mode.")
        .defaultValue(85)
        .range(0, 90)
        .sliderRange(0, 90)
        .visible(lockPitch::get)
        .build()
    );

    private final Setting<Boolean> autoJump = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-jump")
        .description("Automatically jumps for you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sprint = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint-constantly")
        .description("Sprints all the time. If turned off, it will only sprint when the player is touching the ground.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> restart = sgGeneral.add(new BoolSetting.Builder()
        .name("restart")
        .description("Restarts flying with the elytra when rubberbanding.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> restartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("restart-delay")
        .description("How many ticks to wait before restarting the elytra again after rubberbanding.")
        .defaultValue(7)
        .min(0)
        .sliderRange(0, 20)
        .visible(restart::get)
        .build()
    );

    private final Setting<Boolean> manualTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("manual-takeoff")
        .description("Does not automatically take off.")
        .defaultValue(false)
        .build()
    );

    // ------------------------------------------------------------------ the addition

    private final Setting<Boolean> nudge = sgNudge.add(new BoolSetting.Builder()
        .name("baritone-nudge")
        .description("When a block stops the bounce, lets Baritone walk a few blocks forward and then bounces again.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> nudgeBlocks = sgNudge.add(new IntSetting.Builder()
        .name("nudge-blocks")
        .description("How far forward Baritone should walk past the block.")
        .defaultValue(4)
        .range(1, 32)
        .sliderRange(1, 16)
        .visible(nudge::get)
        .build()
    );

    private final Setting<Integer> blockedTicks = sgNudge.add(new IntSetting.Builder()
        .name("blocked-ticks")
        .description("Ticks pressed against a block before the nudge starts.")
        .defaultValue(5)
        .range(1, 40)
        .sliderRange(1, 20)
        .visible(nudge::get)
        .build()
    );

    // ------------------------------------------------------------------ state

    private boolean rubberbanded;
    private int tickDelay;
    private double prevFov;

    private boolean nudging;
    private boolean nudgeStarted;
    private int nudgeTicks;
    private BlockPos nudgeTarget;

    private int blocked;

    private boolean forwardWasPressed;
    private boolean jumpWasPressed;

    public ElytraBounce() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "mf-elytra-bounce", "Meteor's elytra bounce mode, unchanged, plus a Baritone nudge a few blocks forward when a block stops you.");
    }

    @Override
    public void onActivate() {
        prevFov = mc.options.getFovEffectScale().getValue();

        rubberbanded = false;
        tickDelay = restartDelay.get();

        nudging = false;
        nudgeStarted = false;
        nudgeTicks = 0;
        blocked = 0;

        forwardWasPressed = mc.options.forwardKey.isPressed();
        jumpWasPressed = mc.options.jumpKey.isPressed();
    }

    @Override
    public void onDeactivate() {
        if (nudging) {
            if (PathManagers.get().isPathing()) PathManagers.get().stop();
            nudging = false;
        }

        mc.options.forwardKey.setPressed(forwardWasPressed);
        mc.options.jumpKey.setPressed(jumpWasPressed);

        rubberbanded = false;
        if (prevFov != 0 && !sprint.get()) mc.options.getFovEffectScale().setValue(prevFov);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;

        if (nudging) {
            tickNudge();
            return;
        }

        // Held jump while not gliding is sent as a start-gliding packet: that single press is the
        // recast. Vanilla would want a double jump here.
        if (mc.options.jumpKey.isPressed() && !mc.player.isGliding() && !manualTakeoff.get()) {
            mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        // Make sure all the conditions are met (player has an elytra, isn't in water, etc)
        if (checkConditions(mc.player)) {
            if (!rubberbanded) {
                // Without sprint the elytra FOV effect pumps on and off with every bounce; flatten it.
                if (prevFov != 0 && !sprint.get()) mc.options.getFovEffectScale().setValue(0.0);

                if (autoJump.get()) mc.options.jumpKey.setPressed(true);
                mc.options.forwardKey.setPressed(true);
                mc.player.setYaw(getYawDirection());
                if (lockPitch.get()) mc.player.setPitch(pitch.get().floatValue());
            }

            if (!sprint.get()) {
                // Sprinting in the air rubberbands on some anticheats; only sprint on the ground.
                if (mc.player.isGliding()) mc.player.setSprinting(mc.player.isOnGround());
                else mc.player.setSprinting(true);
            }

            // Rubberbanding
            if (rubberbanded && restart.get()) {
                if (tickDelay > 0) tickDelay--;
                else {
                    mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
                    rubberbanded = false;
                    tickDelay = restartDelay.get();
                }
            }

            tickBlocked();
        } else {
            blocked = 0;
        }
    }

    @EventHandler
    private void onPreTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate() || nudging) return;

        if (checkConditions(mc.player) && sprint.get()) mc.player.setSprinting(true);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.player == null || !(event.packet instanceof PlayerPositionLookS2CPacket)) return;

        rubberbanded = true;
        // Meteor's stopFallFlying(): the server does not think you are gliding any more.
        ((EntityFlagMixin) mc.player).mf$setFlag(GLIDING_FLAG, false);
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || sprint.get()) return;

        if (event.packet instanceof ClientCommandC2SPacket packet
            && packet.getMode() == ClientCommandC2SPacket.Mode.START_FALL_FLYING) {
            mc.player.setSprinting(true);
        }
    }

    // ------------------------------------------------------------------ nudge past a block

    private void tickBlocked() {
        if (!nudge.get() || rubberbanded) {
            blocked = 0;
            return;
        }

        if (mc.player.horizontalCollision && mc.player.isOnGround()) blocked++;
        else blocked = 0;

        if (blocked >= blockedTicks.get()) {
            blocked = 0;
            startNudge();
        }
    }

    private void startNudge() {
        Vec3d look = mc.player.getRotationVec(1.0f);
        boolean alongX = Math.abs(look.x) >= Math.abs(look.z);
        int direction = (alongX ? look.x : look.z) >= 0 ? 1 : -1;

        nudgeTarget = BlockPos.ofFloored(
            mc.player.getX() + (alongX ? direction * nudgeBlocks.get() : 0),
            mc.player.getY(),
            mc.player.getZ() + (alongX ? 0 : direction * nudgeBlocks.get())
        );

        if (!BaritoneUtils.IS_AVAILABLE) {
            info("Baritone is not available - can't nudge past the block.");
            return;
        }

        // Baritone drives the walk, so let go of the keys the bounce was holding.
        mc.options.jumpKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);

        PathManagers.get().moveTo(nudgeTarget, true);

        nudging = true;
        nudgeStarted = false;
        nudgeTicks = 0;
    }

    private void tickNudge() {
        nudgeTicks++;

        if (PathManagers.get().isPathing()) nudgeStarted = true;
        else if (nudgeStarted) {
            finishNudge();
            return;
        }

        // Ten seconds is plenty for a few blocks; do not sit in here forever if Baritone gives up.
        if (nudgeTicks > 200) {
            info("Baritone could not get past the block.");
            finishNudge();
        }
    }

    private void finishNudge() {
        if (PathManagers.get().isPathing()) PathManagers.get().stop();

        nudging = false;
        nudgeStarted = false;
        blocked = 0;
    }

    // ------------------------------------------------------------------ helpers

    private float getYawDirection() {
        return switch (yawLockMode.get()) {
            case None -> mc.player.getYaw();
            case Smart -> Math.round((mc.player.getYaw() + 1f) / 45f) * 45f;
            case Simple -> yaw.get().floatValue();
        };
    }

    /** Meteor's bounce conditions: no creative flight, no vehicle, not climbing, not in water. */
    private boolean checkConditions(net.minecraft.client.network.ClientPlayerEntity player) {
        BlockState blockState = player.getSteppingBlockState();
        boolean isClimbing = blockState.isIn(BlockTags.CLIMBABLE) && !blockState.isIn(BlockTags.CAN_GLIDE_THROUGH);

        return !player.getAbilities().flying
            && !player.hasVehicle()
            && !isClimbing
            && !player.isTouchingWater()
            && !player.hasStatusEffect(StatusEffects.LEVITATION);
    }
}
