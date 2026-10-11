package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * When you place an item frame, this module silently interacts with it (instantly, or
 * after a configurable delay) and puts a randomly chosen filled map from your hotbar
 * into it. Uses silent hotbar swaps so your visible slot never changes, and remembers
 * filled frames so nothing gets replaced twice.
 */
public class AutoMapPart extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Puts the map in invisibly by only telling the server which slot to switch to, keeping your visible hotbar unchanged.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks to wait before the map is placed into a freshly placed frame. 0 = instant (same tick the frame spawns); vanilla click-feel delay is 3.")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final List<ItemFrame> filled = new ArrayList<>();
    // Frames waiting out their delay, with ticks left before they get filled.
    private final Map<ItemFrame, Integer> waiting = new HashMap<>();
    private final Random random = new Random();

    public AutoMapPart() {
        super(AddonTemplate.CATEGORY, "auto-map-part", "When you place an item frame, it automatically fills it with a random filled map from your hotbar. Instant by default (same tick the frame spawns) - a configurable delay is available. Maps are taken silently, so your visible hotbar slot stays the same.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        List<ItemFrame> newFrames = new ArrayList<>();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemFrame frame)) continue;
            if (filled.contains(frame) || waiting.containsKey(frame)) continue;

            // Only freshly-spawned empty frames: ones we just placed.
            if (!frame.getItem().isEmpty()) continue;
            if (frame.tickCount > 2) continue;

            newFrames.add(frame);
        }

        for (ItemFrame frame : newFrames) {
            // Claim the frame right away so it is never detected twice.
            filled.add(frame);
            if (delay.get() > 0) {
                waiting.put(frame, delay.get());
            } else {
                fillFrame(frame); // instant: same tick the frame spawns
            }
        }

        // Count down waiting frames.
        if (!waiting.isEmpty()) {
            List<ItemFrame> ready = new ArrayList<>();
            for (var e : waiting.entrySet()) {
                ItemFrame frame = e.getKey();
                if (mc.level.getEntity(frame.getId()) == null || !frame.getItem().isEmpty()) {
                    ready.add(frame); // gone or already filled - drop it
                    continue;
                }
                int left = e.getValue() - 1;
                if (left <= 0) ready.add(frame);
                else waiting.put(frame, left);
            }
            for (ItemFrame frame : ready) {
                waiting.remove(frame);
                if (frame.getItem().isEmpty() && mc.level.getEntity(frame.getId()) != null) {
                    fillFrame(frame);
                }
            }
        }

        // Clean up removed frames from the memory list so it doesn't grow forever.
        filled.removeIf(f -> (mc.level.getEntity(f.getId())) == null);
    }

    private void fillFrame(ItemFrame frame) {
        // Pick a filled map from the hotbar.
        List<Integer> mapSlots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(Items.FILLED_MAP)) mapSlots.add(i);
        }
        if (mapSlots.isEmpty()) return;

        int slot = mapSlots.get(random.nextInt(mapSlots.size()));
        int realSlot = mc.player.getInventory().getSelectedSlot();

        if (silent.get()) {
            // Silent: send a carried-item packet so the server swaps the slot invisibly,
            // interact, then swap the server's view back to the real slot.
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
            mc.player.connection.send(new ServerboundInteractPacket(frame.getId(),
                InteractionHand.MAIN_HAND, new Vec3(0.5, 0.5, 0.5), false));
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));
        } else {
            // Visible: actually swap the hotbar, interact, then swap back.
            mc.player.getInventory().setSelectedSlot(slot);
            mc.player.connection.send(new ServerboundInteractPacket(frame.getId(),
                InteractionHand.MAIN_HAND, new Vec3(0.5, 0.5, 0.5), false));
            mc.player.getInventory().setSelectedSlot(realSlot);
        }
    }

    @Override
    public void onDeactivate() {
        filled.clear();
        waiting.clear();
    }

    @Override
    public String getInfoString() {
        return String.valueOf(filled.size());
    }
}
