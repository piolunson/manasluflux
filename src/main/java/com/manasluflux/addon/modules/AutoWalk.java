package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.mojang.blaze3d.platform.InputConstants;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class AutoWalk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> key = sgGeneral.add(new KeybindSetting.Builder()
        .name("key")
        .description("The key to hold down while the module is enabled. Defaults to 2. Key modifiers are ignored.")
        .defaultValue(Keybind.fromKey(GLFW.GLFW_KEY_2))
        .onChanged(_ -> onKeyChanged())
        .build()
    );

    private InputConstants.Key heldKey;

    public AutoWalk() {
        super(AddonTemplate.CATEGORY, "auto-walk-hold", "Automatically holds the configured key (default 2) down while enabled.");
    }

    @Override
    public void onActivate() {
        press();
    }

    @Override
    public void onDeactivate() {
        release();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        // Re-assert every tick - the game releases all keys when a screen opens or the window loses focus.
        if (heldKey != null) KeyMapping.set(heldKey, true);
    }

    private void onKeyChanged() {
        if (!isActive()) return;

        release();
        press();
    }

    private void press() {
        heldKey = mappedKey();
        if (heldKey == null) return;

        KeyMapping.click(heldKey); // initial press, for actions that consume clicks (e.g. hotbar slots)
        KeyMapping.set(heldKey, true);
    }

    private void release() {
        if (heldKey == null) return;

        KeyMapping.set(heldKey, false);
        heldKey = null;
    }

    private InputConstants.Key mappedKey() {
        Keybind bind = key.get();
        if (bind == null || !bind.isSet() || !bind.isKey()) return null;

        return InputConstants.Type.KEYSYM.getOrCreate(bind.getValue());
    }
}
