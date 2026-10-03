package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the blocks you place and, whenever one of them is mined, exploded or otherwise
 * removed, places the exact same block back. Detects placements from your own outgoing
 * use-item-on packets (client-side prediction shows the placed block instantly) and un-tracks
 * positions you mine yourself, so your own mining isn't fought against.
 */
public class BlockReplacer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Tracked positions farther than this from you are forgotten.")
        .defaultValue(8.0)
        .range(3.0, 32.0)
        .sliderRange(3.0, 16.0)
        .build()
    );

    private final Setting<Integer> maxTracked = sgGeneral.add(new IntSetting.Builder()
        .name("max-blocks")
        .description("Maximum number of placed blocks to keep track of. Oldest ones are dropped first.")
        .defaultValue(128)
        .range(8, 1024)
        .sliderRange(8, 512)
        .build()
    );

    private final Setting<Boolean> ignoreSelfMined = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self-mined")
        .description("Doesn't replace blocks that you mine yourself.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates towards the block when placing it back.")
        .defaultValue(true)
        .build()
    );

    // Insertion-ordered so the oldest tracked positions can be evicted first.
    private final LinkedHashMap<BlockPos, Block> tracked = new LinkedHashMap<>();

    public BlockReplacer() {
        super(AddonTemplate.CATEGORY, "block-replacer", "Places back the same block when one of your placed blocks gets mined or removed.");
    }

    @Override
    public void onActivate() {
        tracked.clear();
    }

    @Override
    public void onDeactivate() {
        tracked.clear();
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.player == null || mc.level == null) return;

        // Track own block placements.
        if (event.packet instanceof ServerboundUseItemOnPacket packet) {
            Item held = mc.player.getItemInHand(packet.getHand()).getItem();
            if (!(held instanceof BlockItem blockItem)) return;

            Block placed = blockItem.getBlock();
            BlockHitResult hit = packet.getHitResult();
            BlockPos clicked = hit.getBlockPos();
            BlockPos neighbor = clicked.relative(hit.getDirection());

            // Normal placement goes into the neighbor of the clicked face; placing into a
            // replaceable block (grass, water, ...) replaces the clicked position itself.
            if (mc.level.getBlockState(neighbor).getBlock() == placed) {
                track(neighbor, placed);
            } else if (mc.level.getBlockState(clicked).getBlock() == placed) {
                track(clicked, placed);
            }
        }

        // Stop replacing blocks we mine ourselves.
        if (ignoreSelfMined.get() && event.packet instanceof ServerboundPlayerActionPacket packet) {
            if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                tracked.remove(packet.getPos());
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || tracked.isEmpty()) return;

        double rangeSq = range.get() * range.get();

        Iterator<Map.Entry<BlockPos, Block>> it = tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Block> entry = it.next();
            BlockPos pos = entry.getKey();

            if (mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > rangeSq) {
                it.remove();
                continue;
            }

            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() == Blocks.VOID_AIR) continue; // chunk not loaded
            if (!state.isAir() && !state.canBeReplaced()) continue; // block still there

            Item item = entry.getValue().asItem();
            if (item == Items.AIR) {
                it.remove();
                continue;
            }

            FindItemResult find = InvUtils.findInHotbar(item);
            if (!find.found()) continue; // no matching block in hotbar - retry next tick

            BlockUtils.place(pos, find, rotate.get(), 50);
        }

        while (tracked.size() > maxTracked.get()) {
            BlockPos oldest = tracked.keySet().iterator().next();
            tracked.remove(oldest);
        }
    }

    private void track(BlockPos pos, Block block) {
        tracked.remove(pos); // refresh insertion order
        tracked.put(pos, block);
    }
}
