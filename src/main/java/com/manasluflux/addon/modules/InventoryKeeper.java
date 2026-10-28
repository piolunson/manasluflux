package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.SlotUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.Identifier;

import java.util.Arrays;

/**
 * Keeps your inventory in the layout you saved.
 *
 * <p>Press <b>Save layout</b> on the module screen and the slots you are using right now become the
 * reference. Every {@code refresh-rate} milliseconds the module re-reads the inventory and swaps
 * anything that drifted back to where it was saved.
 *
 * <p>It only ever moves an item <i>into</i> its saved slot - the item that was sitting there is
 * swapped back out to where the wanted item came from. Nothing is dropped, nothing is thrown out of
 * the inventory, and items that have been used up or lost are simply left alone. The check needs a
 * free cursor and no screen open, so it never fights you while you are dragging something around.
 */
public class InventoryKeeper extends Module {
    /** How many slots are stored - the whole playable inventory (hotbar + main). */
    private static final int SLOTS = SlotUtils.MAIN_END + 1;
    /** Moves per check, so a scrambled inventory is fixed over a few refreshes instead of in one burst. */
    private static final int MAX_MOVES_PER_CHECK = 8;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> refreshRate = sgGeneral.add(new IntSetting.Builder()
        .name("refresh-rate")
        .description("Milliseconds between inventory checks. 0 checks every tick.")
        .defaultValue(200)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> onlyHotbar = sgGeneral.add(new BoolSetting.Builder()
        .name("only-hotbar")
        .description("Only put the nine hotbar slots back in place. A missing item is still searched for in the whole inventory, so a sword that ended up in a backpack slot comes back.")
        .defaultValue(false)
        .build()
    );

    /** Saved item per slot index (0-8 hotbar, 9-35 main). Null = slot is not kept. */
    private final Item[] saved = new Item[SLOTS];
    private boolean hasLayout;
    private long lastCheck;

    public InventoryKeeper() {
        super(AddonTemplate.PLAYER_CATEGORY, "inventory-keeper", "Keeps your inventory in the layout you saved - checks on a refresh interval and puts anything that moved back.");
    }

    @Override
    public void onActivate() {
        lastCheck = 0;
        if (!hasLayout) info("No layout saved yet - open this module and press \"Save layout\".");
    }

    @Override
    public String getInfoString() {
        if (!hasLayout) return "no layout";

        int kept = 0;
        for (Item item : saved) if (item != null) kept++;
        return kept + " slots";
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!hasLayout) return;

        long now = System.currentTimeMillis();
        if (now - lastCheck < refreshRate.get()) return;
        lastCheck = now;

        check();
    }

    // ---------------------------------------------------------------- saving

    /**
     * Snapshots the slots that are in scope right now. Called by the Save layout button.
     */
    private void saveLayout() {
        if (mc.player == null) {
            error("Join a world first.");
            return;
        }

        PlayerInventory inventory = mc.player.getInventory();
        Arrays.fill(saved, null);

        int end = onlyHotbar.get() ? SlotUtils.HOTBAR_END : SlotUtils.MAIN_END;
        int kept = 0;

        for (int slot = SlotUtils.HOTBAR_START; slot <= end; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;

            saved[slot] = stack.getItem();
            kept++;
        }

        hasLayout = true;
        lastCheck = System.currentTimeMillis();

        if (kept == 0) warning("Saved an empty inventory - there is nothing to keep yet.");
        else info("Saved %d slot%s%s.", kept, kept == 1 ? "" : "s", onlyHotbar.get() ? " of your hotbar" : "");
    }

    // ---------------------------------------------------------------- checking

    private void check() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!mc.player.isAlive()) return;

        // Never touch the inventory while a screen is open or an item is on the cursor - the player
        // is moving things themselves and a mid-drag swap would fight them.
        if (mc.currentScreen != null) return;

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler != mc.player.playerScreenHandler) return;
        if (!handler.getCursorStack().isEmpty()) return;

        PlayerInventory inventory = mc.player.getInventory();
        int checkEnd = onlyHotbar.get() ? SlotUtils.HOTBAR_END : SlotUtils.MAIN_END;

        int moves = 0;
        int slot = SlotUtils.HOTBAR_START;

        while (slot <= checkEnd && moves < MAX_MOVES_PER_CHECK) {
            Item want = saved[slot];

            if (want != null && want != Items.AIR && !inventory.getStack(slot).isOf(want)) {
                int from = find(want, inventory);

                if (from != -1) {
                    // Swaps: the item that was sitting in the saved slot ends up where the wanted
                    // item came from, so nothing is lost and the displaced item is fixed on a
                    // later pass.
                    InvUtils.move().from(from).to(slot);
                    moves++;
                }
            }

            slot++;
        }
    }

    /**
     * Finds a source for a wanted item. Slots that are already holding their own saved item are
     * skipped on purpose: taking an item out of a slot where it belongs is what would make two
     * slots that both want the same item type fight over a single copy forever. Skipping them makes
     * every swap permanently satisfy one slot, so the layout converges instead of oscillating.
     *
     * @return the inventory index holding the wanted item, or -1 when it is not in the inventory
     *         at all (used up, dropped, in a container) or every copy is already in place.
     */
    private int find(Item want, PlayerInventory inventory) {
        for (int i = SlotUtils.HOTBAR_START; i <= SlotUtils.MAIN_END; i++) {
            if (!inventory.getStack(i).isOf(want)) continue;
            if (saved[i] == want) continue;

            return i;
        }

        return -1;
    }

    // ---------------------------------------------------------------- gui

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();

        WButton save = list.add(theme.button("Save layout")).expandX().widget();
        save.action = this::saveLayout;
        save.tooltip = "Use the slots you have now as the layout to keep.";

        list.add(theme.label("Takes a snapshot of "
            + (onlyHotbar.get() ? "your hotbar" : "your whole inventory")
            + " and swaps items back into their saved slots.")).widget();

        return list;
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = super.toTag();
        if (tag == null) return null;

        NbtCompound keeper = new NbtCompound();
        keeper.putBoolean("saved", hasLayout);

        for (int slot = 0; slot < SLOTS; slot++) {
            Item item = saved[slot];
            if (item == null || item == Items.AIR) continue;

            keeper.putString(Integer.toString(slot), Registries.ITEM.getId(item).toString());
        }

        tag.put("keeper", keeper);
        return tag;
    }

    @Override
    public Module fromTag(NbtCompound tag) {
        super.fromTag(tag);

        NbtCompound keeper = tag.getCompoundOrEmpty("keeper");
        Arrays.fill(saved, null);
        hasLayout = keeper.getBoolean("saved", false);

        if (hasLayout) {
            for (String key : keeper.getKeys()) {
                int slot = parseSlot(key);
                if (slot < 0 || slot >= SLOTS) continue;

                Item item = Registries.ITEM.get(Identifier.of(keeper.getString(key, "")));
                if (item == null || item == Items.AIR) continue;

                saved[slot] = item;
            }
        }

        return this;
    }

    private static int parseSlot(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
