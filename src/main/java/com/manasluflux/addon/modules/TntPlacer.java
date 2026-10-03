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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

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
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (timer > 0) {
            timer--;
            return;
        }

        Player target = findTarget();
        if (target == null) return;

        boolean acted = false;
        if (trapPlayer.get()) acted |= trap(target);
        if (placeTnt.get()) acted |= placeTnt(target);
        if (igniteTnt.get()) acted |= igniteTnt(target);

        if (acted) timer = delay.get();
    }

    private Player findTarget() {
        Player best = null;
        double bestDist = Double.MAX_VALUE;

        // mc.level.players() misses Meteor fake players (they are added as plain client-side
        // entities, not through the player registry), so scan the full entity list too.
        List<Player> candidates = new ArrayList<>(mc.level.players());
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof Player player) candidates.add(player);
        }

        for (Player player : candidates) {
            if (player == mc.player) continue;
            if (player.isDeadOrDying() || player.isSpectator() || player.isCreative()) continue;
            if (!Friends.get().shouldAttack(player)) continue;

            double dist = mc.player.distanceTo(player);
            if (dist > range.get() || dist >= bestDist) continue;

            best = player;
            bestDist = dist;
        }

        return best;
    }

    private boolean replaceable(BlockPos pos) {
        return mc.level.getBlockState(pos).canBeReplaced();
    }

    private boolean trap(Player target) {
        FindItemResult block = InvUtils.findInHotbar(trapBlock.get().item);
        if (!block.found()) {
            if (!warnedNoTrapBlock) {
                warning("No %s in the hotbar.", trapBlock.get());
                warnedNoTrapBlock = true;
            }
            return false;
        }
        warnedNoTrapBlock = false;

        BlockPos feet = target.blockPosition();
        boolean placed = false;

        // Ring around the head - blocks their body from stepping out of the column.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.above().relative(dir);
            if (replaceable(pos)) placed |= BlockUtils.place(pos, block, true, 50);
        }

        // Cap above the head.
        BlockPos cap = feet.above(2);
        if (replaceable(cap)) placed |= BlockUtils.place(cap, block, true, 50);

        return placed;
    }

    private boolean placeTnt(Player target) {
        FindItemResult tnt = InvUtils.findInHotbar(Items.TNT);
        if (!tnt.found()) {
            if (!warnedNoTnt) {
                warning("No TNT in the hotbar.");
                warnedNoTnt = true;
            }
            return false;
        }
        warnedNoTnt = false;

        BlockPos feet = target.blockPosition();

        // Fill every open side at once - up to 4 TNT per cycle.
        boolean placed = false;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(dir);
            if (!replaceable(pos)) continue;
            placed |= BlockUtils.place(pos, tnt, true, 50);
        }

        return placed;
    }

    private boolean igniteTnt(Player target) {
        BlockPos feet = target.blockPosition();
        BlockPos tntPos = null;

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos pos = feet.relative(dir);
            if (mc.level.getBlockState(pos).getBlock() instanceof TntBlock) {
                tntPos = pos;
                break;
            }
        }
        if (tntPos == null) return false;

        if (selfDamageGuard.get()) {
            double dist = mc.player.position().distanceTo(Vec3.atCenterOf(tntPos));
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
        if (swap) mc.player.connection.send(new ServerboundSetCarriedItemPacket(igniterSlot));

        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(tntPos), Direction.UP, tntPos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        if (swap) mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));

        return true;
    }
}
