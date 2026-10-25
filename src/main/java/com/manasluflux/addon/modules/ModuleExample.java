package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

public class ModuleExample extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> scale = sgGeneral.add(new DoubleSetting.Builder()
        .name("scale")
        .description("The size of the marker.")
        .defaultValue(2.0d)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the marker.")
        .defaultValue(Color.MAGENTA)
        .build()
    );

    public ModuleExample() {
        super(AddonTemplate.CATEGORY, "world-origin", "An example module that highlights the center of the world.");
    }

    @EventHandler
    private void onRender3d(Render3DEvent event) {
        Box marker = new Box(BlockPos.ORIGIN);
        marker = marker.stretch(
            scale.get() * marker.getLengthX(),
            scale.get() * marker.getLengthY(),
            scale.get() * marker.getLengthZ()
        );

        event.renderer.box(marker, color.get(), color.get(), ShapeMode.Both, 0);
    }
}
