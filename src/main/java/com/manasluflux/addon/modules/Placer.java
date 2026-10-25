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
import net.minecraft.util.math.BlockPos;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;

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
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick.")
        .defaultValue(2)
        .range(0, 10000)
        .sliderRange(0, 10000)
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
        super(AddonTemplate.WORLD_CATEGORY, "placer", "Places whitelisted blocks everywhere around you - fills nearby air with the selected blocks from your inventory.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        FindItemResult item = InvUtils.find(stack -> {
            if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
            return blocks.get().contains(blockItem.getBlock());
        });
        if (!item.found()) return;

        BlockPos feet = mc.player.getBlockPos();
        // radius can be set up to 10000, but a bigger scan cube than this would hang the tick.
        int r = Math.min(radius.get(), 8);

        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(feet.add(-r, -r, -r), feet.add(r, r, r))) {
            if (checkEntities.get() ? !BlockUtils.canPlace(pos, true) : !BlockUtils.canPlace(pos)) continue;
            positions.add(pos.toImmutable());
        }

        positions.sort(Comparator.comparingDouble(feet::getSquaredDistance));

        int placed = 0;
        for (BlockPos pos : positions) {
            if (placed >= blocksPerTick.get()) break;
            if (BlockUtils.place(pos, item, rotate.get(), 0, true, checkEntities.get(), true)) placed++;
        }
    }
}
