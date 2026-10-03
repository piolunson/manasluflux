package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ItemListSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.FastUse;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Enables Meteor Client's own FastUse module while a whitelisted item is held,
 * and disables it when holding anything else.
 */
public class WhitelistFastUse extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<Item>> whitelist = sgGeneral.add(new ItemListSetting.Builder()
        .name("whitelist")
        .description("While holding one of these items, Meteor's FastUse is enabled; holding anything else disables it.")
        .build()
    );

    private final Setting<Boolean> onlyWhenHolding = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-holding")
        .description("Only react to the main hand (off = the offhand triggers it too).")
        .defaultValue(true)
        .build()
    );

    private final FastUse meteorFastUse = Modules.get().get(FastUse.class);
    private boolean meteorWasActive;
    private boolean forcedOn;

    public WhitelistFastUse() {
        super(AddonTemplate.CATEGORY, "whitelist-fast-use", "Toggles Meteor's FastUse automatically based on the item you are holding.");
    }

    private boolean isWhitelisted(ItemStack stack) {
        return stack != null && !stack.isEmpty() && whitelist.get().contains(stack.getItem());
    }

    private boolean shouldBeActive() {
        if (mc.player == null) return false;
        boolean main = isWhitelisted(mc.player.getMainHandItem());
        boolean off = !onlyWhenHolding.get() && isWhitelisted(mc.player.getOffhandItem());
        return main || off;
    }

    @Override
    public void onActivate() {
        meteorWasActive = meteorFastUse != null && meteorFastUse.isActive();
        forcedOn = false;
    }

    @Override
    public void onDeactivate() {
        // If we force-enabled Meteor's FastUse, put it back the way we found it.
        if (forcedOn && meteorFastUse != null && meteorFastUse.isActive() && !meteorWasActive) {
            meteorFastUse.toggle();
        }
        forcedOn = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (meteorFastUse == null) return;

        boolean wanted = shouldBeActive();

        if (wanted && !meteorFastUse.isActive()) {
            meteorFastUse.toggle();
            forcedOn = true;
        } else if (!wanted && meteorFastUse.isActive() && forcedOn) {
            meteorFastUse.toggle();
            forcedOn = false;
        }
    }
}
