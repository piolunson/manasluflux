package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Looks at TNT -> ignites it automatically. No key press, no holding.
 * Silently switches to flint & steel / fire charge server-side, sends the use,
 * and switches the server-side slot back - your hotbar never visibly moves.
 *
 * Click Through Walls: ignores line of sight - ignites any TNT in reach even if walls
 * or other blocks are between you and it, by sending the use packet directly.
 *
 * Redstone Block: when no igniter is in the inventory, silently places a redstone block
 * next to the TNT instead - redstone power primes TNT in vanilla.
 *
 * Mine Redstone Block: the placed redstone block is packet-mined again during the TNT's
 * 4-second fuse with a silent pickaxe swap, so the dust lands back in your inventory.
 */
public class InstantTnt extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> silentSwitch = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-switch")
        .description("Switches to the igniter server-side only, so your hotbar never visibly moves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clickThroughWalls = sgGeneral.add(new BoolSetting.Builder()
        .name("click-through-walls")
        .description("Ignores line of sight - ignites TNT behind walls and around corners, as long as it is in reach.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> ctwRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("ctw-range")
        .description("How far away TNT can be ignited with click through walls. Servers only accept interactions within ~4.5 blocks.")
        .defaultValue(4.5)
        .range(2.0, 8.0)
        .sliderRange(2.0, 6.0)
        .visible(clickThroughWalls::get)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Ticks between ignitions.")
        .defaultValue(8)
        .range(1, 40)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<Boolean> redstoneBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("redstone-block")
        .description("When no flint & steel or fire charge is in the inventory, silently places a redstone block next to the TNT instead - redstone power primes TNT.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> mineRedstoneBlock = sgGeneral.add(new BoolSetting.Builder()
        .name("mine-redstone-block")
        .description("After igniting with a redstone block, packet-mines that redstone block during the fuse with a silent hotbar-pickaxe swap - the dust lands back in your inventory.")
        .defaultValue(true)
        .visible(redstoneBlock::get)
        .build()
    );

    private int cooldownTicks;
    private boolean warnedNoIgniter;
    private boolean warnedNoRedstone;
    private boolean warnedNoPickaxe;

    // packet-mine state for the placed redstone block
    private BlockPos miningPos;
    private int miningTicks;
    private int miningRestoreSlot = -1;
    private int swingCounter;
    private ItemStack miningPick;

    public InstantTnt() {
        super(AddonTemplate.CATEGORY, "instant-tnt", "Automatically ignites TNT just by looking at it (or through walls with click through walls) - silent server-side switch to your flint & steel, or a redstone block fallback that is mined back during the fuse.");
    }

    private boolean isPickaxe(Item item) {
        return item == Items.WOODEN_PICKAXE || item == Items.COPPER_PICKAXE || item == Items.STONE_PICKAXE
            || item == Items.GOLDEN_PICKAXE || item == Items.IRON_PICKAXE || item == Items.DIAMOND_PICKAXE
            || item == Items.NETHERITE_PICKAXE;
    }

    private boolean isIgniter(Item item) {
        return item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE;
    }

    private int findIgniterSlot() {
        for (int i = 0; i < 9; i++) {
            if (isIgniter(mc.player.getInventory().getItem(i).getItem())) return i;
        }
        return -1;
    }

    private BlockHitResult lookingAtTnt() {
        if (mc.hitResult == null || !(mc.hitResult instanceof BlockHitResult bhr)) return null;
        BlockPos pos = bhr.getBlockPos();
        if (mc.level == null || !(mc.level.getBlockState(pos).getBlock() instanceof TntBlock)) return null;
        if (!mc.player.isWithinBlockInteractionRange(pos, mc.player.blockInteractionRange())) return null;
        return bhr;
    }

    /**
     * Finds the nearest TNT in reach, ignoring line of sight.
     */
    private BlockHitResult findTntThroughWalls(double range) {
        Vec3 eye = mc.player.getEyePosition();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        int r = (int) Math.ceil(range);
        BlockPos eyePos = BlockPos.containing(eye);

        for (BlockPos pos : BlockPos.betweenClosed(
            eyePos.offset(-r, -r, -r),
            eyePos.offset(r, r, r)
        )) {
            BlockState state = mc.level.getBlockState(pos);
            if (!(state.getBlock() instanceof TntBlock)) continue;

            Vec3 center = Vec3.atCenterOf(pos);
            double dist = eye.distanceTo(center);
            if (dist > range || dist >= bestDist) continue;

            best = pos.immutable();
            bestDist = dist;
        }

        if (best == null) return null;

        // Aim at the top face center - a valid-looking hit for the server.
        Vec3 hitLoc = Vec3.atCenterOf(best).add(0, 0.5, 0);
        return new BlockHitResult(hitLoc, Direction.UP, best, false);
    }

    private void ignite(BlockHitResult hit) {
        int realSlot = mc.player.getInventory().getSelectedSlot();
        int igniterSlot = findIgniterSlot();

        if (igniterSlot == -1) {
            if (redstoneBlock.get()) {
                BlockPos placed = igniteWithRedstone(hit.getBlockPos());
                if (placed != null) {
                    cooldownTicks = cooldown.get();
                    if (mineRedstoneBlock.get()) startMining(placed);
                }
                return;
            }

            if (!warnedNoIgniter) {
                warning("No flint & steel or fire charge in the hotbar.");
                warnedNoIgniter = true;
            }
            return;
        }
        warnedNoIgniter = false;
        warnedNoRedstone = false;

        boolean swap = silentSwitch.get() && igniterSlot != realSlot;
        if (swap) mc.player.connection.send(new ServerboundSetCarriedItemPacket(igniterSlot));

        // Server-side "press": sends the use packet with proper block-prediction sequencing.
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);

        if (swap) mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));

        cooldownTicks = cooldown.get();
    }

    /**
     * Places a redstone block next to the TNT - redstone power primes TNT, no igniter needed.
     */
    private BlockPos igniteWithRedstone(BlockPos tnt) {
        FindItemResult item = InvUtils.find(Items.REDSTONE_BLOCK);
        if (!item.found()) {
            if (!warnedNoRedstone) {
                warning("No flint & steel, fire charge or redstone block found.");
                warnedNoRedstone = true;
            }
            return null;
        }

        for (Direction direction : Direction.values()) {
            BlockPos pos = tnt.relative(direction);
            if (!mc.level.getBlockState(pos).canBeReplaced()) continue;
            if (!mc.player.isWithinBlockInteractionRange(pos, mc.player.blockInteractionRange())) continue;

            if (BlockUtils.place(pos, item, false, 0, true, true, true)) {
                warnedNoRedstone = false;
                return pos;
            }
        }

        if (!warnedNoRedstone) {
            warning("Could not place a redstone block next to the TNT (no free spot).");
            warnedNoRedstone = true;
        }
        return null;
    }

    /**
     * Starts a silent packet mine of the redstone block: server-side pickaxe swap,
     * START_DESTROY_BLOCK, then STOP_DESTROY_BLOCK once the vanilla break time is up.
     */
    private void startMining(BlockPos pos) {
        int pickSlot = findPickaxeSlot();
        if (pickSlot == -1) {
            if (!warnedNoPickaxe) {
                warning("No pickaxe in the hotbar - the redstone block won't be mined back.");
                warnedNoPickaxe = true;
            }
            return;
        }
        warnedNoPickaxe = false;

        miningPos = pos;
        miningTicks = 0;
        swingCounter = 0;
        miningPick = mc.player.getInventory().getItem(pickSlot);
        miningRestoreSlot = mc.player.getInventory().getSelectedSlot();

        mc.player.connection.send(new ServerboundSetCarriedItemPacket(pickSlot));
        mc.player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
        mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
    }

    private void updateMining() {
        // Block already gone (server broke it or the explosion took it) - just restore the slot.
        if (mc.level.getBlockState(miningPos).getBlock() != Blocks.REDSTONE_BLOCK) {
            finishMining();
            return;
        }

        miningTicks++;
        if (++swingCounter >= 5) {
            swingCounter = 0;
            mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
        }

        if (miningTicks >= breakTicks(miningPos)) {
            mc.player.connection.send(new ServerboundPlayerActionPacket(
                ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, miningPos, Direction.UP));
            finishMining();
        }
    }

    private void finishMining() {
        miningPos = null;
        if (miningRestoreSlot >= 0) {
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(miningRestoreSlot));
            miningRestoreSlot = -1;
        }
    }

    private void stopMining() {
        if (miningPos == null) return;
        mc.player.connection.send(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, miningPos, Direction.UP));
        finishMining();
    }

    @Override
    public void onDeactivate() {
        stopMining();
    }

    private int findPickaxeSlot() {
        for (int i = 0; i < 9; i++) {
            if (isPickaxe(mc.player.getInventory().getItem(i).getItem())) return i;
        }
        return -1;
    }

    /**
     * Vanilla break time in ticks for the redstone block with the pickaxe we silently
     * swapped to. A pickaxe is always the correct tool for a redstone block, so the
     * per-tick progress is speed / hardness / 30 (hardness 1.5 - a wooden pickaxe takes
     * 23 ticks, iron 8, netherite 5).
     */
    private int breakTicks(BlockPos pos) {
        if (miningPick == null || miningPick.isEmpty()) return 10;

        BlockState state = mc.level.getBlockState(pos);
        float hardness = state.getDestroySpeed(mc.level, pos);
        float speed = miningPick.getDestroySpeed(state);
        if (hardness <= 0 || speed <= 1) return 10;

        return Math.max(1, (int) Math.ceil(hardness * 30 / speed));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        // progress the silent packet mine first; skip new ignitions while it runs
        if (miningPos != null) {
            updateMining();
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        BlockHitResult hit = null;

        if (clickThroughWalls.get()) {
            hit = findTntThroughWalls(ctwRange.get());
            if (hit == null) hit = lookingAtTnt();
        } else {
            hit = lookingAtTnt();
        }

        if (hit != null) ignite(hit);
    }
}
