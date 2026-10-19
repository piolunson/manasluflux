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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.block.Blocks;
import net.minecraft.block.TntBlock;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

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
 *
 * Bow Fallback: with no igniter AND no redstone block either, it silently swaps to a bow
 * and shoots the TNT - a Flame-enchanted bow (or creative mode) makes flaming arrows that
 * ignite TNT on contact. One full draw is sent, then the release packet.
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

    private final Setting<Boolean> bowFallback = sgGeneral.add(new BoolSetting.Builder()
        .name("bow-fallback")
        .description("Last resort when there is no flint & steel, fire charge AND no redstone block: silently swaps to a bow and shoots the TNT. Only works with a Flame bow (or in creative mode) plus an arrow - flaming arrows ignite TNT on contact.")
        .defaultValue(true)
        .build()
    );

    private int cooldownTicks;
    private boolean warnedNoIgniter;
    private boolean warnedNoRedstone;
    private boolean warnedNoPickaxe;
    private boolean warnedNoBow;
    private boolean warnedBowCtw;

    // bow fallback state: draw for 10 ticks, then send the release packet
    private boolean bowDrawing;
    private int bowTicks;
    private int bowRestoreSlot = -1;

    // packet-mine state for the placed redstone block
    private BlockPos miningPos;
    private int miningTicks;
    private int miningRestoreSlot = -1;
    private int swingCounter;
    private ItemStack miningPick;

    public InstantTnt() {
        super(AddonTemplate.CATEGORY, "instant-tnt", "Automatically ignites TNT just by looking at it (or through walls with click through walls) - silent server-side switch to your flint & steel, a self-mining redstone block fallback, or a Flame-bow shot as a last resort.");
    }

    /**
     * True if the stack is a bow with the Flame enchantment - or any bow in creative mode,
     * where shot arrows always burn.
     */
    private boolean isFlameBow(ItemStack stack) {
        if (stack.getItem() != Items.BOW) return false;
        if (mc.player.getAbilities().creativeMode) return true;

        for (RegistryEntry<Enchantment> enchantment : stack.getEnchantments().getEnchantments()) {
            if (enchantment.getKey().filter(key -> key == Enchantments.FLAME).isPresent()) return true;
        }
        return false;
    }

    /**
     * First hotbar slot with a Flame bow. In creative mode any bow works, since all
     * shot arrows burn there.
     */
    private int findBowSlot() {
        boolean creative = mc.player.getAbilities().creativeMode;
        int plain = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() != Items.BOW) continue;
            if (isFlameBow(stack)) return i;
            if (plain == -1) plain = i;
        }
        return creative ? plain : -1;
    }

    private boolean hasArrow() {
        if (mc.player.getAbilities().creativeMode) return true;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == Items.ARROW) return true;
        }
        return false;
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
            if (isIgniter(mc.player.getInventory().getStack(i).getItem())) return i;
        }
        return -1;
    }

    private BlockHitResult lookingAtTnt() {
        if (mc.crosshairTarget == null || !(mc.crosshairTarget instanceof BlockHitResult bhr)) return null;
        BlockPos pos = bhr.getBlockPos();
        if (mc.world == null || !(mc.world.getBlockState(pos).getBlock() instanceof TntBlock)) return null;
        if (!mc.player.canInteractWithBlockAt(pos, mc.player.getBlockInteractionRange())) return null;
        return bhr;
    }

    /**
     * Finds the nearest TNT in reach, ignoring line of sight.
     */
    private BlockHitResult findTntThroughWalls(double range) {
        Vec3d eye = mc.player.getEyePos();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        int r = (int) Math.ceil(range);
        BlockPos eyePos = BlockPos.ofFloored(eye);

        for (BlockPos pos : BlockPos.iterate(
            eyePos.add(-r, -r, -r),
            eyePos.add(r, r, r)
        )) {
            BlockState state = mc.world.getBlockState(pos);
            if (!(state.getBlock() instanceof TntBlock)) continue;

            Vec3d center = Vec3d.ofCenter(pos);
            double dist = eye.distanceTo(center);
            if (dist > range || dist >= bestDist) continue;

            best = pos.toImmutable();
            bestDist = dist;
        }

        if (best == null) return null;

        // Aim at the top face center - a valid-looking hit for the server.
        Vec3d hitLoc = Vec3d.ofCenter(best).add(0, 0.5, 0);
        return new BlockHitResult(hitLoc, Direction.UP, best, false);
    }

    private void ignite(BlockHitResult hit) {
        int realSlot = mc.player.getInventory().getSelectedSlot();
        int igniterSlot = findIgniterSlot();

        if (igniterSlot == -1) {
            boolean ignited = false;

            if (redstoneBlock.get()) {
                BlockPos placed = igniteWithRedstone(hit.getBlockPos());
                if (placed != null) {
                    ignited = true;
                    cooldownTicks = cooldown.get();
                    if (mineRedstoneBlock.get()) startMining(placed);
                }
            }

            // still nothing? last resort: draw a (Flame) bow and shoot the TNT
            if (!ignited) {
                if (bowFallback.get()) {
                    // the arrow flies where you actually look, so only shoot when the
                    // crosshair is really on TNT (click through walls can't aim it)
                    if (lookingAtTnt() != null) {
                        if (!bowDrawing) startBowIgnite();
                    } else if (!warnedBowCtw) {
                        warning("Bow fallback needs the TNT in your crosshair - arrows can't be aimed through walls.");
                        warnedBowCtw = true;
                    }
                } else if (!warnedNoIgniter) {
                    warning("No flint & steel or fire charge in the hotbar.");
                    warnedNoIgniter = true;
                }
            }
            return;
        }
        warnedNoIgniter = false;
        warnedNoRedstone = false;

        boolean swap = silentSwitch.get() && igniterSlot != realSlot;
        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(igniterSlot));

        // Server-side "press": sends the use packet with proper block-prediction sequencing.
        ActionResult result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (result.isAccepted()) mc.player.swingHand(Hand.MAIN_HAND);

        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(realSlot));

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
            BlockPos pos = tnt.offset(direction);
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;
            if (!mc.player.canInteractWithBlockAt(pos, mc.player.getBlockInteractionRange())) continue;

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
        miningPick = mc.player.getInventory().getStack(pickSlot);
        miningRestoreSlot = mc.player.getInventory().getSelectedSlot();

        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(pickSlot));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
    }

    private void updateMining() {
        // Block already gone (server broke it or the explosion took it) - just restore the slot.
        if (mc.world.getBlockState(miningPos).getBlock() != Blocks.REDSTONE_BLOCK) {
            finishMining();
            return;
        }

        miningTicks++;
        if (++swingCounter >= 5) {
            swingCounter = 0;
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }

        if (miningTicks >= breakTicks(miningPos)) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, miningPos, Direction.UP));
            finishMining();
        }
    }

    private void finishMining() {
        miningPos = null;
        if (miningRestoreSlot >= 0) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(miningRestoreSlot));
            miningRestoreSlot = -1;
        }
    }

    private void stopMining() {
        if (miningPos == null) return;
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, miningPos, Direction.UP));
        finishMining();
    }

    /**
     * Last-resort ignite with a bow: silently swaps to the first (Flame) bow, sends one
     * draw packet, and releases after 10 ticks - the arrow flies at the TNT and its
     * flames ignite it (Flame bow, or any bow in creative). Needs an arrow.
     */
    private void startBowIgnite() {
        int bowSlot = findBowSlot();
        if (bowSlot == -1 || !hasArrow()) {
            if (!warnedNoBow) {
                warning("No usable bow + arrow: needs a Flame bow (or creative mode) and an arrow to ignite TNT.");
                warnedNoBow = true;
            }
            return;
        }
        warnedNoBow = false;
        warnedBowCtw = false;

        bowDrawing = true;
        bowTicks = 0;
        bowRestoreSlot = mc.player.getInventory().getSelectedSlot();

        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bowSlot));
        mc.player.networkHandler.sendPacket(new PlayerInteractItemC2SPacket(Hand.MAIN_HAND,
            0, mc.player.getYaw(), mc.player.getPitch()));
    }

    private void updateBowDraw() {
        bowTicks++;

        if (bowTicks >= 10) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.UP));
            finishBowDraw();
        }
    }

    private void finishBowDraw() {
        bowDrawing = false;
        bowTicks = 0;
        if (bowRestoreSlot >= 0) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(bowRestoreSlot));
            bowRestoreSlot = -1;
        }
        // one shot per second-ish so we don't machine-gun arrows at primed TNT
        cooldownTicks = Math.max(cooldownTicks, 20);
    }

    private void stopBowDraw() {
        if (!bowDrawing) return;
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.UP));
        finishBowDraw();
    }

    @Override
    public void onDeactivate() {
        stopMining();
        stopBowDraw();
    }

    private int findPickaxeSlot() {
        for (int i = 0; i < 9; i++) {
            if (isPickaxe(mc.player.getInventory().getStack(i).getItem())) return i;
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

        BlockState state = mc.world.getBlockState(pos);
        float hardness = state.getHardness(mc.world, pos);
        float speed = miningPick.getMiningSpeedMultiplier(state);
        if (hardness <= 0 || speed <= 1) return 10;

        return Math.max(1, (int) Math.ceil(hardness * 30 / speed));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        // progress the silent packet mine first; skip new ignitions while it runs
        if (miningPos != null) {
            updateMining();
            return;
        }

        // bow draw in progress - wait for the release
        if (bowDrawing) {
            updateBowDraw();
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
