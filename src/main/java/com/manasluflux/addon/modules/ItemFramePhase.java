package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * The phase attempt shared by the {@code ncp-itemframe} and {@code th-itemframe} modules: sneak,
 * jump, then push small position packets forward until the client is standing inside the block it
 * was facing - and take it back when the server disagrees.
 *
 * <p><b>What the research says about item frames, honestly:</b> an item frame is a thin
 * {@code BlockAttachedEntity}. Its box is a <i>targeting</i> box ({@code canHit()}); it is not
 * movement collision, and it does not make the block it hangs on passable. No verified
 * item-frame-specific phase technique exists for 1.21 in any public client or anticheat source: the
 * documented phase bypasses are generic movement-packet tricks, and the only item-frame material
 * found is a Grim issue about a <i>false positive</i> when attacking a frame while moving. So this
 * module does the thing that is actually available: a generic stepped phase that does not care that
 * a frame is in the way (it ignores frame entities entirely, because they cannot block you), aimed
 * at the block behind the frame when one is there.
 *
 * <p>The two variants differ only in pacing, which is the part anticheats are sensitive to:
 * <b>NCP</b> is checked by SurvivalFly-style movement validation that also looks at passability, so
 * it wants small, steps-per-tick-cadenced increments; <b>Themis</b> advertises Fly/Step/Speed/Blink
 * checks, so it wants fewer, larger packets and a slower cadence. Neither is verified to work - this
 * is a configurable attempt with retries, not a proven bypass.
 *
 * <p>Everything the server sees can be silenced: the rotation is sent as look packets while your
 * camera stays put, the item switch is a server-side hotbar swap, and the movement itself is driven
 * by our own position packets.
 */
public abstract class ItemFramePhase extends Module {
    public enum Mode { Normal, Frame }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSilent = settings.createGroup("Silent");

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Normal: sneak, jump, then phase in the direction you look. Frame: find an item frame in reach and phase into the block it is attached to.")
        .defaultValue(Mode.Normal)
        .build()
    );

    private final Setting<Double> distance = sgGeneral.add(new DoubleSetting.Builder()
        .name("distance")
        .description("How far to push yourself, in blocks. A little over 1 is enough to pass a block face.")
        .defaultValue(1.25)
        .range(0.1, 10000.0)
        .sliderRange(0.1, 3.0)
        .build()
    );

    private final Setting<Integer> stepTicks = sgGeneral.add(new IntSetting.Builder()
        .name("step-every")
        .description("Ticks between movement packets. 1 = one packet per tick (slowest, gentlest), 0 = push the whole distance in one tick.")
        .defaultValue(1)
        .range(0, 40)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> retries = sgGeneral.add(new IntSetting.Builder()
        .name("retries")
        .description("How many times to try again after the server pushes you back.")
        .defaultValue(3)
        .range(0, 100)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Integer> retryDelay = sgGeneral.add(new IntSetting.Builder()
        .name("retry-delay")
        .description("Ticks to wait after an attempt before deciding whether it worked.")
        .defaultValue(10)
        .range(0, 200)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak")
        .description("Hold sneak for the attempt - it keeps your own interaction from firing mid-phase.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jump = sgGeneral.add(new BoolSetting.Builder()
        .name("jump")
        .description("Jump once before pushing, so the push starts from a moving tick.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentRotation = sgSilent.add(new BoolSetting.Builder()
        .name("silent-rotation")
        .description("Turn towards the phase direction on the server only - your camera never moves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silentSwitch = sgSilent.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Switch to a hotbar slot on the server for the attempt, then switch back.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> switchSlot = sgSilent.add(new IntSetting.Builder()
        .name("switch-slot")
        .description("Hotbar slot (1-9) to hold during the attempt.")
        .defaultValue(1)
        .range(1, 9)
        .sliderRange(1, 9)
        .visible(silentSwitch::get)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Report each attempt and what happened.")
        .defaultValue(true)
        .build()
    );

    private enum Stage { Idle, Prepare, Push, Wait }

    private final double plainStep;
    private final int prepareTicks;

    private Stage stage = Stage.Idle;
    private int ticks;
    private int attemptsLeft;
    private boolean setback;
    private boolean phased;
    private boolean switched;
    private boolean sneakingBefore;
    private float yawBefore;
    private float pitchBefore;
    private Vec3d direction = Vec3d.ZERO;
    private Vec3d startPos;
    private double pushed;
    private BlockPos target;

    protected ItemFramePhase(String name, String description, double plainStep, int prepareTicks) {
        super(AddonTemplate.BYPASS_CATEGORY, name, description);

        this.plainStep = plainStep;
        this.prepareTicks = prepareTicks;
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) {
            error("Join a world first.");
            toggle();
            return;
        }

        stage = Stage.Idle;
        attemptsLeft = retries.get() + 1;
        begin();
    }

    @Override
    public void onDeactivate() {
        finish(true);
    }

    private void begin() {
        setback = false;
        phased = false;
        pushed = 0;

        startPos = mc.player.getEntityPos();
        sneakingBefore = mc.player.isSneaking();
        yawBefore = mc.player.getYaw();
        pitchBefore = mc.player.getPitch();

        target = resolveTarget();
        direction = resolveDirection();

        if (silentSwitch.get() && !switched) {
            InvUtils.swap(switchSlot.get() - 1, false);
            switched = true;
        }

        if (silentRotation.get() && direction.lengthSquared() > 1.0E-6) {
            float yaw = (float) (Math.toDegrees(Math.atan2(-direction.x, direction.z)));
            sendLook(yaw, pitchBefore);
        }

        if (sneak.get()) mc.player.setSneaking(true);

        stage = Stage.Prepare;
        ticks = 0;
    }

    /** Frame mode looks for a frame in reach; Normal mode just uses the look direction. */
    private BlockPos resolveTarget() {
        if (mode.get() != Mode.Frame || mc.world == null || mc.player == null) return null;

        ItemFrameEntity nearest = null;
        double best = 9.0;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity frame)) continue;

            double distanceSq = mc.player.squaredDistanceTo(frame);
            if (distanceSq > 25.0 || distanceSq >= best) continue;

            best = distanceSq;
            nearest = frame;
        }

        if (nearest == null) {
            if (chatFeedback.get()) warning("No item frame in reach - phasing in the look direction instead.");
            return null;
        }

        return nearest.getAttachedBlockPos().toImmutable();
    }

    private Vec3d resolveDirection() {
        if (target != null) {
            Vec3d to = Vec3d.ofCenter(target).subtract(mc.player.getEntityPos());
            Vec3d flat = new Vec3d(to.x, 0, to.z);

            if (flat.lengthSquared() > 1.0E-4) return flat.normalize();
        }

        float yaw = (float) Math.toRadians(mc.player.getYaw());
        return new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)).normalize();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        switch (stage) {
            case Prepare -> {
                if (jump.get() && mc.player.isOnGround()) mc.player.jump();

                if (++ticks >= prepareTicks) {
                    stage = Stage.Push;
                    ticks = 0;
                }
            }

            case Push -> pushSteps();

            case Wait -> {
                if (++ticks < retryDelay.get()) return;

                // The client is inside the block either way - we put it there - so the only honest
                // test is whether the server left us alone while we sat in it.
                phased = !setback && insideBlock();

                if (phased) {
                    if (chatFeedback.get()) info("Phased %.2f blocks into %s and stayed there.", pushed, at());
                    toggle();
                    return;
                }

                attemptsLeft--;

                if (setback && attemptsLeft > 0) {
                    if (chatFeedback.get()) warning("Pushed back - retrying (%d left).", attemptsLeft);
                    resetPosition();
                    begin();
                    return;
                }

                if (chatFeedback.get()) {
                    if (setback) warning("No phase - the server kept pushing back after %d tries.", retries.get() + 1);
                    else warning("No phase - nothing moved us into the block.");
                }

                toggle();
            }

            default -> {}
        }
    }

    /**
     * Sends the movement packets for this tick. {@code step-every} 0 pushes the whole distance in one
     * tick (a single big step, which reads as a teleport); anything else sends one step packet every
     * that many ticks, which is the gentler pacing NCP wants.
     */
    private void pushSteps() {
        int every = stepTicks.get();

        if (every <= 0) {
            while (pushed < distance.get()) sendStep();
        } else if (ticks % every == 0) {
            sendStep();
        }

        ticks++;

        if (pushed >= distance.get()) {
            stage = Stage.Wait;
            ticks = 0;
        }
    }

    /** One push: move the client, tell the server, count what is left. */
    private void sendStep() {
        double step = Math.min(plainStep, distance.get() - pushed);
        if (step <= 0) return;

        Vec3d next = mc.player.getEntityPos().add(direction.multiply(step));
        mc.player.setPosition(next.x, next.y, next.z);
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
            next.x, next.y, next.z, mc.player.isOnGround(), false));

        pushed += step;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;
        if (stage == Stage.Idle) return;

        // The server disagreeing with our position is the signal that this attempt failed.
        setback = true;
    }

    private boolean insideBlock() {
        BlockPos pos = mc.player.getBlockPos();
        return !mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty();
    }

    private void resetPosition() {
        stage = Stage.Idle;

        if (startPos != null) {
            mc.player.setPosition(startPos.x, startPos.y, startPos.z);
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                startPos.x, startPos.y, startPos.z, mc.player.isOnGround(), false));
        }
    }

    /** Puts the player, camera, sneak and hotbar back the way they were. */
    private void finish(boolean quiet) {
        stage = Stage.Idle;

        if (mc.player == null) return;

        if (sneak.get()) mc.player.setSneaking(sneakingBefore);
        if (silentRotation.get()) sendLook(yawBefore, pitchBefore);
        if (switched) {
            InvUtils.swapBack();
            switched = false;
        }

        if (!quiet && chatFeedback.get()) info("Item Frame Phase stopped.");
    }

    private void sendLook(float yaw, float pitch) {
        if (mc.getNetworkHandler() == null || mc.player == null) return;

        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
            yaw, pitch, mc.player.isOnGround(), false));
    }

    private String at() {
        if (target != null) return target.toShortString();
        return String.format("%.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    @Override
    public String getInfoString() {
        return mode.get() + " " + (int) Math.round(pushed * 100) / 100.0 + "/" + distance.get();
    }
}
