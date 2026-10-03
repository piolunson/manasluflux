package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;

/**
 * Refills your offhand with a totem of undying whenever it pops (or you use it),
 * straight from any inventory slot - no screen opening, no hotbar shuffling.
 */
public class AutoTotem extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between offhand refills.")
        .defaultValue(2)
        .range(0, 40)
        .sliderRange(0, 20)
        .build()
    );

    private int timer;

    public AutoTotem() {
        super(AddonTemplate.COMBAT_CATEGORY, "auto-totem", "Instantly refills a totem of undying into your offhand whenever it is used or pops.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.gameMode == null) return;
        if (timer > 0) {
            timer--;
            return;
        }

        if (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) return;

        FindItemResult totem = InvUtils.find(Items.TOTEM_OF_UNDYING);
        if (!totem.found()) return;

        InvUtils.move().from(totem.slot()).toOffhand();

        timer = delay.get();
    }
}
