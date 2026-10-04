package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;

/**
 * Replaces every end crystal's full animated model (outer cube, inner core, bedrock
 * base and beam) with a single static box - or hides crystals entirely. Big FPS boost
 * with lots of crystals on screen. Purely client-side: nothing is sent to the server
 * and crystals behave exactly the same.
 */
public class CrystalOptimizer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Simple: render a plain static box instead of the full animated model. Hide: don't render crystals at all.")
        .defaultValue(Mode.Simple)
        .build()
    );

    private final Setting<ShapeMode> boxShape = sgGeneral.add(new EnumSetting.Builder<ShapeMode>()
        .name("box-shape")
        .description("Fill and/or outline for the simple box.")
        .defaultValue(ShapeMode.Both)
        .visible(() -> mode.get() == Mode.Simple)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgGeneral.add(new ColorSetting.Builder()
        .name("box-color")
        .description("The color of the simple box.")
        .defaultValue(new SettingColor(255, 255, 255, 60))
        .visible(() -> mode.get() == Mode.Simple)
        .build()
    );

    public CrystalOptimizer() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "crystal-optimizer", "Replaces every end crystal's animated model with a single static box (or hides it) - big FPS boost with lots of crystals on screen. Purely client-side rendering.");
    }

    /**
     * Called from EndCrystalRendererMixin - cancels the vanilla crystal model render.
     */
    public static boolean shouldCancelRender() {
        return Modules.get().isActive(CrystalOptimizer.class);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null || mode.get() != Mode.Simple) return;

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;

            var bb = crystal.getBoundingBox();
            event.renderer.box(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ,
                boxColor.get(), boxColor.get(), boxShape.get(), 0);
        }
    }

    public enum Mode {
        Simple,
        Hide
    }
}
