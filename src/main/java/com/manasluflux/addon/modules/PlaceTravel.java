package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Travel with a block line: holds forward + sprint, jumps the moment you touch the ground
 * (no vanilla jump cooldown) and lays a straight line of blocks a few blocks ahead of you.
 *
 * <p>The line is placed on a <b>fixed level</b> - the block level you last stood on - so it stays
 * flat while you are bunny hopping instead of stepping up and down with your jump arc. Distance
 * ahead and the height of the line are both settings.
 *
 * <p>"Floor" continues the level you are walking on, which is what keeps a hole in the nether roof
 * from dropping you. "Above head" puts the blocks over you instead - note that a line at head
 * height is inside your jump arc, so with bunny hopping on it stops you at the first block.
 */
public class PlaceTravel extends Module {
    /** Placing farther than this is out of reach and would only produce rejected packets. */
    private static final double REACH = 4.5;
    /** Upper bound on the scan, so a dialled-up distance can't stall the tick. */
    private static final int MAX_DISTANCE = 8;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("How many blocks ahead the line is placed. Each step is one block further in front of you; anything beyond your block reach is skipped.")
        .defaultValue(2)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<LineHeight> height = sgGeneral.add(new EnumSetting.Builder<LineHeight>()
        .name("height")
        .description("Where the line sits relative to the block level you are running on. Floor keeps the path under you solid; Head and Above head build the line over you.")
        .defaultValue(LineHeight.Floor)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks of the line are placed per tick.")
        .defaultValue(2)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("Which blocks may be used for the line. Only blocks you actually carry are used.")
        .defaultValue(Blocks.NETHERRACK, Blocks.COBBLESTONE, Blocks.DIRT)
        .build()
    );

    private final Setting<Boolean> sprint = sgGeneral.add(new BoolSetting.Builder()
        .name("sprint")
        .description("Keeps you sprinting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> jump = sgGeneral.add(new BoolSetting.Builder()
        .name("jump")
        .description("Jumps the instant you land - the vanilla jump cooldown is skipped, so the hops never stall.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotates towards the blocks it places.")
        .defaultValue(true)
        .build()
    );

    /** Block level the player last stood on, so the line stays flat while jumping. */
    private int groundY;
    private boolean hasGroundY;

    public PlaceTravel() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "place-travel", "Sprints and bunny-hops forward with no jump delay while laying a straight line of blocks ahead of you - built for running the nether roof.");
    }

    @Override
    public void onActivate() {
        hasGroundY = false;
    }

    @Override
    public void onDeactivate() {
        mc.options.forwardKey.setPressed(false);
    }

    @Override
    public String getInfoString() {
        return distance.get() + " ahead";
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // Keep the reference level stable: only the blocks you actually stood on refresh it.
        if (mc.player.isOnGround()) {
            groundY = mc.player.getBlockPos().getY();
            hasGroundY = true;
        }

        travel();

        if (hasGroundY) place();
    }

    private void travel() {
        mc.options.forwardKey.setPressed(true);

        if (sprint.get()) mc.player.setSprinting(true);

        // Calling jump() directly skips LivingEntity's 10 tick jumping cooldown (that cooldown only
        // gates the key-press path), so a hop starts on the very first tick you are back on ground.
        if (jump.get() && mc.player.isOnGround() && !mc.player.isSneaking()) mc.player.jump();
    }

    private void place() {
        if (blocksPerTick.get() <= 0) return;

        FindItemResult item = InvUtils.findInHotbar(stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            return blocks.get().contains(blockItem.getBlock());
        });
        if (!item.found()) return;

        double yaw = Math.toRadians(mc.player.getYaw());
        double dirX = -Math.sin(yaw);
        double dirZ = Math.cos(yaw);

        BlockPos origin = mc.player.getBlockPos();
        int steps = Math.min(distance.get(), MAX_DISTANCE);
        int placed = 0;

        for (int step = 1; step <= steps && placed < blocksPerTick.get(); step++) {
            int x = origin.getX() + (int) Math.round(dirX * step);
            int z = origin.getZ() + (int) Math.round(dirZ * step);

            BlockPos line = new BlockPos(x, groundY + height.get().offset, z);
            if (!inReach(line) || !BlockUtils.canPlace(line, true)) continue;

            if (BlockUtils.place(line, item, rotate.get(), 50, true, true, true)) placed++;
        }
    }

    private boolean inReach(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        return mc.player.getEyePos().squaredDistanceTo(center) <= REACH * REACH;
    }

    public enum LineHeight {
        /** The level of the block you are running on - keeps the path solid ahead of you. */
        Floor(-1),
        /** The level your feet are at. */
        Feet(0),
        /** Head level. */
        Head(1),
        /** One block above your head. */
        AboveHead(2);

        private final int offset;

        LineHeight(int offset) {
            this.offset = offset;
        }

        @Override
        public String toString() {
            return switch (this) {
                case Floor -> "Floor";
                case Feet -> "Feet";
                case Head -> "Head";
                case AboveHead -> "Above head";
            };
        }
    }
}
