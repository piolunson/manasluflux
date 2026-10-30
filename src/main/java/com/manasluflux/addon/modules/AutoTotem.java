package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.util.PacketLevel;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;

/**
 * Refills your offhand with a totem of undying whenever it pops (or you use it),
 * straight from any inventory slot - no screen opening, no hotbar shuffling.
 *
 * With packet-level on, the refill is done by sending the inventory click packets yourself
 * (no client-side prediction, both clicks in the same millisecond) and the pop is detected
 * from the entity status packet, so the totem is back before the game's next tick.
 */
public class AutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPacket = settings.createGroup("Packet");

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between offhand refills.")
        .defaultValue(2)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> packetLevel = sgPacket.add(new BoolSetting.Builder()
        .name("packet-level")
        .description("Refills through raw inventory click packets and detects the pop from the status packet - removes the inventory/tick delay of the normal refill.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> packetRetries = sgPacket.add(new IntSetting.Builder()
        .name("packet-retries")
        .description("How many times the click pair is resent when the offhand still has no totem.")
        .defaultValue(2)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .visible(packetLevel::get)
        .build()
    );

    private final Setting<Integer> packetRetryDelay = sgPacket.add(new IntSetting.Builder()
        .name("packet-retry-delay")
        .description("Milliseconds to wait before resending the clicks.")
        .defaultValue(50)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .visible(packetLevel::get)
        .build()
    );

    private final Setting<Integer> maxPacketsPerSecond = sgPacket.add(new IntSetting.Builder()
        .name("max-packets-per-second")
        .description("Hard cap on inventory packets per second, so a broken sync can never flood the server.")
        .defaultValue(20)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .visible(packetLevel::get)
        .build()
    );

    private final PacketLevel packets = new PacketLevel();

    private int timer;
    private int offhandSlotId = -1;
    private long pendingUntil;

    public AutoTotem() {
        super(AddonTemplate.COMBAT_CATEGORY, "mf-auto-totem", "Instantly refills a totem of undying into your offhand whenever it is used or pops.");
    }

    @Override
    public void onActivate() {
        timer = 0;
        offhandSlotId = -1;
        pendingUntil = 0;
        packets.reset();
    }

    @Override
    public void onDeactivate() {
        packets.reset();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Packet level: resend clicks the server never confirmed.
        packets.tick();

        if (timer > 0) {
            timer--;
            return;
        }

        refill();
    }

    /**
     * Packet level detection: the server sends USE_TOTEM_OF_UNDYING for the player the instant
     * the totem pops. Refill right away instead of waiting for the next tick.
     */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!packetLevel.get()) return;

        if (event.packet instanceof EntityStatusS2CPacket status) {
            if (status.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
            if (mc.player == null || mc.world == null) return;
            if (!mc.player.equals(status.getEntity(mc.world))) return;

            mc.execute(() -> {
                if (!isActive() || mc.player == null) return;
                refill();
            });
        } else if (event.packet instanceof ScreenHandlerSlotUpdateS2CPacket update) {
            // Server confirmed the offhand slot - drop any pending resend.
            int offhand = offhandSlotId();
            if (offhand == -1 || update.getSlot() != offhand) return;
            if (update.getStack().isOf(Items.TOTEM_OF_UNDYING)) packets.clearRetries();
        }
    }

    private void refill() {
        if (mc.player == null) return;
        if (mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (!totem.found()) return;

        if (packetLevel.get()) {
            int offhand = offhandSlotId();
            ScreenHandler handler = mc.player.currentScreenHandler;
            if (offhand == -1 || handler == null) return;

            // Both clicks need room under the cap before the first one goes out.
            if (packets.remaining(maxPacketsPerSecond.get()) < 2) return;

            // A refill is already in flight (sent, not confirmed yet) - let the retry queue
            // finish it instead of pulling a second totem.
            if (packets.pendingRetries() > 0 || System.currentTimeMillis() < pendingUntil) return;

            int source = handler.getSlotIndex(mc.player.getInventory(), totem.slot()).orElse(-1);
            if (source == -1) return;

            int sourceId = source;

            packets.retry(packetRetries.get(), packetRetryDelay.get(),
                () -> mc.player != null && mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING),
                () -> resendClicks(sourceId, offhandSlotId()));

            swap(sourceId, offhand);
            pendingUntil = System.currentTimeMillis() + packetRetryDelay.get();
        } else {
            InvUtils.move().from(totem.slot()).toOffhand();
        }

        timer = packetLevel.get() ? 0 : delay.get();
    }

    private void resendClicks(int sourceId, int offhand) {
        if (mc.player == null || offhand == -1) return;
        if (packets.remaining(maxPacketsPerSecond.get()) < 2) return;

        swap(sourceId, offhand);
    }

    /**
     * Sends the two pickup clicks (source slot -> cursor -> offhand) as raw packets, with no
     * client-side prediction and no wait between them.
     */
    private void swap(int sourceId, int offhand) {
        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) return;

        sendClick(handler, sourceId);
        sendClick(handler, offhand);
    }

    private void sendClick(ScreenHandler handler, int slotId) {
        if (!packets.allow(maxPacketsPerSecond.get())) return;

        mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(
            handler.syncId,
            handler.nextRevision(),
            (short) slotId,
            (byte) 0,
            SlotActionType.PICKUP,
            Int2ObjectMaps.emptyMap(),
            ItemStackHash.EMPTY
        ));
    }

    /**
     * @return the screen handler slot id of the offhand slot, or -1 when there is no player
     *         inventory handler open (e.g. a chest is open).
     */
    private int offhandSlotId() {
        if (offhandSlotId != -1) return offhandSlotId;
        if (mc.player == null) return -1;
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) return -1;

        offhandSlotId = mc.player.playerScreenHandler.getSlotIndex(mc.player.getInventory(), 40).orElse(-1);
        return offhandSlotId;
    }

    @SuppressWarnings("unused")
    private static boolean isTotem(ItemStack stack) {
        return stack.isOf(Items.TOTEM_OF_UNDYING);
    }
}
