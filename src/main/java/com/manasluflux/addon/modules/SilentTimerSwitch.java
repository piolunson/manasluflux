package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ItemSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/**
 * Held-item switch on a timer.
 *
 * <p>Every {@code every} ticks the module looks for {@code item} in your hotbar, switches to it for
 * {@code hold} ticks and then goes back to the slot you were on. In silent mode the switch is a bare
 * {@link UpdateSelectedSlotC2SPacket}: the server thinks you hold the item while your client keeps
 * showing (and using) the slot it was already on, so your hotbar never moves. Visible mode moves the
 * real slot for the hold time and restores it afterwards.
 *
 * <p>Hotbar only - the protocol can only select hotbar slots, so an item that is not in slots 1-9 is
 * never switched to.
 */
public class SilentTimerSwitch extends Module {
    public enum Mode {
        Silent,
        Visible
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Item> item = sgGeneral.add(new ItemSetting.Builder()
        .name("item")
        .description("The item to switch to. It has to be in your hotbar (slots 1-9).")
        .defaultValue(Items.TOTEM_OF_UNDYING)
        .build()
    );

    private final Setting<Integer> every = sgGeneral.add(new IntSetting.Builder()
        .name("every")
        .description("How often the switch happens, in ticks (20 ticks = 1 second).")
        .defaultValue(60)
        .range(1, 2400)
        .sliderRange(1, 200)
        .build()
    );

    private final Setting<Integer> hold = sgGeneral.add(new IntSetting.Builder()
        .name("hold")
        .description("How long you hold the item before switching back, in ticks (20 ticks = 1 second).")
        .defaultValue(10)
        .range(1, 1200)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Silent keeps the switch server-side only so your hotbar never moves; Visible also moves your real slot for the hold time.")
        .defaultValue(Mode.Silent)
        .build()
    );

    /** Ticks left until the next switch; only counts while the module is not holding the item. */
    private int ticksUntilSwitch;
    /** Ticks left in the current hold. */
    private int holdTicks;
    private boolean switched;
    /** The slot the client showed when the switch started (Visible mode only). */
    private int previousSlot = -1;

    public SilentTimerSwitch() {
        super(AddonTemplate.BYPASS_CATEGORY, "mf-silent-switch",
            "Switches your held item to the one you pick on a timer: every N ticks it switches, holds for M ticks, then goes back. Silent only tells the server, so your hotbar never moves.");
    }

    @Override
    public void onActivate() {
        ticksUntilSwitch = every.get();
        holdTicks = 0;
        switched = false;
        previousSlot = -1;
    }

    @Override
    public void onDeactivate() {
        restore();
        ticksUntilSwitch = 0;
        holdTicks = 0;
    }

    @Override
    public String getInfoString() {
        Item wanted = item.get();
        return wanted == null ? null : wanted.getName().getString();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (switched) {
            if (--holdTicks <= 0) {
                restore();
                // "Every" is the gap between switch starts, so the hold time comes out of it.
                ticksUntilSwitch = Math.max(1, every.get() - hold.get());
            }
            return;
        }

        if (--ticksUntilSwitch > 0) return;

        ticksUntilSwitch = every.get();

        int slot = findSlot();
        if (slot == -1) return;

        previousSlot = mc.player.getInventory().getSelectedSlot();

        if (mode.get() == Mode.Silent) {
            // Server-side only: the client keeps its current visible slot.
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        } else {
            mc.player.getInventory().setSelectedSlot(slot);
        }

        holdTicks = hold.get();
        switched = true;
    }

    /** First hotbar slot holding {@code item} that is not the one already in hand, or -1. */
    private int findSlot() {
        Item wanted = item.get();
        if (wanted == null) return -1;

        int current = mc.player.getInventory().getSelectedSlot();
        for (int i = 0; i < 9; i++) {
            if (i == current) continue;
            if (mc.player.getInventory().getStack(i).isOf(wanted)) return i;
        }

        return -1;
    }

    /** Puts the held slot back where it was. Safe to call when nothing is switched. */
    private void restore() {
        if (!switched) {
            previousSlot = -1;
            return;
        }

        switched = false;

        if (mc.player != null) {
            if (previousSlot != -1) {
                // Visible switch: the client slot moved, so move it back.
                mc.player.getInventory().setSelectedSlot(previousSlot);
            } else {
                // Tell the server we are back on the slot the client actually shows.
                mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(mc.player.getInventory().getSelectedSlot()));
            }
        }

        previousSlot = -1;
    }
}
