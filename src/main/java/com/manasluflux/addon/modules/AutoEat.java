package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AutoEat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("food-threshold")
        .description("Start eating when the food level drops below this.")
        .defaultValue(14)
        .range(1, 19)
        .sliderRange(1, 19)
        .build()
    );

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Eats without changing your visible hotbar slot by telling the server to switch slots invisibly.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> avoidGapples = sgGeneral.add(new BoolSetting.Builder()
        .name("avoid-golden-apples")
        .description("Won't eat golden apples.")
        .defaultValue(true)
        .build()
    );

    private int lastSlot = -1;
    private boolean eating;
    private int usingTicks;

    public AutoEat() {
        super(AddonTemplate.CATEGORY, "auto-eat", "Automatically eats food from your hotbar when hungry.");
    }

    public void setThreshold(int value) {
        this.threshold.set(value);
    }

    public int getThreshold() {
        return threshold.get();
    }

    public boolean isSilent() {
        return silent.get();
    }

    public void setSilent(boolean value) {
        silent.set(value);
    }

    private boolean isFood(ItemStack stack) {
        if (stack.isEmpty() || !stack.has(DataComponents.FOOD)) return false;
        if (avoidGapples.get() && (stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE))) return false;
        return true;
    }

    private int findFoodSlot() {
        for (int i = 0; i < 9; i++) {
            if (isFood(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private void switchTo(int slot) {
        if (slot < 0 || slot > 8) return;
        if (slot == mc.player.getInventory().getSelectedSlot()) return;

        if (silent.get()) {
            // Server-side only: the client keeps its current visible slot.
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
        } else {
            mc.player.getInventory().setSelectedSlot(slot);
        }
    }

    private void restoreSlot() {
        if (silent.get()) {
            // Tell the server we're back on the slot the client actually shows.
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
        } else if (lastSlot != -1) {
            mc.player.getInventory().setSelectedSlot(lastSlot);
        }
        lastSlot = -1;
    }

    private void startEating(int slot) {
        lastSlot = mc.player.getInventory().getSelectedSlot();
        switchTo(slot);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        mc.player.startUsingItem(InteractionHand.MAIN_HAND);
        eating = true;
        usingTicks = 0;
    }

    private void stopEating() {
        if (mc.player.isUsingItem()) mc.player.releaseUsingItem();
        restoreSlot();
        eating = false;
        usingTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.gameMode == null) return;

        int food = mc.player.getFoodData().getFoodLevel();

        if (eating) {
            usingTicks++;
            // Stop when full, or if the eat clearly never started / got interrupted.
            if (food >= 20 || (usingTicks > 15 && !mc.player.isUsingItem())) {
                stopEating();
            }
            return;
        }

        if (food < threshold.get()) {
            int slot = findFoodSlot();
            if (slot != -1) startEating(slot);
        }
    }

    @Override
    public void onDeactivate() {
        if (eating && mc.player != null) stopEating();
    }
}
