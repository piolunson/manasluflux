package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

/**
 * Mines you back out when a phase leaves you standing inside blocks.
 *
 * <p>It looks at the two blocks your body is actually in - the one your <b>head</b> is inside and the
 * one your <b>feet</b> are inside - and hands whichever is solid to Speed Mine, so the block is
 * cleared at tool speed instead of by holding left click inside a wall. Both are separate toggles,
 * because a head-only hole is enough to breathe in some spots and a feet hole is what stops the
 * client pushing you back.
 *
 * <p>The blocks are found from your bounding box, not from a guess about phase direction: the head
 * block is the one containing your eye height, the feet block the one containing your feet, and a
 * block only counts when it has collision. That means it also works after a pearl or a vclip dumped
 * you inside terrain, and it does nothing when you are standing in the open.
 *
 * <p>It also takes the tool itself: before a block is handed over, the best tool for that block is put
 * in your hand - silently by default, which is one selected-slot packet so your hotbar keeps the slot
 * it is on - and the slot is handed back once you are out of the blocks again. Without that the mine
 * would run with whatever happened to be selected.
 *
 * <p>Needs Speed Mine enabled - it is the miner; this module only decides what to ask it for, and holds
 * the tool for it. It says so once instead of silently doing nothing.
 */
public class SelfMineOut extends Module {
    public enum ToolSwitch {
        Silent,
        Visible,
        None
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> head = sgGeneral.add(new BoolSetting.Builder()
        .name("head")
        .description("Mine the block your head is inside.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> feet = sgGeneral.add(new BoolSetting.Builder()
        .name("feet")
        .description("Mine the block your feet are inside.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between attempts, so it does not re-ask every tick while a block is breaking.")
        .defaultValue(2)
        .range(0, 40)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<ToolSwitch> toolSwitch = sgGeneral.add(new EnumSetting.Builder<ToolSwitch>()
        .name("tool-switch")
        .description("Hold the best tool for the block while it is mined out. Silent only tells the server, so your hotbar stays where it is; Visible also selects the slot.")
        .defaultValue(ToolSwitch.Silent)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Say what it is mining, and warn once when Speed Mine is not enabled.")
        .defaultValue(true)
        .build()
    );

    private int cooldown;
    private boolean warned;
    /** True while the tool we picked is the one the server believes is in your hand. */
    private boolean holdingTool;
    /** The slot the client showed when a Visible switch started. */
    private int restoreSlot = -1;

    public SelfMineOut() {
        super(AddonTemplate.BYPASS_CATEGORY, "universal-self-mine-out",
            "When you are phased into blocks, mines out the block in your head and/or your feet using Speed Mine.");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
        warned = false;
        holdingTool = false;
        restoreSlot = -1;
    }

    @Override
    public void onDeactivate() {
        releaseTool();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        SpeedMine miner = Modules.get().get(SpeedMine.class);

        if (miner == null || !miner.isActive()) {
            releaseTool();

            if (!warned && chatFeedback.get()) {
                warning("Speed Mine is not enabled - it is the miner this module drives.");
                warned = true;
            }
            return;
        }

        warned = false;

        BlockPos target = nextTarget();

        if (target == null) {
            // Out of the blocks again - give the hand back.
            releaseTool();
            return;
        }

        // The pickaxe goes in before the block is asked for, or the mine runs with the wrong item.
        ensureTool(mc.world.getBlockState(target));

        if (miner.requestBreak(target)) {
            cooldown = delay.get();

            if (chatFeedback.get()) info("Mining out %s (%s).", target.toShortString(), headOrFeet(target));
        }
    }

    /** The first solid block of the head/feet pair that needs clearing, head first. */
    private BlockPos nextTarget() {
        Box box = mc.player.getBoundingBox();
        double x = (box.minX + box.maxX) / 2.0;
        double z = (box.minZ + box.maxZ) / 2.0;

        if (head.get()) {
            BlockPos headPos = BlockPos.ofFloored(x, mc.player.getEyePos().y - 0.1, z);

            if (needsMining(headPos)) return headPos;
        }

        if (feet.get()) {
            BlockPos feetPos = BlockPos.ofFloored(x, box.minY + 0.1, z);

            if (needsMining(feetPos)) return feetPos;
        }

        return null;
    }

    /** Puts the best tool for {@code state} in the server's view of your hand, once per hold. */
    private void ensureTool(BlockState state) {
        if (holdingTool || toolSwitch.get() == ToolSwitch.None) return;
        if (mc.player.networkHandler == null) return;

        int slot = bestToolSlot(state);
        if (slot == -1 || slot == mc.player.getInventory().getSelectedSlot()) return;

        if (toolSwitch.get() == ToolSwitch.Silent) {
            // Server-side only: the client keeps its current visible slot.
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } else {
            restoreSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.getInventory().setSelectedSlot(slot);
        }

        holdingTool = true;
    }

    /** Gives the hand back once the blocks are gone. Safe to call at any time. */
    private void releaseTool() {
        if (!holdingTool) {
            restoreSlot = -1;
            return;
        }

        holdingTool = false;

        if (mc.player != null && mc.player.networkHandler != null) {
            if (restoreSlot != -1) {
                // Visible switch: the client slot moved, so move it back.
                mc.player.getInventory().setSelectedSlot(restoreSlot);
            } else {
                // Tell the server we are back on the slot the client actually shows.
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
            }
        }

        restoreSlot = -1;
    }

    /** The hotbar slot with the fastest break delta for {@code state}, or -1 when none can break it. */
    private int bestToolSlot(BlockState state) {
        int best = -1;
        double bestDelta = 0;

        for (int i = 0; i < 9; i++) {
            double delta = BlockUtils.getBreakDelta(i, state);

            if (delta > bestDelta) {
                bestDelta = delta;
                best = i;
            }
        }

        return best;
    }

    private boolean needsMining(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;

        // A block with no collision (a torch, a sign, water) is not what is holding you in.
        return !state.getCollisionShape(mc.world, pos).isEmpty();
    }

    private String headOrFeet(BlockPos pos) {
        BlockPos headPos = BlockPos.ofFloored(mc.player.getX(), mc.player.getEyePos().y - 0.1, mc.player.getZ());
        return pos.equals(headPos) ? "head" : "feet";
    }

    @Override
    public String getInfoString() {
        return (head.get() ? "H" : "") + (feet.get() ? "F" : "");
    }

    /** Whether the tool switch currently holds the hand, for the module's own debugging. */
    public boolean isHoldingTool() {
        return holdingTool;
    }
}
