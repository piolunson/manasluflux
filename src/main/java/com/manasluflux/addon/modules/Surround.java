package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Step;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of Sydney's <b>Surround</b> ({@code me.aidan.sydney.modules.impl.combat.SurroundModule},
 * sydney-client/Sydney-Legacy, GPL-3.0 - the original is vendored in
 * <a href="../../../../../../../../sydney/README.md">sydney/</a>, together with the hole geometry it
 * takes its feet positions from).
 *
 * <p>The behaviour is Sydney's, not a rewrite: the feet positions come from
 * {@code HoleUtils#getFeetPositions} (the block you stand in, its four sides, and - with
 * {@code extension} - every extra column an entity is standing in, with {@code floor} adding the
 * block below each), each one is filtered by {@code range} and by {@code isPlaceable}, and the block
 * item used is the hardest one in the hotbar, placed in groups of {@code limit} with {@code delay}
 * ticks between groups. One spot is filled per tick, because Meteor carries a single rotation per
 * tick; the spots are picked in Sydney's order, a crystal that spawned into one first. When a target
 * position has nothing to click against, the block under it is placed first and the target is retried
 * on the next tick. Directions are
 * picked the way Sydney picks them: the first side whose neighbour is solid, and with
 * {@code strict-direction} only sides that actually face you.
 *
 * <p>{@code timing} keeps its two modes: {@code Vanilla} only replaces a spot on the next placement
 * group, {@code Sequential} rebuilds it as soon as a crystal spawns into it, which is what makes the
 * surround hold against crystal spam.
 *
 * <p>Adapted to Meteor: Sydney's own events map onto {@code TickEvent.Pre} and
 * {@code PacketEvent.Receive}, item switching onto {@link InvUtils#swap(int, boolean)}, rotation onto
 * {@link Rotations}, the click onto Meteor's sequenced {@code useItemOn} through
 * {@link BlockUtils#interact}, and {@code render} onto Meteor's renderer. Two of Sydney's details
 * cannot be reproduced on 1.21.11 and are dropped (see the vendored README): the {@code HitboxDesync}
 * interaction, and the shift/sprint suppression packets around an interactable block, because the
 * shift-press packet no longer exists.
 */
public class Surround extends Module {
    public enum SwitchMode {
        None,
        Silent,
        Normal
    }

    public enum Timing {
        Vanilla,
        Sequential
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgDisable = settings.createGroup("Disable");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ------------------------------------------------------------------ general

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch")
        .description("How the block item is taken from your hotbar.")
        .defaultValue(SwitchMode.Silent)
        .build()
    );

    private final Setting<Timing> timing = sgGeneral.add(new EnumSetting.Builder<Timing>()
        .name("timing")
        .description("Vanilla only replaces a spot in the next group, Sequential rebuilds it the moment a crystal spawns into it.")
        .defaultValue(Timing.Sequential)
        .build()
    );

    private final Setting<Integer> limit = sgGeneral.add(new IntSetting.Builder()
        .name("limit")
        .description("The maximum number of blocks placed each group (one block per tick).")
        .defaultValue(4)
        .range(1, 20)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between each group of placements.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("The maximum range at which blocks are placed.")
        .defaultValue(5.0)
        .range(0.0, 12.0)
        .sliderRange(0.0, 8.0)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Whether or not you should rotate when you place blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> strictDirection = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-direction")
        .description("Only places using directions that face you.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> crystalDestruction = sgGeneral.add(new BoolSetting.Builder()
        .name("crystal-destruction")
        .description("Destroys any crystals that interfere with block placement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> center = sgGeneral.add(new BoolSetting.Builder()
        .name("center")
        .description("Puts you in the centre of the block when you surround.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> floor = sgGeneral.add(new BoolSetting.Builder()
        .name("floor")
        .description("Places blocks under your feet as well.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> extension = sgGeneral.add(new BoolSetting.Builder()
        .name("extension")
        .description("Extends the surround if there are entities obstructing block placement.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> whileEating = sgGeneral.add(new BoolSetting.Builder()
        .name("while-eating")
        .description("Places blocks normally while eating.")
        .defaultValue(true)
        .build()
    );

    // ------------------------------------------------------------------ disable

    private final Setting<Boolean> selfDisable = sgDisable.add(new BoolSetting.Builder()
        .name("self-disable")
        .description("Turns the module off once it has finished placing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> jumpDisable = sgDisable.add(new BoolSetting.Builder()
        .name("jump-disable")
        .description("Turns the module off whenever your Y level changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> itemDisable = sgDisable.add(new BoolSetting.Builder()
        .name("item-disable")
        .description("Turns the module off whenever you run out of blocks to place with.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stepToggle = sgDisable.add(new BoolSetting.Builder()
        .name("step-toggle")
        .description("Turns Meteor's Step off when you surround.")
        .defaultValue(false)
        .build()
    );

    // ------------------------------------------------------------------ render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Renders the positions it is placing at.")
        .defaultValue(true)
        .build()
    );

    private Set<BlockPos> targets = new HashSet<>();
    private final Map<Integer, BlockPos> pendingCrystals = new ConcurrentHashMap<>();

    private BlockPos lastPosition;
    private int ticks;
    private int groupPlaced;
    private boolean placing;
    private int placingTicks;
    private final Map<BlockPos, Long> attempts = new HashMap<>();

    /** How long a spot is left alone after a placement was sent into it, in milliseconds. */
    private static final long ATTEMPT_COOLDOWN_MS = 100L;

    /** Ticks a queued placement is given to reach the network before it is considered lost. */
    private static final int PLACE_TIMEOUT_TICKS = 4;

    public Surround() {
        super(AddonTemplate.COMBAT_CATEGORY, "mf-surround",
            "Places blocks around your feet to stop crystal damage (port of Sydney's Surround).");
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null) return;

        lastPosition = flooredPosition(mc.player);
        targets.clear();
        pendingCrystals.clear();
        attempts.clear();
        ticks = 0;
        groupPlaced = 0;
        placing = false;
        placingTicks = 0;

        Step step = Modules.get().get(Step.class);
        if (stepToggle.get() && step != null && step.isActive()) step.toggle();

        if (center.get()) {
            mc.player.setPosition(lastPosition.getX() + 0.5, lastPosition.getY(), lastPosition.getZ() + 0.5);
        }
    }

    @Override
    public void onDeactivate() {
        lastPosition = null;
        targets.clear();
        pendingCrystals.clear();
        attempts.clear();
        ticks = 0;
        groupPlaced = 0;
        placing = false;
        placingTicks = 0;
    }

    @Override
    public String getInfoString() {
        return String.valueOf(targets.size());
    }

    // ------------------------------------------------------------------ tick

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        if (jumpDisable.get()) {
            if (mc.player.fallDistance > 2.0f) {
                toggle();
                return;
            }

            if (lastPosition == null || lastPosition.getY() != flooredPosition(mc.player).getY()) {
                toggle();
                return;
            }
        }

        if (!whileEating.get() && mc.player.isUsingItem()) return;

        // Meteor's Rotations handles one rotation per tick and sends a look packet for every extra
        // rotation queued behind it, so a placement is started only once the previous one is out.
        if (placing) {
            if (++placingTicks <= PLACE_TIMEOUT_TICKS) return;
            placing = false;
        }

        // Sydney's group: `limit` placements, then `delay` idle ticks before the next group.
        if (groupPlaced >= limit.get()) {
            if (ticks < delay.get()) {
                ticks++;
                return;
            }

            ticks = 0;
            groupPlaced = 0;
        }

        if (switchMode.get() == SwitchMode.None && !(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            if (itemDisable.get()) {
                error("You are currently not holding any blocks.");
                toggle();
            }

            targets.clear();
            return;
        }

        int slot = switchMode.get() == SwitchMode.None ? -1 : findHardestBlock(0, 8);

        if (switchMode.get() != SwitchMode.None && slot == -1) {
            if (itemDisable.get()) {
                error("No blocks could be found in your hotbar.");
                toggle();
            }

            targets.clear();
            return;
        }

        calculateTargets();

        Placement placement = pickPlacement();

        if (placement == null) {
            if (selfDisable.get() && !hasWorkLeft()) toggle();
            return;
        }

        placeBlock(placement.position(), placement.direction(), placement.crystalId(), slot);
        groupPlaced++;
    }

    private record Placement(BlockPos position, Direction direction, int crystalId) { }

    /**
     * Picks the single spot to fill this tick. Sydney filled up to {@code limit} spots per tick, but
     * Meteor can only carry one rotation per tick, so the spots are filled one after another in the
     * same order: a crystal that spawned into a surround spot first ({@code timing = Sequential}),
     * then the feet positions themselves.
     */
    private Placement pickPlacement() {
        if (timing.get() == Timing.Sequential && !pendingCrystals.isEmpty()) {
            for (Map.Entry<Integer, BlockPos> entry : new ArrayList<>(pendingCrystals.entrySet())) {
                BlockPos position = entry.getValue();

                if (!(mc.world.getEntityById(entry.getKey()) instanceof EndCrystalEntity crystal)) {
                    pendingCrystals.remove(entry.getKey());
                    continue;
                }

                if (!targets.contains(position)) {
                    pendingCrystals.remove(entry.getKey());
                    continue;
                }

                if (!inRange(position) || !isPlaceable(position) || recentlyAttempted(position)) continue;

                Direction direction = getDirection(position, strictDirection.get());
                if (direction == null) continue;

                return new Placement(position, direction, crystal.getId());
            }
        }

        for (BlockPos position : targets) {
            if (!inRange(position) || !isPlaceable(position) || recentlyAttempted(position)) continue;

            Direction direction = getDirection(position, strictDirection.get());

            if (direction == null) {
                // Nothing to click against: place the block underneath and retry the spot next tick.
                BlockPos support = position.down();
                if (!inRange(support) || !isPlaceable(support) || recentlyAttempted(support)) continue;

                Direction supportDirection = getDirection(support, strictDirection.get());
                if (supportDirection == null) continue;

                return new Placement(support, supportDirection, -1);
            }

            return new Placement(position, direction, -1);
        }

        return null;
    }

    /** Whether any spot is still open, so {@code self-disable} does not fire while one is pending. */
    private boolean hasWorkLeft() {
        for (BlockPos position : targets) {
            if (inRange(position) && isPlaceable(position)) return true;
        }

        return false;
    }

    private boolean inRange(BlockPos position) {
        return mc.player.squaredDistanceTo(Vec3d.ofCenter(position)) <= MathHelper.square(range.get());
    }

    /**
     * A spot that was placed into a moment ago is skipped until the server has had time to confirm
     * it, otherwise a block the client has not received yet is placed into again on the next tick.
     */
    private boolean recentlyAttempted(BlockPos position) {
        Long last = attempts.get(position);
        return last != null && System.currentTimeMillis() - last < ATTEMPT_COOLDOWN_MS;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (timing.get() != Timing.Sequential) return;

        if (!(event.packet instanceof EntitySpawnS2CPacket packet)) return;
        if (packet.getEntityType() != EntityType.END_CRYSTAL) return;

        Vec3d spawn = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
        Box spawnBox = EntityType.END_CRYSTAL.getDimensions().getBoxAt(spawn);

        for (BlockPos position : targets) {
            if (new Box(position).intersects(spawnBox)) {
                pendingCrystals.put(packet.getEntityId(), position);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ placement

    private void placeBlock(BlockPos position, Direction direction, int crystalId, int slot) {
        Vec3d hitPos = Vec3d.ofCenter(position);
        BlockPos clicked = position;

        if (direction != null) {
            clicked = position.offset(direction);
            hitPos = hitPos.add(direction.getOffsetX() / 2.0, direction.getOffsetY() / 2.0, direction.getOffsetZ() / 2.0);
        }

        Direction clickSide = direction == null ? Direction.UP : direction;
        BlockHitResult hit = new BlockHitResult(hitPos, clickSide.getOpposite(), clicked, false);

        boolean swap = switchMode.get() != SwitchMode.None
            && slot != -1
            && mc.player.getInventory().getSelectedSlot() != slot;

        Runnable action = () -> {
            placing = false;
            placingTicks = 0;
            attempts.put(position, System.currentTimeMillis());

            if (crystalId != -1) pendingCrystals.remove(crystalId);

            // The swap belongs inside this callback: Meteor sends the rotation with the next
            // movement packet, after this tick's hotbar state would already be back to normal.
            if (swap) InvUtils.swap(slot, switchMode.get() == SwitchMode.Silent);

            if (crystalDestruction.get()) destroyCrystals(position);

            BlockUtils.interact(hit, Hand.MAIN_HAND, true);

            if (swap && switchMode.get() == SwitchMode.Silent) InvUtils.swapBack();
        };

        if (!rotate.get()) {
            action.run();
            return;
        }

        placing = true;
        placingTicks = 0;

        float[] angles = rotationsTo(hitPos);
        Rotations.rotate(angles[0], angles[1], 50, action);
    }

    /**
     * Sydney's {@code WorldUtils.destroyCrystals}: attack the first crystal inside the spot so the
     * placement is not rejected. Only a crystal the client still holds an id for is attacked.
     */
    private void destroyCrystals(BlockPos position) {
        for (Entity entity : mc.world.getOtherEntities(null, new Box(position))) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (mc.world.getEntityById(crystal.getId()) != crystal) continue;

            attackCrystal(crystal);
            break;
        }
    }

    private void attackCrystal(EndCrystalEntity crystal) {
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (mc.world == null || mc.world.getEntityById(crystal.getId()) != crystal) return;

        mc.player.networkHandler.sendPacket(
            PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    /** Sydney's {@code RotationUtils.getRotations}: yaw/pitch to a point, with its ±2° jitter. */
    private float[] rotationsTo(Vec3d target) {
        Vec3d eye = mc.player.getEyePos();

        double deltaX = target.x - eye.x;
        double deltaY = (target.y - eye.y) * -1.0;
        double deltaZ = target.z - eye.z;
        double distance = MathHelper.sqrt((float) (deltaX * deltaX + deltaZ * deltaZ));

        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);
        float pitch = (float) MathHelper.clamp(MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(deltaY, distance))), -90f, 90f);

        return new float[]{
            yaw + (((float) Math.random() - 0.5f) * 4),
            pitch + (((float) Math.random() - 0.5f) * 4)
        };
    }

    // ------------------------------------------------------------------ geometry

    /** Sydney's {@code HoleUtils.getFeetPositions(target, extension, floor, false)}. */
    private void calculateTargets() {
        Set<BlockPos> positions = new HashSet<>();
        Set<BlockPos> blacklist = new HashSet<>();

        BlockPos feetPos = flooredPosition(mc.player);
        blacklist.add(feetPos);

        if (extension.get()) {
            for (Direction direction : Direction.values()) {
                if (direction.getAxis().isVertical()) continue;

                BlockPos offset = feetPos.offset(direction);
                List<PlayerEntity> collisions = collisions(offset);
                if (collisions.isEmpty()) continue;

                for (PlayerEntity player : collisions) {
                    Box box = player.getBoundingBox();
                    for (int x = (int) Math.floor(box.minX); x < Math.ceil(box.maxX); x++) {
                        for (int z = (int) Math.floor(box.minZ); z < Math.ceil(box.maxZ); z++) {
                            blacklist.add(new BlockPos(x, feetPos.getY(), z));
                        }
                    }
                }
            }
        }

        for (BlockPos position : blacklist) {
            if (floor.get()) positions.add(position.down());

            for (Direction direction : Direction.values()) {
                if (!direction.getAxis().isHorizontal()) continue;

                BlockPos offset = position.offset(direction);
                if (!blacklist.contains(offset)) positions.add(offset);
            }
        }

        targets = positions;
    }

    /** Sydney's {@code PositionUtils.getFlooredPosition}: the block a player standing on 0.8 is already in. */
    private static BlockPos flooredPosition(Entity entity) {
        double y = entity.getY();
        double floored = Math.floor(y);
        int blockY = MathHelper.floor(floored + ((y - floored) > 0.8 ? 1.0 : 0.0));
        return new BlockPos(MathHelper.floor(entity.getX()), blockY, MathHelper.floor(entity.getZ()));
    }

    private List<PlayerEntity> collisions(BlockPos position) {
        List<PlayerEntity> out = new ArrayList<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == null || player.isDead()) continue;
            if (player.getBoundingBox().intersects(new Box(position))) out.add(player);
        }
        return out;
    }

    /** Sydney's {@code WorldUtils.isPlaceable(position, false)}. */
    private boolean isPlaceable(BlockPos position) {
        if (!mc.world.getBlockState(position).isReplaceable()) return false;

        for (Entity entity : mc.world.getOtherEntities(null, new Box(position))) {
            if (entity instanceof EndCrystalEntity || entity instanceof ExperienceOrbEntity || entity instanceof ItemEntity) continue;
            return false;
        }

        return true;
    }

    /** Sydney's {@code WorldUtils.getDirection}: the first side whose neighbour is solid. */
    private Direction getDirection(BlockPos position, boolean strict) {
        List<Direction> strictDirections = strict ? getStrictDirections(mc.player.getEyePos(), Vec3d.ofCenter(position)) : List.of();

        for (Direction direction : Direction.values()) {
            BlockPos offset = position.offset(direction);
            if (strict && !strictDirections.contains(direction.getOpposite())) continue;
            if (mc.world.getBlockState(offset).isReplaceable()) continue;

            return direction;
        }

        return null;
    }

    /** Sydney's {@code WorldUtils.getStrictDirections}: the sides of a block that face you. */
    private static List<Direction> getStrictDirections(Vec3d eyePos, Vec3d blockPos) {
        List<Direction> directions = new ArrayList<>();

        double differenceX = eyePos.getX() - blockPos.getX();
        double differenceY = eyePos.getY() - blockPos.getY();
        double differenceZ = eyePos.getZ() - blockPos.getZ();

        if (differenceY > 0.5) directions.add(Direction.UP);
        else if (differenceY < -0.5) directions.add(Direction.DOWN);
        else {
            directions.add(Direction.UP);
            directions.add(Direction.DOWN);
        }

        if (differenceX > 0.5) directions.add(Direction.EAST);
        else if (differenceX < -0.5) directions.add(Direction.WEST);
        else {
            directions.add(Direction.EAST);
            directions.add(Direction.WEST);
        }

        if (differenceZ > 0.5) directions.add(Direction.SOUTH);
        else if (differenceZ < -0.5) directions.add(Direction.NORTH);
        else {
            directions.add(Direction.SOUTH);
            directions.add(Direction.NORTH);
        }

        return directions;
    }

    /** Sydney's {@code InventoryUtils.findHardestBlock}: the block item with the highest hardness. */
    private int findHardestBlock(int start, int end) {
        float bestHardness = -1;
        int bestSlot = -1;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof BlockItem item)) continue;

            float hardness = item.getBlock().getHardness();
            if (hardness == -1) return i;

            if (hardness > bestHardness) {
                bestHardness = hardness;
                bestSlot = i;
            }
        }

        return bestSlot;
    }

    // ------------------------------------------------------------------ render

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!render.get() || mc.player == null || targets.isEmpty()) return;

        Color side = new Color(130, 80, 255, 45);
        Color line = new Color(130, 80, 255, 255);

        for (BlockPos position : targets) {
            Box box = new Box(position);
            event.renderer.box(box, side, line, ShapeMode.Both, 0);
        }
    }
}
