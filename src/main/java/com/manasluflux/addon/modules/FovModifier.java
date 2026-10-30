package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Port of Sydney's <b>FOVModifier</b> ({@code me.aidan.sydney.modules.impl.miscellaneous.FOVModifierModule},
 * sydney-client/Sydney-Legacy, GPL-3.0). Sydney forces the value inside its own
 * {@code GameRendererMixin#getFov} hook; Meteor already posts {@link GetFovEvent} from the same place,
 * so the module simply overwrites the event's value and needs no mixin of its own.
 *
 * <p>Sydney's one subtlety is kept: the game asks for a fixed 70° when it renders something that must
 * not follow the FOV setting (the held item and other fixed-FOV passes). Those calls are left alone
 * unless {@code items} is enabled, which is what Sydney's {@code info.getReturnValue() == 70} check
 * does.
 */
public class FovModifier extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder()
        .name("fov")
        .description("The FOV you want to use.")
        .defaultValue(120.0)
        .range(50.0, 150.0)
        .sliderRange(70.0, 150.0)
        .build()
    );

    private final Setting<Boolean> items = sgGeneral.add(new BoolSetting.Builder()
        .name("items")
        .description("Modify the FOV of held items as well.")
        .defaultValue(false)
        .build()
    );

    public FovModifier() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "fov-modifier",
            "Forces the game's FOV to a number you pick instead of the vanilla option (port of Sydney's FOVModifier).");
    }

    @EventHandler
    private void onGetFov(GetFovEvent event) {
        // 70 is the fixed FOV the game uses for passes that ignore your FOV setting.
        if (!items.get() && Math.abs(event.fov - 70.0f) < 0.001f) return;

        event.fov = fov.get().floatValue();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(fov.get().intValue());
    }
}
