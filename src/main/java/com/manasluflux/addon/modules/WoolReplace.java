package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;

/**
 * Places <b>wool instead of obsidian</b>: whenever you right-click a block while holding obsidian,
 * the click is made with a wool stack in your hand instead, and your obsidian comes straight back
 * afterwards.
 *
 * <p><b>Why it swaps instead of editing the packet:</b> the placement packet
 * ({@code PlayerInteractBlockC2SPacket}) carries the hand, the block you hit, the sequence number
 * and the screen-handler revision - it does <b>not</b> carry the item. What gets placed is decided
 * by the server from the item it believes you are holding, which comes from your inventory, not
 * from the packet. So the only way to make the server place wool is to actually be holding wool for
 * that one click: {@link com.manasluflux.addon.mixin.WoolReplaceMixin} swaps the hotbar slot right
 * before vanilla builds the placement (so the client predicts wool too and there is no ghost
 * block), and swaps back on the way out.
 *
 * <p>No checks beyond "am I holding obsidian" and "is there wooi in the hotbar": no position test,
 * no distance test, no target test. If you have no wool in the hotbar nothing happens, because
 * there is nothing honest to place.
 */
public class WoolReplace extends Module {
    private boolean swapped;

    public WoolReplace() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "wool-replace",
            "Places wool instead of obsidian: swaps to a wool hotbar slot for the click, then swaps straight back.");
    }

    @Override
    public void onActivate() {
        swapped = false;
    }

    @Override
    public void onDeactivate() {
        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }
    }

    /** Called from the mixin at the head of {@code interactBlock}, before vanilla builds the packet. */
    public void beforePlacement(Hand hand) {
        if (!isActive() || mc.player == null) return;
        if (hand != Hand.MAIN_HAND) return;
        if (swapped) return;

        ItemStack held = mc.player.getMainHandStack();
        if (!held.isOf(Items.OBSIDIAN)) return;

        int wool = findWoolSlot();
        if (wool < 0) return;

        if (wool == mc.player.getInventory().getSelectedSlot()) return;

        InvUtils.swap(wool, false);
        swapped = true;
    }

    /** Called from the mixin when {@code interactBlock} returns - your obsidian is selected again. */
    public void afterPlacement() {
        if (!swapped) return;

        InvUtils.swapBack();
        swapped = false;
    }

    private int findWoolSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isIn(ItemTags.WOOL)) return i;
        }

        return -1;
    }
}
