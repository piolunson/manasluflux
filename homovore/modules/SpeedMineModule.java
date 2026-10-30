package dev.leonetic.features.modules.world;

import dev.leonetic.Homovore;
import dev.leonetic.event.impl.entity.player.PreTickEvent;
import dev.leonetic.event.impl.network.AttackBlockEvent;
import dev.leonetic.event.impl.render.Render3DEvent;
import dev.leonetic.event.system.Subscribe;
import dev.leonetic.features.modules.Module;
import dev.leonetic.features.settings.Setting;
import dev.leonetic.util.InteractionUtil;
import dev.leonetic.util.inventory.Result;
import dev.leonetic.util.inventory.ResultType;
import dev.leonetic.util.inventory.SwapMode;
import dev.leonetic.util.inventory.SwapPriority;
import dev.leonetic.util.render.RenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.Color;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class SpeedMineModule extends Module implements MineApi {

    private static final int DECOY_Y_OFFSET = 2000;

    private static final double GRIM_MIN_EYE = 0.4;
    private static final double GRIM_MAX_EYE = 1.62;

    /** ticks past the predicted server completion before the secondary is written off. */
    private static final int SECONDARY_TIMEOUT = 10;

    /** how far inside a face the BreakAhead ray has to exit for it to count as straight-through. */
    private static final double BREAK_AHEAD_EDGE = 0.05;

    private final Setting<Double> threshold = num("Threshold", 0.7, 0.1, 1.0);
    private final Setting<Integer> fudgeTicks = num("FudgeTicks", 1, 0, 5);
    private final Setting<Integer> breakDelay = num("BreakDelay", 6, 0, 10);
    private final Setting<Boolean> pauseOnEat = bool("PauseOnEat", true);
    private final Setting<Boolean> decoy = bool("GrimDecoy", true);
    private final Setting<Boolean> doubleBreak = bool("DoubleBreak", true);
    private final Setting<Boolean> breakAhead = bool("BreakAhead", false);
    private final Setting<Boolean> rebreak = bool("Rebreak", true);
    private final Setting<Boolean> swing = bool("Swing", true);
    private final Setting<Double> range = num("Range", 5.0, 1.0, 7.0);

    private final Setting<Boolean> render = bool("Render", true).setPage("Render");
    private final Setting<Float> lineWidth = num("LineWidth", 2.0f, 0.5f, 5.0f).setPage("Render");
    private final Setting<Color> lineColor = color("LineColor", 255, 255, 255, 150).setPage("Render");
    private final Setting<Color> sideColor = color("SideColor", 255, 255, 255, 40).setPage("Render");
    private final Setting<Color> primaryColor = color("PrimaryColor", 255, 180, 255, 60).setPage("Render");

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

    private BlockPos rebreakHoldPos;
    private int rebreakHoldTicks;

    public interface MineFinishListener { void onMineFinish(BlockPos pos); }

    private final CopyOnWriteArrayList<MineFinishListener> finishListeners = new CopyOnWriteArrayList<>();

    public void addFinishListener(MineFinishListener l) { finishListeners.addIfAbsent(l); }

    public void removeFinishListener(MineFinishListener l) { finishListeners.remove(l); }

    private void fireFinish(BlockPos target) {
        for (MineFinishListener l : finishListeners) l.onMineFinish(target);
    }

    public SpeedMineModule() {
        super("SpeedMine", "Packet mines the clicked block at best-tool speed behind a Grim decoy, then rebreaks it.", Category.WORLD);

        breakAhead.setVisibility(v -> doubleBreak.getValue());
        lineWidth.setVisibility(v -> render.getValue());
        lineColor.setVisibility(v -> render.getValue());
        sideColor.setVisibility(v -> render.getValue());
        primaryColor.setVisibility(v -> render.getValue());
    }

    @Override
    public void onDisable() {
        if (!nullCheck() && pos != null && started && !finished) {
            abortBreak();
        }
        clearSecondary();
        clearMine();
        rebreakHoldPos = null;
        rebreakHoldTicks = 0;
    }

    @Override
    public String getDisplayInfo() {
        String extra = secondaryPos != null ? " +1" : "";
        if (pos == null) return secondaryPos != null ? "+1" : null;
        if (!started) return "wait" + extra;
        return (finished ? "rebreak" : (int) (Math.min(progress / threshold.getValue(), 1) * 100) + "%") + extra;
    }

    @Subscribe
    private void onAttackBlock(AttackBlockEvent event) {
        if (nullCheck()) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;

        event.cancel();

        if (isMining(event.getPos())) return;
        if (!InteractionUtil.canBreak(event.getPos(), event.getState())) return;

        // start the block behind first so it gets demoted into the secondary slot and
        // finishes on its own while the clicked block runs as the primary break.
        BlockPos ahead = breakAheadPos(event.getPos());
        if (ahead != null) startMine(ahead, validFace(ahead));

        startMine(event.getPos(), event.getDirection());
    }

    /**
     * Traces the look ray through the clicked block and returns the block it continues into,
     * or null if it leaves through an edge / into something we can't mine. Straight-on mining
     * (any of the 8 compass directions) exits cleanly through a face, while grazing a corner
     * lands too close to the face border and is rejected so we don't waste the second slot.
     */
    private BlockPos breakAheadPos(BlockPos target) {
        if (!breakAhead.getValue() || !doubleBreak.getValue()) return null;
        // only when both slots are free, so we never disturb a break already in progress
        if (secondaryPos != null || (pos != null && started && !finished)) return null;

        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = mc.player.getLookAngle();
        AABB box = new AABB(target);

        double tEnter = Double.NEGATIVE_INFINITY;
        double tExit = Double.POSITIVE_INFINITY;
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
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            if (near > tEnter) tEnter = near;
            if (far < tExit) {
                tExit = far;
                exitFace = Direction.fromAxisAndDirection(axis,
                        d > 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            }
        }
        if (exitFace == null || tEnter > tExit || tExit <= 0) return null;

        // reject exits that hug the border of the face, those are corner grazes
        Vec3 exit = eye.add(dir.scale(tExit));
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis == exitFace.getAxis()) continue;
            double p = axis.choose(exit.x, exit.y, exit.z) - axis.choose(target.getX(), target.getY(), target.getZ());
            if (p < BREAK_AHEAD_EDGE || p > 1 - BREAK_AHEAD_EDGE) return null;
        }

        BlockPos ahead = target.relative(exitFace);
        if (!inMineRange(ahead)) return null;
        BlockState state = mc.level.getBlockState(ahead);
        if (state.isAir() || !InteractionUtil.canBreak(ahead, state)) return null;
        return ahead;
    }

    private void startMine(BlockPos target, Direction dir) {
        if (pos != null && started && !finished) {
            if (!doubleBreak.getValue() || secondaryPos != null || !demote()) {
                abortBreak();
            }
        }

        pos = target.immutable();
        direction = dir;
        ticks = 0;
        progress = 0;
        started = false;
        finished = false;

        if (stopCooldown == 0 && canBegin()) begin();
    }

    private boolean canBegin() {
        long delay = System.currentTimeMillis() - lastStopMs;
        if (delay >= 275) return true; // grim decays the balance instead
        double cost = (300 - delay) * (decoy.getValue() ? 2 : 1);
        return delayBalance + cost <= 900;
    }

    private void trackStarts(int starts) {
        long delay = System.currentTimeMillis() - lastStopMs;
        for (int i = 0; i < starts; i++) {
            if (delay >= 275) delayBalance *= 0.9;
            else delayBalance += 300 - delay;
        }
        delayBalance = Mth.clamp(delayBalance, -1000, 1000);
    }

    @Override
    public boolean isAvailable() {
        return isEnabled() && !nullCheck() && !mc.player.isCreative() && !mc.player.isSpectator();
    }

    @Override
    public boolean requestBreak(BlockPos target) {
        if (!isAvailable()) return false;
        if (isMining(target)) return true;
        if (!inMineRange(target)) return false;
        BlockState state = mc.level.getBlockState(target);
        if (state.isAir() || !InteractionUtil.canBreak(target, state)) return false;
        if (pos != null && !finished) {
            if (!started) return false;
            if (!doubleBreak.getValue() || secondaryPos != null) return false;
        }
        startMine(target, validFace(target));
        return true;
    }

    @Override
    public boolean isMining(BlockPos target) {
        return target.equals(pos) || target.equals(secondaryPos);
    }

    @Override
    public BlockPos getRebreakPos() {
        return finished && rebreak.getValue() ? pos : null;
    }

    @Override
    public boolean hasFreePrimary() {
        return pos == null || finished;
    }

    @Override
    public boolean hasFreeSecondary() {
        return doubleBreak.getValue() && secondaryPos == null;
    }

    @Override
    public boolean inMineRange(BlockPos target) {
        return mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(target)) <= range.getValue();
    }

    /** The primary mine target, finished or not. */
    public BlockPos getPrimaryPos() {
        return pos;
    }

    /** The secondary (double break) target, or null. */
    public BlockPos getSecondaryPos() {
        return secondaryPos;
    }

    /**
     * Suppresses the rebreak of {@code target} for {@code ticks} ticks, so a module can drop
     * something into the hole (AutoMine's GlassPush) before we take the block back out.
     */
    public void holdRebreak(BlockPos target, int ticks) {
        rebreakHoldPos = target != null ? target.immutable() : null;
        rebreakHoldTicks = target != null ? ticks : 0;
    }

    public void collectMiningPositions(Set<BlockPos> out, double minProgress) {
        if (pos != null && started && (finished || progress >= minProgress)) out.add(pos);
        if (secondaryPos != null && !nullCheck()) {
            BlockState state = mc.level.getBlockState(secondaryPos);
            if (!state.isAir()) {
                double delta = InteractionUtil.getBreakDelta(
                        mc.player.getInventory().getItem(bestSlot(state, secondaryPos)), state, secondaryPos);
                if (delta > 0 && secondaryTicks * delta >= minProgress) out.add(secondaryPos);
            }
        }
    }

    private boolean demote() {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        if (!stopBreak(bestSlot(state, pos), false)) {
            sendAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, validFace(pos));
        }
        secondaryPos = pos;
        secondaryTicks = ticks;
        secondaryHolding = false;
        secondaryProgress = Math.min(progress, 1);
        return true;
    }

    private void begin() {
        started = true;
        trackStarts(decoy.getValue() ? 2 : 1);
        if (faceMargin(direction, targetBox(pos)) <= 0) {
            direction = validFace(pos);
        }
        sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, direction);
        if (decoy.getValue()) {
            BlockPos decoyPos = pos.below(DECOY_Y_OFFSET);
            sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, decoyPos, validFace(decoyPos));
        }
        if (swing.getValue()) {
            mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }
    }

    @Subscribe
    private void onTick(PreTickEvent event) {
        if (nullCheck()) return;

        if (stopCooldown > 0) stopCooldown--;
        if (rebreakHoldTicks > 0) rebreakHoldTicks--;

        tickSecondary();

        if (pos == null) return;

        if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > range.getValue()) {
            if (started && !finished) abortBreak();
            clearMine();
            return;
        }

        BlockState state = mc.level.getBlockState(pos);

        if (!started) {
            if (state.isAir() || !InteractionUtil.canBreak(pos, state)) {
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
            double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, pos);
            if (delta <= 0) {
                abortBreak();
                clearMine();
                return;
            }

            ticks++;
            progress = Math.max(ticks - fudgeTicks.getValue(), 0) * delta;
            if (progress >= threshold.getValue()) {
                finished = stopBreak(slot);
                if (finished) fireFinish(pos);
            }
            return;
        }

        if (!rebreak.getValue()) {
            clearMine();
            return;
        }
        if (state.isAir()) return;
        if (rebreakHoldTicks > 0 && pos.equals(rebreakHoldPos)) return;
        stopBreak(bestSlot(state, pos));
    }

    private void tickSecondary() {
        if (secondaryPos == null) return;

        BlockState state = mc.level.getBlockState(secondaryPos);
        if (state.isAir()) {
            fireFinish(secondaryPos);
            clearSecondary();
            return;
        }

        secondaryTicks++;
        int slot = bestSlot(state, secondaryPos);
        double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(slot), state, secondaryPos);
        if (delta <= 0) {
            clearSecondary();
            return;
        }

        int expected = Mth.ceil(1.0 / delta) - 1;
        secondaryProgress = Math.min(secondaryTicks * delta, 1);

        if (secondaryTicks > expected + SECONDARY_TIMEOUT) {
            clearSecondary();
            return;
        }

        if (secondaryTicks >= expected - 1 && !secondaryHolding) {
            Result result = new Result(slot, mc.player.getInventory().getItem(slot), ResultType.HOTBAR);
            if (!result.holding() && !Homovore.swapManager.isLatched() && Homovore.swapManager.latch(result)) {
                secondaryHolding = true;
            }
        }
    }

    private void clearSecondary() {
        if (secondaryHolding) Homovore.swapManager.release();
        secondaryPos = null;
        secondaryTicks = 0;
        secondaryHolding = false;
        secondaryProgress = 0;
    }

    @Subscribe
    private void onRender(Render3DEvent event) {
        if (nullCheck() || !render.getValue()) return;
        if (pos != null && started) drawBlock(event, pos, primaryColor.getValue(),
                finished ? 1 : Math.min(progress / threshold.getValue(), 1));
        if (secondaryPos != null) drawBlock(event, secondaryPos, sideColor.getValue(), secondaryProgress);
    }

    private void drawBlock(Render3DEvent event, BlockPos target, Color side, double progress) {
        if (mc.level.getBlockState(target).isAir()) return;

        float t = (float) Mth.clamp(progress, 0, 1);
        double cx = target.getX() + 0.5;
        double cy = target.getY() + 0.5;
        double cz = target.getZ() + 0.5;
        double half = 0.5 * t;
        AABB box = new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
        RenderUtil.drawBoxFilled(event.getMatrix(), box, side);
        RenderUtil.drawBox(event.getMatrix(), box, lineColor.getValue(), lineWidth.getValue());
    }

    private boolean stopBreak(int slot) {
        return stopBreak(slot, true);
    }

    private boolean stopBreak(int slot, boolean cooldown) {
        // A SILENT swap would break an active latch (SwordGap eating, or our own secondary hold) —
        // wait it out instead; the caller just retries next tick.
        if (Homovore.swapManager.isLatched() && (pauseOnEat.getValue() || secondaryHolding)) return false;

        ItemStack stack = mc.player.getInventory().getItem(slot);
        return Homovore.swapManager.withSwap(new Result(slot, stack, ResultType.HOTBAR), SwapMode.SILENT,
                SwapPriority.MINING, () -> {
            if (cooldown) stopCooldown = breakDelay.getValue();
            sendAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, validFace(pos));
            if (swing.getValue()) {
                mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
            }
        });
    }

    private void abortBreak() {
        sendAction(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN);
    }

    private Direction validFace(BlockPos target) {
        AABB box = targetBox(target);

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

    private double faceMargin(Direction dir, AABB box) {
        Vec3 now = mc.player.position();
        Vec3 prev = new Vec3(mc.player.xo, mc.player.yo, mc.player.zo);
        return Math.min(feetMargin(dir, box, now), feetMargin(dir, box, prev));
    }

    private double feetMargin(Direction dir, AABB box, Vec3 feet) {
        return switch (dir) {
            case UP -> feet.y + GRIM_MAX_EYE - box.maxY;
            case DOWN -> box.minY - (feet.y + GRIM_MIN_EYE);
            case EAST -> feet.x - box.maxX;
            case WEST -> box.minX - feet.x;
            case SOUTH -> feet.z - box.maxZ;
            case NORTH -> box.minZ - feet.z;
        };
    }

    private AABB targetBox(BlockPos target) {
        VoxelShape shape = mc.level.getBlockState(target).getShape(mc.level, target);
        return shape.isEmpty() ? new AABB(target) : shape.bounds().move(target);
    }

    private int bestSlot(BlockState state, BlockPos target) {
        int best = mc.player.getInventory().getSelectedSlot();
        double bestDelta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(best), state, target);
        for (int i = 0; i < 9; i++) {
            double delta = InteractionUtil.getBreakDelta(mc.player.getInventory().getItem(i), state, target);
            if (delta > bestDelta) {
                best = i;
                bestDelta = delta;
            }
        }
        return best;
    }

    private void sendAction(ServerboundPlayerActionPacket.Action action, BlockPos target, Direction face) {
        if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
            lastStopMs = System.currentTimeMillis();
        }
        mc.getConnection().send(new ServerboundPlayerActionPacket(action, target, face));
    }

    private void clearMine() {
        pos = null;
        direction = null;
        ticks = 0;
        progress = 0;
        started = false;
        finished = false;
    }
}
