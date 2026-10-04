package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Places whitelisted blocks everywhere around you - fills the nearby air
 * with the selected blocks straight from your inventory. Very useful fr fr.
 */
public class Placer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Block>> blocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("blocks")
        .description("The whitelist of blocks to place. Any matching block item from your inventory is used.")
        .defaultValue(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.NETHERRACK)
        .build()
    );

    private final Setting<Integer> radius = sgGeneral.add(new IntSetting.Builder()
        .name("radius")
        .description("How far around you to fill with blocks.")
        .defaultValue(3)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick.")
        .defaultValue(2)
        .min(1)
        .sliderMax(10)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Silently rotates towards the blocks it places.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> checkEntities = sgGeneral.add(new BoolSetting.Builder()
        .name("check-entities")
        .description("Don't place blocks where entities (like you) are standing.")
        .defaultValue(true)
        .build()
    );

    public Placer() {
        super(AddonTemplate.CATEGORY, "placer", "Places whitelisted blocks everywhere around you - fills nearby air with the selected blocks from your inventory.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        FindItemResult item = InvUtils.find(stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            return blocks.get().contains(blockItem.getBlock());
        });
        if (!item.found()) return;

        BlockPos feet = mc.player.blockPosition();
        int r = radius.get();

        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-r, -r, -r), feet.offset(r, r, r))) {
            if (checkEntities.get() ? !BlockUtils.canPlace(pos, true) : !BlockUtils.canPlace(pos)) continue;
            positions.add(pos.immutable());
        }

        positions.sort(Comparator.comparingDouble(feet::distSqr));

        int placed = 0;
        for (BlockPos pos : positions) {
            if (placed >= blocksPerTick.get()) break;
            if (BlockUtils.place(pos, item, rotate.get(), 0, true, checkEntities.get(), true)) placed++;
        }
    }
}
