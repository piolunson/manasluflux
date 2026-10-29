package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.entity.EntityPosition;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * The phase-fill trick in one module: vclip straight down (or up) a few blocks, fill the
 * blocks your body was standing in before the clip, then wait for the server's anticheat to
 * rubberband you - if nothing arrives within {@code timeout}, vclip back to where you started.
 *
 * <p>Why the fill: the server only knows about the position packet you sent, so the space you
 * vacated looks like plain air. Placing your own blocks into it makes the server see the spot it
 * still believes you are in as solid, which is what forces the correction teleport. Those two
 * placements are ordinary block places from your hotbar, retried every tick until they land.
 *
 * <p>While waiting, {@code hold-position} keeps re-applying the clipped position, because the
 * client itself pushes an entity that ends up inside a solid block back out - without the hold
 * you would pop back to the surface before the server ever answers.
 *
 * <p>One-shot like Pearl Phase: enabling the module runs the whole sequence and it switches
 * itself off afterwards.
 */
public class VClipPhaseFill extends Module {
    /** Slack for "the client is still where we clipped it". */
    private static final double HOLD_EPSILON = 0.05;
    /** A sync packet this close to the clipped spot is the server agreeing with us, not a rubberband. */
    private static final double ACCEPT_EPSILON = 0.5;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("How many blocks to vclip.")
        .defaultValue(3)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<ClipDirection> direction = sgGeneral.add(new EnumSetting.Builder<ClipDirection>()
        .name("direction")
        .description("Where to vclip. Down clips into the floor, up into the ceiling.")
        .defaultValue(ClipDirection.Down)
        .build()
    );

    private final Setting<Integer> timeout = sgGeneral.add(new IntSetting.Builder()
        .name("timeout")
        .description("How many ticks to wait for the server's rubberband before vclipping back. 20 ticks = 1 second.")
        .defaultValue(20)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> holdPosition = sgGeneral.add(new BoolSetting.Builder()
        .name("hold-position")
        .description("Keeps you at the clipped spot while waiting, so the client can't push you back out of the block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<List<Block>> fillBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("fill-blocks")
        .description("Which blocks may be placed into the space you left behind. Only blocks you carry in the hotbar are used.")
        .defaultValue(Blocks.COBBLESTONE, Blocks.NETHERRACK, Blocks.DIRT)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many of the vacated blocks are filled per tick. 0 turns the fill off.")
        .defaultValue(2)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotates towards the blocks it fills back in.")
        .defaultValue(true)
        .build()
    );

    private enum Stage {
        Idle,
        Waiting
    }

    private Stage stage = Stage.Idle;
    private Vec3d origin;
    private Vec3d clipped;
    /** Blocks the body stood in before the clip - feet and head - still to be filled. */
    private final List<BlockPos> vacated = new ArrayList<>();
    private int waited;
    private volatile boolean rubberbanded;
    private boolean warnedNoBlock;

    public VClipPhaseFill() {
        super(AddonTemplate.BYPASS_CATEGORY, "vclip-phasefill",
            "Vclips a few blocks, fills the space you left behind and waits for the server's rubberband - if none comes within the timeout it vclips you back.");
    }

    @Override
    public void onActivate() {
        stage = Stage.Idle;
        rubberbanded = false;
        waited = 0;
        warnedNoBlock = false;
        vacated.clear();

        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        if (distance.get() <= 0) {
            error("Distance is 0 - set how many blocks to vclip.");
            toggle();
            return;
        }

        origin = mc.player.getEntityPos();
        clipped = new Vec3d(
            origin.x,
            origin.y + (double) direction.get().sign * distance.get(),
            origin.z
        );

        BlockPos feet = BlockPos.ofFloored(origin);
        vacated.add(feet);
        vacated.add(feet.up());

        stage = Stage.Waiting;
        clipTo(clipped);

        info("Vclipped %d block(s) %s - filling %d block(s) behind you.", distance.get(), direction.get(), vacated.size());
    }

    @Override
    public void onDeactivate() {
        stage = Stage.Idle;
        vacated.clear();
        waited = 0;
        rubberbanded = false;
    }

    @Override
    public String getInfoString() {
        if (stage == Stage.Waiting) return waited + "/" + timeout.get() + " ticks";
        return distance.get() + " " + direction.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (stage != Stage.Waiting) return;

        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        // The server already pulled us back: nothing left to do.
        if (rubberbanded) {
            stage = Stage.Idle;
            info("The server rubberbanded you - done.");
            toggle();
            return;
        }

        fill();

        if (holdPosition.get() && mc.player.getEntityPos().squaredDistanceTo(clipped) > HOLD_EPSILON * HOLD_EPSILON) {
            clipTo(clipped);
        }

        if (++waited >= timeout.get()) {
            clipTo(origin);
            stage = Stage.Idle;
            info("No rubberband in %d tick(s) - vclipped back.", timeout.get());
            toggle();
        }
    }

    /** Fills the blocks that are still standing empty behind us, newest first in list order. */
    private void fill() {
        if (vacated.isEmpty() || blocksPerTick.get() <= 0) return;

        FindItemResult item = InvUtils.findInHotbar(stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            return fillBlocks.get().contains(blockItem.getBlock());
        });

        if (!item.found()) {
            if (!warnedNoBlock) {
                warnedNoBlock = true;
                warning("No fill block in your hotbar - the space behind you stays empty.");
            }
            return;
        }

        int placed = 0;
        for (int i = 0; i < vacated.size() && placed < blocksPerTick.get(); i++) {
            BlockPos pos = vacated.get(i);

            // Someone (or our own prediction) already put a block there.
            if (!mc.world.getBlockState(pos).isAir()) {
                vacated.remove(i--);
                continue;
            }

            if (BlockUtils.place(pos, item, rotate.get(), 50)) {
                vacated.remove(i--);
                placed++;
            }
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (stage != Stage.Waiting) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket packet)) return;

        // Position-sync teleport: the server moved us. Coming back to within ACCEPT_EPSILON of the
        // clipped spot just means it accepted the vclip, anything else is the rubberband.
        EntityPosition change = packet.change();
        Vec3d serverPos = change.position();
        if (clipped != null && serverPos.squaredDistanceTo(clipped) < ACCEPT_EPSILON * ACCEPT_EPSILON) return;

        rubberbanded = true;
    }

    private void clipTo(Vec3d pos) {
        if (mc.player == null) return;

        mc.player.setPos(pos.x, pos.y, pos.z);
        mc.player.setVelocity(0, 0, 0);
    }

    public enum ClipDirection {
        Down(-1),
        Up(1);

        private final int sign;

        ClipDirection(int sign) {
            this.sign = sign;
        }

        @Override
        public String toString() {
            return this == Down ? "Down" : "Up";
        }
    }
}
