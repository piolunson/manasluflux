package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;

/**
 * XCarry: four extra storage slots in the 2x2 crafting grid of your own inventory.
 *
 * <p>Nothing client-side can move items into slots the server does not keep - the grid is a normal
 * part of {@code PlayerScreenHandler}, and the server empties it back into your inventory the moment
 * it is told the inventory screen closed. So the trick is simply not to tell it: the
 * {@code CloseHandledScreenC2SPacket} for your own player handler is cancelled while the grid holds
 * anything, and the server keeps the slots exactly as they were. Your client already reset itself to
 * {@code playerScreenHandler} before sending that packet, so pressing E again shows the same four
 * slots with the items still in them, and the crafting grid works as storage.
 *
 * <p>Only your own handler is touched - closing a chest, a shulker or an anvil sends a different
 * sync id and goes out normally. The packet is only held back while something is actually stored, so
 * an empty grid closes like vanilla instead of leaving the server thinking a screen is open. Turning
 * the module off (or emptying the grid and closing it) sends the real close, and the server moves the
 * items into your inventory the normal way, so nothing is ever stranded in the grid.
 */
public class XCarry extends Module {
    private final Setting<Boolean> chatFeedback = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Prints when the crafting grid starts being kept and when it is handed back.")
        .defaultValue(false)
        .build()
    );

    /** True while the server still has our player handler open because we swallowed the close. */
    private boolean keeping;
    /** Set while we deliberately send the close ourselves, so our own handler lets it through. */
    private boolean sendingRealClose;

    public XCarry() {
        super(AddonTemplate.PLAYER_CATEGORY, "xcarry",
            "Stores four extra item stacks in your 2x2 crafting grid by holding back the inventory close packet.");
    }

    @Override
    public void onActivate() {
        keeping = false;
        sendingRealClose = false;
    }

    @Override
    public void onDeactivate() {
        // Give the slots back: the server returns them to the inventory when it sees a real close.
        if (keeping) closeForReal();

        keeping = false;
        sendingRealClose = false;
    }

    @Override
    public String getInfoString() {
        return keeping ? "storing" : null;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (sendingRealClose) return;
        if (mc.player == null || mc.player.networkHandler == null) return;
        if (!(event.packet instanceof CloseHandledScreenC2SPacket packet)) return;

        // Someone else's screen (chest, shulker, anvil, ...) closes normally.
        if (packet.getSyncId() != mc.player.playerScreenHandler.syncId) return;

        // An empty grid has nothing worth keeping open - let vanilla close it.
        if (!gridHasItems()) {
            keeping = false;
            return;
        }

        event.cancel();

        if (!keeping) {
            keeping = true;

            if (chatFeedback.get()) info("Crafting grid kept - four extra slots are live on the server.");
        }
    }

    private boolean gridHasItems() {
        // Slot 0 is the crafting result; 1-4 are the 2x2 grid.
        for (int i = 1; i <= 4; i++) {
            if (!mc.player.playerScreenHandler.getSlot(i).getStack().isEmpty()) return true;
        }

        return false;
    }

    private void closeForReal() {
        if (mc.player == null || mc.player.networkHandler == null) return;

        sendingRealClose = true;

        try {
            mc.player.networkHandler.sendPacket(new CloseHandledScreenC2SPacket(mc.player.playerScreenHandler.syncId));
        } finally {
            sendingRealClose = false;
        }

        if (chatFeedback.get()) info("Crafting grid handed back to the server.");
    }
}
