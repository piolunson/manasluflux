package com.manasluflux.addon.homovore;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Port of Homovore's {@code PlacementManager} (leonetics/homovore-public, AGPL-3.0).
 *
 * <p>Modules queue a position and the item to put there; the manager batches every queued position
 * that wants the same item, takes <b>one</b> silent hotbar swap to that item and sends the whole
 * batch from the mainhand inside it. Two limits keep the burst plausible: a sliding
 * {@value #MAX_PER_WINDOW}-per-{@value #WINDOW_MS}ms window across all placements, and a per-block
 * cooldown so a position the server has not confirmed yet is not spammed.
 *
 * <p>The original drives the swap through Homovore's {@code SwapManager} and the use packet through
 * its own sequence-predicting mixin; this port uses Meteor's {@link InvUtils#swap(int, boolean)} and
 * {@link BlockUtils#interact(BlockHitResult, Hand, boolean)}, which send the same sequenced packet.
 */
public class PlacementManager {
    private static final long WINDOW_MS = 300L;
    private static final int MAX_PER_WINDOW = 9;
    private static final long PER_BLOCK_COOLDOWN_MS = 50L;

    /** How a placement aims: not at all, server-side only, or by really turning the camera. */
    public enum Look {
        Off,
        Silent,
        Visible
    }

    private Look look = Look.Silent;

    public interface Listener {
        void onBlockUpdate(BlockPos pos, boolean nowAir);
    }

    private final Map<BlockPos, Item> queue = new LinkedHashMap<>();
    private final Deque<Long> window = new ArrayDeque<>();
    private final Map<BlockPos, Long> cooldowns = new HashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<Listener> listeners() {
        return listeners;
    }

    /** Drops everything queued. Called when the module that owns this manager is turned off. */
    public void clear() {
        queue.clear();
        cooldowns.clear();
        window.clear();
    }

    public void setLook(Look look) {
        this.look = look;
    }

    // ------------------------------------------------------------------ queue

    /**
     * Queues {@code item} to be placed at {@code pos}. Returns false when the position is on
     * cooldown or already queued, so callers can tell whether they now own the placement.
     */
    public boolean enqueue(BlockPos pos, Item item) {
        if (pos == null || item == null) return false;
        if (onCooldown(pos)) return false;
        return queue.putIfAbsent(pos.toImmutable(), item) == null;
    }

    /** Books a placement this manager did not send, so it still counts against the rate window. */
    public void notePlacement(BlockPos pos) {
        long now = System.currentTimeMillis();
        cooldowns.put(pos.toImmutable(), now);
        window.addLast(now);
    }

    /**
     * Turns towards {@code target} and runs {@code action}. Silent only tells the server, visible
     * really moves the camera; off leaves the aim alone. Reach, not the camera, decides whether a
     * placement happens, so an offline look no longer blocks it.
     */
    private void aimingAt(Vec3d target, Runnable action) {
        if (look == Look.Off || mc.player == null) {
            action.run();
            return;
        }

        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) MathHelper.wrapDegrees(-Math.toDegrees(Math.atan2(dy, flat)));

        if (look == Look.Silent) {
            Rotations.rotate(yaw, pitch, 60, action);
            return;
        }

        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        action.run();
    }

    private boolean onCooldown(BlockPos pos) {
        Long last = cooldowns.get(pos);
        return last != null && System.currentTimeMillis() - last < PER_BLOCK_COOLDOWN_MS;
    }

    private int budget() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        while (!window.isEmpty() && window.peekFirst() < cutoff) window.pollFirst();
        return MAX_PER_WINDOW - window.size();
    }

    // --------------------------------------------------------------- flushing

    /** Sends what the rate window allows right now. Safe to call straight after queueing. */
    public void flushQueue() {
        if (mc.player == null || mc.world == null || queue.isEmpty()) return;
        if (shouldDefer()) return;

        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(t -> now - t >= PER_BLOCK_COOLDOWN_MS);

        int budget = budget();
        if (budget <= 0) return;

        // One item per flush: the batch rides a single swap, so mixing items would need one swap
        // each and blow the tick's silent budget. The rest keeps its place for the next flush.
        Item item = queue.values().iterator().next();

        List<BlockPos> batch = new ArrayList<>();
        List<BlockPos> stale = new ArrayList<>();
        for (Map.Entry<BlockPos, Item> entry : queue.entrySet()) {
            if (entry.getValue() != item) continue;
            BlockPos pos = entry.getKey();
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                stale.add(pos);
                continue;
            }
            if (!canPlace(pos)) continue;
            if (cooldowns.containsKey(pos)) continue;
            batch.add(pos);
            if (batch.size() >= budget) break;
        }
        stale.forEach(queue::remove);
        if (batch.isEmpty()) return;

        if (place(batch, item)) batch.forEach(queue::remove);
    }

    /** Places {@code item} at every position in {@code batch} inside one silent swap. */
    private boolean place(List<BlockPos> batch, Item item) {
        FindItemResult found = InvUtils.findInHotbar(item);
        if (!found.found()) return false;

        InvUtils.swap(found.slot(), true);
        batch.forEach(this::sendPlace);
        InvUtils.swapBack();

        long stamp = System.currentTimeMillis();
        for (BlockPos pos : batch) {
            cooldowns.put(pos, stamp);
            window.addLast(stamp);
        }
        return true;
    }

    /** Sends one {@code UseItemOn} for {@code pos}. Must run inside a swap holding the block item. */
    private void sendPlace(BlockPos pos) {
        Direction side = BlockUtils.getPlaceSide(pos);
        BlockPos clicked;
        Vec3d hitPos;
        Direction hitSide;

        if (side == null) {
            // Nothing solid to click against: aim at the face of the target cell itself.
            hitSide = Direction.UP;
            clicked = pos;
            hitPos = Vec3d.ofCenter(pos);
        } else {
            clicked = pos.offset(side);
            hitSide = side.getOpposite();
            hitPos = Vec3d.ofCenter(pos).add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);
        }

        BlockHitResult hit = new BlockHitResult(hitPos, hitSide, clicked, false);
        aimingAt(hitPos, () -> BlockUtils.interact(hit, Hand.MAIN_HAND, false));
    }

    // --------------------------------------------------------------- crystals

    /**
     * Places an end crystal on {@code base} inside a silent swap. {@code trustBase} skips the
     * obsidian/bedrock check for a base we are about to place ourselves.
     */
    public boolean placeCrystal(BlockPos base, boolean trustBase) {
        if (mc.player == null || mc.world == null) return false;
        if (shouldDefer()) return false;

        BlockHitResult hit = crystalHit(base, trustBase);
        if (hit == null) return false;

        FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystal.found()) return false;

        aimingAt(hit.getPos(), () -> {
            InvUtils.swap(crystal.slot(), true);
            BlockUtils.interact(hit, Hand.MAIN_HAND, false);
            InvUtils.swapBack();
        });
        return true;
    }

    private BlockHitResult crystalHit(BlockPos base, boolean trustBase) {
        if (!trustBase) {
            BlockState baseState = mc.world.getBlockState(base);
            if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) return null;
        }

        BlockPos airPos = base.up();
        BlockState airState = mc.world.getBlockState(airPos);
        if (!airState.isAir() && !(airState.isOf(Blocks.FIRE) && mc.world.getRegistryKey().equals(World.END))) {
            return null;
        }

        for (Entity e : mc.world.getOtherEntities(null, new Box(airPos))) {
            if (e instanceof ItemEntity) continue;
            if (e instanceof EndCrystalEntity crystal
                && (crystal.age < 5 || crystal.getBlockPos().equals(airPos))) continue;
            return null;
        }

        Vec3d eye = mc.player.getEyePos();
        Vec3d baseCenter = Vec3d.ofCenter(base);
        double dx = eye.x - baseCenter.x, dy = eye.y - baseCenter.y, dz = eye.z - baseCenter.z;
        double absX = Math.abs(dx), absY = Math.abs(dy), absZ = Math.abs(dz);

        Direction clickFace;
        if (absY >= absX && absY >= absZ) clickFace = dy > 0 ? Direction.UP : Direction.DOWN;
        else if (absX >= absZ) clickFace = dx > 0 ? Direction.EAST : Direction.WEST;
        else clickFace = dz > 0 ? Direction.SOUTH : Direction.NORTH;

        // Bias the hit vector toward where we are standing, so it stays inside the face we can
        // actually see rather than sitting dead centre.
        Vec3d playerPos = mc.player.getEntityPos();
        double offX = MathHelper.clamp(playerPos.x - Math.floor(playerPos.x), 0.2, 0.8) - 0.5;
        double offY = MathHelper.clamp(playerPos.y - Math.floor(playerPos.y), 0.2, 0.8) - 0.5;
        double offZ = MathHelper.clamp(playerPos.z - Math.floor(playerPos.z), 0.2, 0.8) - 0.5;

        Vec3d hitVec = switch (clickFace) {
            case UP, DOWN -> baseCenter.add(offX, clickFace == Direction.UP ? 0.5 : -0.5, offZ);
            case NORTH, SOUTH -> baseCenter.add(offX, offY, clickFace == Direction.SOUTH ? 0.5 : -0.5);
            case EAST, WEST -> baseCenter.add(clickFace == Direction.EAST ? 0.5 : -0.5, offY, offZ);
        };

        return new BlockHitResult(hitVec, clickFace, base, false);
    }

    // --------------------------------------------------------------- geometry

    /** Homovore's placement validity: inside the world, replaceable, in reach, not inside us. */
    private boolean canPlace(BlockPos pos) {
        if (mc.player == null || mc.world == null) return false;
        if (mc.world.isOutOfHeightLimit(pos)) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > 5.154) return false;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return false;
        return noEntityCollision(pos);
    }

    private boolean noEntityCollision(BlockPos pos) {
        Box box = new Box(pos);
        for (Entity e : mc.world.getOtherEntities(mc.player, box)) {
            if (e instanceof ItemEntity || e instanceof EndCrystalEntity) continue;
            return false;
        }
        return true;
    }

    /**
     * Whether an action that needs the mainhand should stand down: the mainhand is mid-use already.
     * Homovore also defers to its swap manager's latch here, which this port does not have.
     */
    private boolean shouldDefer() {
        if (mc.player == null) return true;
        return mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND;
    }
}
