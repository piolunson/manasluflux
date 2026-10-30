package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

/**
 * Turns your <b>obsidian placement packet</b> into a <b>wool placement packet</b>.
 *
 * <p>No wool in the inventory, no hotbar swapping, no position or "around your feet" test, nothing
 * about where you are looking. If the packet you are about to send places obsidian, the server is
 * told your hand holds wool for that one interaction, and then it is told your hand holds obsidian
 * again. That is the whole module.
 *
 * <p>Your client is never touched: the local inventory keeps the obsidian, so vanilla still predicts
 * and renders an <b>obsidian</b> block where the server actually stored <b>wool</b> - the block you
 * see and the block the server has disagree, which is the glitch block this exists for.
 *
 * <p>Why the packet swap instead of rewriting the placement packet: {@link PlayerInteractBlockC2SPacket}
 * carries only the hand, the block hit result, the sequence number and the screen-handler revision.
 * <b>The item is not in it.</b> The server decides what gets placed by looking the held item up in
 * your inventory, so the only packet that can change the outcome is the one that changes what the
 * server thinks you are holding.
 *
 * <p>That packet ({@link CreativeInventoryActionC2SPacket}) is a creative-only packet: a survival
 * server ignores it. Nothing here checks your gamemode, so on survival it simply does nothing.
 */
public class WoolReplace extends Module {
    private static final int HOTBAR_END = 9;
    private static final int INVENTORY_SLOT_OFFSET = 36;

    private boolean spoofed;
    private int spoofedSlot = -1;
    private ItemStack original = ItemStack.EMPTY;

    public WoolReplace() {
        super(AddonTemplate.BYPASS_CATEGORY, "universal-wool-replace",
            "Your obsidian placement goes out as a wool placement - no wool needed, obsidian stays in your hand, your client keeps showing obsidian.");
    }

    @Override
    public void onActivate() {
        spoofed = false;
        spoofedSlot = -1;
        original = ItemStack.EMPTY;
    }

    @Override
    public void onDeactivate() {
        restore();
    }

    /** Called at the head of {@code ClientConnection.send}, before the packet leaves the client. */
    public void beforeSend(Packet<?> packet) {
        if (!isActive() || spoofed) return;
        if (!(packet instanceof PlayerInteractBlockC2SPacket)) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        int slot = mc.player.getInventory().getSelectedSlot();
        if (slot < 0 || slot >= HOTBAR_END) return;

        ItemStack held = mc.player.getInventory().getStack(slot);
        if (!held.isOf(Items.OBSIDIAN)) return;

        original = held.copy();
        spoofedSlot = slot;
        spoofed = true;

        // Server-side only: the client stack is left alone so the predicted block stays obsidian.
        mc.getNetworkHandler().sendPacket(
            new CreativeInventoryActionC2SPacket(INVENTORY_SLOT_OFFSET + slot, new ItemStack(Items.WHITE_WOOL, 64)));
    }

    /** Called on return from {@code ClientConnection.send}, after the placement packet went out. */
    public void afterSend(Packet<?> packet) {
        if (!spoofed) return;
        if (!(packet instanceof PlayerInteractBlockC2SPacket)) return;
        restore();
    }

    private void restore() {
        if (!spoofed) return;

        if (spoofedSlot >= 0 && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(
                new CreativeInventoryActionC2SPacket(INVENTORY_SLOT_OFFSET + spoofedSlot, original));
        }

        spoofed = false;
        spoofedSlot = -1;
        original = ItemStack.EMPTY;
    }

    @Override
    public String getInfoString() {
        return "wool";
    }
}
