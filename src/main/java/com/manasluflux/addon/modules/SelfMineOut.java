package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
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
 * <p>Needs Speed Mine enabled - it is the miner; this module only decides what to ask it for. It says
 * so once instead of silently doing nothing.
 */
public class SelfMineOut extends Module {
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

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Say what it is mining, and warn once when Speed Mine is not enabled.")
        .defaultValue(true)
        .build()
    );

    private int cooldown;
    private boolean warned;

    public SelfMineOut() {
        super(AddonTemplate.BYPASS_CATEGORY, "universal-self-mine-out",
            "When you are phased into blocks, mines out the block in your head and/or your feet using Speed Mine.");
    }

    @Override
    public void onActivate() {
        cooldown = 0;
        warned = false;
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
            if (!warned && chatFeedback.get()) {
                warning("Speed Mine is not enabled - it is the miner this module drives.");
                warned = true;
            }
            return;
        }

        warned = false;

        BlockPos target = nextTarget();
        if (target == null) return;

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
}
