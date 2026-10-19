package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.block.TntBlock;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Traps the nearest player in (crying) obsidian, then buries them in TNT and ignites it, non-stop.
 * The cage goes around their head + above it (so they cannot walk out), TNT is placed in the
 * empty cell right next to them (the only spot the server accepts while they are trapped) and
 * primed with a silent server-side switch to your flint & steel.
 */
public class TntPlacer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to the target player.")
        .defaultValue(4.5)
        .range(1.0, 6.0)
        .sliderRange(1.0, 6.0)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between action cycles. 0 = every tick (non-stop).")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> trapPlayer = sgGeneral.add(new BoolSetting.Builder()
        .name("trap-player")
        .description("Traps the target in (crying) obsidian: ring around their head plus a cap above it.")
        .defaultValue(true)
        .build()
    );

    private final Setting<TrapBlock> trapBlock = sgGeneral.add(new EnumSetting.Builder<TrapBlock>()
        .name("trap-block")
        .description("The block used for the trap.")
        .defaultValue(TrapBlock.OBSIDIAN)
        .build()
    );

    private final Setting<Boolean> placeTnt = sgGeneral.add(new BoolSetting.Builder()
        .name("place-tnt")
        .description("Places TNT in the cell next to the trapped player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> igniteTnt = sgGeneral.add(new BoolSetting.Builder()
        .name("ignite-tnt")
        .description("Primes placed TNT with a silent server-side switch to your igniter.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> selfDamageGuard = sgGeneral.add(new BoolSetting.Builder()
        .name("self-damage-guard")
        .description("Only ignites TNT when you are farther than min-self-distance from it, so you don't blow yourself up.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minSelfDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-self-distance")
        .description("How far away from the TNT you must be before it is ignited.")
        .defaultValue(5.0)
        .range(0.0, 12.0)
        .sliderRange(0.0, 12.0)
        .visible(selfDamageGuard::get)
        .build()
    );

    private int timer;
    private boolean warnedNoTrapBlock;
    private boolean warnedNoTnt;
    private boolean warnedNoIgniter;

    public TntPlacer() {
        super(AddonTemplate.COMBAT_CATEGORY, "tnt-placer", "Traps the nearest player in (crying) obsidian, buries them in TNT and ignites it - non-stop.");
    }

    public enum TrapBlock {
        OBSIDIAN("Obsidian", Items.OBSIDIAN),
        CRYING_OBSIDIAN("Crying Obsidian", Items.CRYING_OBSIDIAN);

        private final String title;
        public final Item item;

        TrapBlock(String title, Item item) {
            this.title = title;
            this.item = item;
        }

        @Override
        public String toString() {
            return title;
        }
    }

    @Override
    public void onActivate() {
        timer = 0;
        warnedNoTrapBlock = false;
        warnedNoTnt = false;
        warnedNoIgniter = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (timer > 0) {
            timer--;
            return;
        }

        PlayerEntity target = findTarget();
        if (target == null) return;

        boolean acted = false;
        if (trapPlayer.get()) acted |= trap(target);
        if (placeTnt.get()) acted |= placeTnt(target);
        if (igniteTnt.get()) acted |= igniteTnt(target);

        if (acted) timer = delay.get();
    }

    private PlayerEntity findTarget() {
        PlayerEntity best = null;
        double bestDist = Double.MAX_VALUE;

        // mc.world.getPlayers() misses Meteor fake players (they are added as plain client-side
        // entities, not through the player registry), so scan the full entity list too.
        List<PlayerEntity> candidates = new ArrayList<>(mc.world.getPlayers());
        for (Entity entity : mc.world.getEntities()) {
            if (entity instanceof PlayerEntity player) candidates.add(player);
        }

        for (PlayerEntity player : candidates) {
            if (player == mc.player) continue;
            if (player.isDead() || player.isSpectator() || player.isCreative()) continue;
            if (!Friends.get().shouldAttack(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > range.get() || dist >= bestDist) continue;

            best = player;
            bestDist = dist;
        }

        return best;
    }

    private boolean replaceable(BlockPos pos) {
        return mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean trap(PlayerEntity target) {
        FindItemResult block = InvUtils.findInHotbar(trapBlock.get().item);
        if (!block.found()) {
            if (!warnedNoTrapBlock) {
                warning("No %s in the hotbar.", trapBlock.get());
                warnedNoTrapBlock = true;
            }
            return false;
        }
        warnedNoTrapBlock = false;

        BlockPos feet = target.getBlockPos();
        boolean placed = false;

        // Ring around the head - blocks their body from stepping out of the column.
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos pos = feet.up().offset(dir);
            if (replaceable(pos)) placed |= BlockUtils.place(pos, block, true, 50);
        }

        // Cap above the head.
        BlockPos cap = feet.up(2);
        if (replaceable(cap)) placed |= BlockUtils.place(cap, block, true, 50);

        return placed;
    }

    private boolean placeTnt(PlayerEntity target) {
        FindItemResult tnt = InvUtils.findInHotbar(Items.TNT);
        if (!tnt.found()) {
            if (!warnedNoTnt) {
                warning("No TNT in the hotbar.");
                warnedNoTnt = true;
            }
            return false;
        }
        warnedNoTnt = false;

        BlockPos feet = target.getBlockPos();

        // Fill every open side at once - up to 4 TNT per cycle.
        boolean placed = false;
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos pos = feet.offset(dir);
            if (!replaceable(pos)) continue;
            placed |= BlockUtils.place(pos, tnt, true, 50);
        }

        return placed;
    }

    private boolean igniteTnt(PlayerEntity target) {
        BlockPos feet = target.getBlockPos();
        BlockPos tntPos = null;

        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos pos = feet.offset(dir);
            if (mc.world.getBlockState(pos).getBlock() instanceof TntBlock) {
                tntPos = pos;
                break;
            }
        }
        if (tntPos == null) return false;

        if (selfDamageGuard.get()) {
            double dist = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(tntPos));
            if (dist < minSelfDistance.get()) return false;
        }

        FindItemResult igniter = InvUtils.findInHotbar(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE);
        if (!igniter.found() || !igniter.isHotbar()) {
            if (!warnedNoIgniter) {
                warning("No flint & steel or fire charge in the hotbar.");
                warnedNoIgniter = true;
            }
            return false;
        }
        warnedNoIgniter = false;

        int realSlot = mc.player.getInventory().getSelectedSlot();
        int igniterSlot = igniter.slot();

        boolean swap = igniterSlot != realSlot;
        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(igniterSlot));

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(tntPos), Direction.UP, tntPos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(realSlot));

        return true;
    }
}
