package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class ToggleTab extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hideOnDisable = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-on-disable")
        .description("Hides the tab list again when you disable the module.")
        .defaultValue(true)
        .build()
    );

    public ToggleTab() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "toggle-tab", "Keeps the player list (tab) open by making the game think you are holding Tab.");
    }

    @Override
    public void onActivate() {
        mc.options.keyPlayerList.setDown(true);
    }

    @Override
    public void onDeactivate() {
        mc.options.keyPlayerList.setDown(false);
        if (hideOnDisable.get() && mc.gui != null && mc.gui.hud != null) {
            mc.gui.hud.getTabList().setVisible(false);
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        // Vanilla's HUD extraction renders the tab list only while keyPlayerList.isDown() is true,
        // and screen open/close events (releaseAll) clear the faked state - so re-assert every tick.
        mc.options.keyPlayerList.setDown(true);
    }
}
