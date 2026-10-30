package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;

public class SpeedMine extends Module {
    private static final int DECOY_Y_OFFSET = 2000;

    private static final double GRIM_MIN_EYE = 0.4;
    private static final double GRIM_MAX_EYE = 1.62;

    /** Ticks past the predicted server completion before the secondary is written off. */
    private static final int SECONDARY_TIMEOUT = 10;

    /** How close to the edge of a face the break-ahead ray may exit before it counts as a graze. */
    private static final double BREAK_AHEAD_EDGE = 0.05;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> threshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("threshold")
        .description("Predicted progress to reach before the stop packet is sent. 1.0 waits for a full break; lower is faster and louder.")
        .defaultValue(0.7)
        .range(0.1, 1.0)
        .sliderRange(0.1, 1.0)
        .build()
    );

    private final Setting<Integer> fudgeTicks = sgGeneral.add(new IntSetting.Builder()
        .name("fudge-ticks")
        .description("Ticks of progress to ignore, so the prediction does not run ahead of the server.")
        .defaultValue(1)
        .range(0, 5)
        .sliderRange(0, 5)
        .build()
    );

    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Ticks to wait after a stop before the next break starts.")
        .defaultValue(6)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Never swap tools out from under an item you are using (gaps, pots, food).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> decoy = sgGeneral.add(new BoolSetting.Builder()
        .name("grim-decoy")
        .description("Sends a second block-start two thousand blocks below the world, so the pair Grim sees does not read as one fast break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> doubleBreak = sgGeneral.add(new BoolSetting.Builder()
        .name("double-break")
        .description("Demotes a break in progress to a second slot so a new click can start at once.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> breakAhead = sgGeneral.add(new BoolSetting.Builder()
        .name("break-ahead")
        .description("Also mine the block your look ray continues into, when it leaves the clicked block cleanly through a face.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> rebreak = sgGeneral.add(new BoolSetting.Builder()
        .name("rebreak")
        .description("Keep re-sending the stop so the block actually breaks on servers that need it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your hand with the packets.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a block may be to be mined.")
        .defaultValue(5.0)
        .range(1.0, 7.0)
        .sliderRange(1.0, 7.0)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draw the block being mined, shrinking as the predicted progress rises.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline colour.")
        .defaultValue(new SettingColor(255, 255, 255, 150))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> primaryColor = sgRender.add(new ColorSetting.Builder()
        .name("primary-color")
        .description("Fill colour of the primary break.")
        .defaultValue(new SettingColor(255, 180, 255, 60))
        .visible(render::get)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("secondary-color")
        .description("Fill colour of the secondary (demoted) break.")
        .defaultValue(new SettingColor(255, 255, 255, 40))
        .visible(render::get)
        .build()
    );

    private BlockPos pos;
    private Direction direction;
    private int ticks;
    private double progress;
    private boolean started;
    private boolean finished;

    private BlockPos secondaryPos;
    private int secondaryTicks;
    private boolean secondaryHolding;
    private double secondaryProgress;

    private int stopCooldown;

    private long lastStopMs;
    private double delayBalance;

    /** Hotbar slot we have selected for this break, -1 when we are not holding one. */
    private int heldSlot = -1;

    public SpeedMine() {
        super(AddonTemplate.WORLD_CATEGORY, "mf-speed-mine",
            "Packet mines the clicked block at best-tool speed behind a Grim decoy, then rebreaks it.");
    }

    @Override
    public void onActivate() {
        clearMine();
        clearSecondary();
        releaseSlot();
        stopCooldown = 0;
        delayBalance = 0;
        lastStopMs = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && pos != null && started && !finished) abortBreak();

        clearSecondary();
        clearMine();
        releaseSlot();
    }

    @Override
    public String getInfoString() {
        String extra = secondaryPos != null ? " +1" : "";

        if (pos == null) return secondaryPos != null ? "+1" : null;
        if (!started) return "wait" + extra;

        return (finished ? "rebreak" : (int) (Math.min(progress / threshold.get(), 1) * 100) + "%") + extra;
    }

    @EventHandler
    private void onStartBreaking(StartBreakingBlockEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        event.cancel();

        if (isMining(event.blockPos)) return;
        if (!BlockUtils.canBreak(event.blockPos, mc.world.getBlockState(event.blockPos))) return;

        // Start the block behind first, so it gets demoted into the secondary slot and finishes on
        // its own while the clicked block runs as the primary break.
        BlockPos ahead = breakAheadPos(event.blockPos);
        if (ahead != null) startMine(ahead, validFace(ahead));

        startMine(event.blockPos, event.direction);
    }

    /**
     * Traces the look ray through the clicked block and returns the block it continues into, or null
     * when it leaves through an edge or into something that cannot be mined. Straight-on mining exits
     * cleanly through a face; grazing a corner lands too close to the border and is rejected so the
     * second slot is not wasted.
     */
    private BlockPos breakAheadPos(BlockPos target) {
        if (!breakAhead.get() || !doubleBreak.get()) return null;
        if (secondaryPos != null || (pos != null && started && !finished)) return null;

        Vec3d eye = mc.player.getEyePos();
        Vec3d dir = mc.player.getRotationVec(1.0f);
        Box box = targetBox(target);

        double enter = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        Direction exitFace = null;

        for (Direction.Axis axis : Direction.Axis.values()) {
            double d = axis.choose(dir.x, dir.y, dir.z);
            double o = axis.choose(eye.x, eye.y, eye.z);
            double min = axis.choose(box.minX, box.minY, box.minZ);
            double max = axis.choose(box.maxX, box.maxY, box.maxZ);

            if (Math.abs(d) < 1.0E-7) {
                if (o < min || o > max) return null;
                continue;
            }

            double t1 = (min - o) / d;
            double t2 = (max - o) / d;

            if (Math.min(t1, t2) > enter) enter = Math.min(t1, t2);

            if (Math.max(t1, t2) < exit) {
                exit = Math.max(t1, t2);
                exitFace = axisFace(axis, d > 0);
            }
        }

        if (exitFace == null || enter > exit || exit <= 0) return null;

        Vec3d exitPoint = eye.add(dir.multiply(exit));

        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == exitFace.getAxis()) continue;

            double p = axis.choose(exitPoint.x, exitPoint.y, exitPoint.z)
                - axis.choose(target.getX(), target.getY(), target.getZ());

            if (p < BREAK_AHEAD_EDGE || p > 1 - BREAK_AHEAD_EDGE) return null;
        }

        BlockPos ahead = target.offset(exitFace);
        if (!inMineRange(ahead)) return null;

        BlockState state = mc.world.getBlockState(ahead);
        if (state.isAir() || !BlockUtils.canBreak(ahead, state)) return null;

        return ahead;
    }

    private void startMine(BlockPos target, Direction dir) {
        if (pos != null && started && !finished) {
            if (!doubleBreak.get() || secondaryPos != null || !demote()) abortBreak();
        }

        pos = target.toImmutable();
        direction = dir;
        ticks = 0;
        progress = 0;
        started = false;
        finished = false;

        if (stopCooldown == 0 && canBegin()) begin();
    }

    private boolean canBegin() {
        long delay = System.currentTimeMillis() - lastStopMs;
        if (delay >= 275) return true;

        double cost = (300 - delay) * (decoy.get() ? 2 : 1);
        return delayBalance + cost <= 900;
    }

    private void trackStarts(int starts) {
        long delay = System.currentTimeMillis() - lastStopMs;

        for (int i = 0; i < starts; i++) {
            if (delay >= 275) delayBalance *= 0.9;
            else delayBalance += 300 - delay;
        }

        delayBalance = MathHelper.clamp(delayBalance, -1000, 1000);
    }

    private void begin() {
        started = true;
        trackStarts(decoy.get() ? 2 : 1);

        BlockState state = mc.world.getBlockState(pos);

        if (faceMargin(direction, targetBox(pos)) <= 0) direction = validFace(pos);

        holdSlot(bestSlot(state, pos));
        sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, direction);

        if (decoy.get()) {
            BlockPos decoyPos = pos.down(DECOY_Y_OFFSET);
            sendAction(PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, decoyPos, validFace(decoyPos));
        }

        if (swing.get()) mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (stopCooldown > 0) stopCooldown--;

        tickSecondary();

        if (pos == null) return;

        BlockState state = mc.world.getBlockState(pos);

        if (!inMineRange(pos)) {
            if (started && !finished) abortBreak();
            clearMine();
            return;
        }

        if (!started) {
            if (state.isAir() || !BlockUtils.canBreak(pos, state)) {
                clearMine();
                return;
            }

            if (stopCooldown > 0 || !canBegin()) return;

            direction = validFace(pos);
            begin();
            return;
        }

        if (!finished) {
            if (state.isAir()) {
                clearMine();
                return;
            }

            int slot = bestSlot(state, pos);
            double delta = BlockUtils.getBreakDelta(slot, state);

            if (delta <= 0) {
                abortBreak();
                clearMine();
                return;
            }

            ticks++;
            progress = Math.max(ticks - fudgeTicks.get(), 0) * delta;

            if (progress >= threshold.get()) finished = stopBreak(slot);

            return;
        }

        if (!rebreak.get()) {
            clearMine();
            return;
        }

        if (state.isAir()) return;

        stopBreak(bestSlot(state, pos));
    }

    private void tickSecondary() {
        if (secondaryPos == null) return;

        BlockState state = mc.world.getBlockState(secondaryPos);

        if (state.isAir()) {
            clearSecondary();
            return;
        }

        secondaryTicks++;

        int slot = bestSlot(state, secondaryPos);
        double delta = BlockUtils.getBreakDelta(slot, state);

        if (delta <= 0) {
            clearSecondary();
            return;
        }

        int expected = MathHelper.ceil(1.0 / delta) - 1;
        secondaryProgress = Math.min(secondaryTicks * delta, 1);

        if (secondaryTicks > expected + SECONDARY_TIMEOUT) {
            clearSecondary();
            return;
        }

        // Hold the tool for the demoted break only when it is about to land, and never while the
        // primary break still needs the slot.
        if (secondaryTicks >= expected - 1 && !secondaryHolding && (pos == null || finished)) {
            holdSlot(slot);
            secondaryHolding = true;
        }
    }

    private boolean demote() {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;

        // The stop has to go out with the best tool held, or the server rolls the drops for the
        // wrong item.
        holdSlot(bestSlot(state, pos));
        sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, validFace(pos));

        secondaryPos = pos;
        secondaryTicks = ticks;
        secondaryHolding = false;
        secondaryProgress = Math.min(progress, 1);
        return true;
    }

    private boolean stopBreak(int slot) {
        // A swap would break an active latch on a gap or potion: wait it out, the caller retries.
        if (pauseOnEat.get() && mc.player.isUsingItem()) return false;

        if (!holdSlot(slot)) return false;

        stopCooldown = breakDelay.get();
        sendAction(PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, validFace(pos));

        if (swing.get()) mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        return true;
    }

    private void abortBreak() {
        sendAction(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN);
    }

    /** Selects {@code slot} and remembers it, so the break runs with the best tool held. */
    private boolean holdSlot(int slot) {
        if (slot < 0 || slot > 8) return false;
        if (heldSlot == slot) return true;

        releaseSlot();

        if (!InvUtils.swap(slot, true)) return false;

        heldSlot = slot;
        return true;
    }

    private void releaseSlot() {
        if (heldSlot < 0) return;

        InvUtils.swapBack();
        heldSlot = -1;
    }

    /** The direction pointing out of {@code axis} in the positive or negative sense. */
    private Direction axisFace(Direction.Axis axis, boolean positive) {
        return switch (axis) {
            case X -> positive ? Direction.EAST : Direction.WEST;
            case Y -> positive ? Direction.UP : Direction.DOWN;
            case Z -> positive ? Direction.SOUTH : Direction.NORTH;
        };
    }

    private Direction validFace(BlockPos target) {
        Box box = targetBox(target);

        Direction best = Direction.UP;
        double bestMargin = -Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            double margin = faceMargin(dir, box);

            if (margin > bestMargin) {
                bestMargin = margin;
                best = dir;
            }
        }

        return best;
    }

    private double faceMargin(Direction dir, Box box) {
        Vec3d feet = mc.player.getEntityPos();
        return feetMargin(dir, box, feet);
    }

    private double feetMargin(Direction dir, Box box, Vec3d feet) {
        return switch (dir) {
            case UP -> feet.y + GRIM_MAX_EYE - box.maxY;
            case DOWN -> box.minY - (feet.y + GRIM_MIN_EYE);
            case EAST -> feet.x - box.maxX;
            case WEST -> box.minX - feet.x;
            case SOUTH -> feet.z - box.maxZ;
            case NORTH -> box.minZ - feet.z;
        };
    }

    private Box targetBox(BlockPos target) {
        VoxelShape shape = mc.world.getBlockState(target).getOutlineShape(mc.world, target);
        return shape.isEmpty() ? new Box(target) : shape.getBoundingBox().offset(target);
    }

    private int bestSlot(BlockState state, BlockPos target) {
        int best = mc.player.getInventory().getSelectedSlot();
        double bestDelta = BlockUtils.getBreakDelta(best, state);

        for (int i = 0; i < 9; i++) {
            double delta = BlockUtils.getBreakDelta(i, state);

            if (delta > bestDelta) {
                best = i;
                bestDelta = delta;
            }
        }

        return best;
    }

    private boolean inMineRange(BlockPos target) {
        return mc.player.getEyePos().distanceTo(Vec3d.ofCenter(target)) <= range.get();
    }

    public boolean isMining(BlockPos target) {
        return target.equals(pos) || target.equals(secondaryPos);
    }

    /**
     * Collects the blocks this miner is partway through, for modules that plan around them - the
     * crystal aura ignores a block it is already breaking when it works out explosion exposure.
     * Same contract as Homovore's {@code SpeedMineModule#collectMiningPositions}.
     *
     * @param out           receives the positions
     * @param minCompletion how far through the break a block has to be, 0..1
     */
    public void collectMiningPositions(java.util.Collection<BlockPos> out, double minCompletion) {
        if (!isActive()) return;
        if (pos != null && progress >= minCompletion) out.add(pos);
        if (secondaryPos != null && secondaryProgress >= minCompletion) out.add(secondaryPos);
    }

    /**
     * Asks the miner to break a block without a click, the way Homovore's {@code MineApi} does, so
     * other modules (Self Mine Out) can clear a block silently.
     *
     * @return true when the block is now being mined, or already was
     */
    public boolean requestBreak(BlockPos target) {
        if (!isActive() || mc.player == null || mc.world == null) return false;
        if (mc.player.isCreative() || mc.player.isSpectator()) return false;
        if (isMining(target)) return true;
        if (!inMineRange(target)) return false;

        BlockState state = mc.world.getBlockState(target);
        if (state.isAir() || !BlockUtils.canBreak(target, state)) return false;

        if (pos != null && !finished) {
            if (!started) return false;
            if (!doubleBreak.get() || secondaryPos != null) return false;
        }

        startMine(target, validFace(target));
        return true;
    }

    private void sendAction(PlayerActionC2SPacket.Action action, BlockPos target, Direction face) {
        if (mc.getNetworkHandler() == null) return;

        if (action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) lastStopMs = System.currentTimeMillis();

        mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(action, target, face));
    }

    private void clearMine() {
        pos = null;
        direction = null;
        ticks = 0;
        progress = 0;
        started = false;
        finished = false;
    }

    private void clearSecondary() {
        if (secondaryHolding) releaseSlot();

        secondaryPos = null;
        secondaryTicks = 0;
        secondaryHolding = false;
        secondaryProgress = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null || !render.get()) return;

        if (pos != null && started) {
            drawBlock(event, pos, primaryColor.get(), finished ? 1 : Math.min(progress / threshold.get(), 1));
        }

        if (secondaryPos != null) drawBlock(event, secondaryPos, sideColor.get(), secondaryProgress);
    }

    private void drawBlock(Render3DEvent event, BlockPos target, SettingColor side, double progress) {
        if (mc.world.getBlockState(target).isAir()) return;

        float t = (float) MathHelper.clamp(progress, 0, 1);

        double cx = target.getX() + 0.5;
        double cy = target.getY() + 0.5;
        double cz = target.getZ() + 0.5;
        double half = 0.5 * t;

        Box box = new Box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);

        event.renderer.box(box, side, lineColor.get(), ShapeMode.Both, 0);
    }
}
