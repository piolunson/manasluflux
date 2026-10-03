package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
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

    private int cooldownTicks;
    private boolean warnedNoIgniter;

    public InstantTnt() {
        super(AddonTemplate.CATEGORY, "instant-tnt", "Automatically ignites TNT just by looking at it (or through walls with click through walls) - silent server-side switch to your flint & steel.");
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
            if (!warnedNoIgniter) {
                warning("No flint & steel or fire charge in the hotbar.");
                warnedNoIgniter = true;
            }
            return;
        }
        warnedNoIgniter = false;

        boolean swap = silentSwitch.get() && igniterSlot != realSlot;
        if (swap) mc.player.connection.send(new ServerboundSetCarriedItemPacket(igniterSlot));

        // Server-side "press": sends the use packet with proper block-prediction sequencing.
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (result.consumesAction()) mc.player.swing(InteractionHand.MAIN_HAND);

        if (swap) mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));

        cooldownTicks = cooldown.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
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
