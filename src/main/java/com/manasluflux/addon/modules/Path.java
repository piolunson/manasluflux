package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Path extends Module {
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the path line.")
        .defaultValue(new SettingColor(225, 25, 25, 200))
        .build()
    );

    private BlockPos target;

    public Path() {
        super(AddonTemplate.CATEGORY, "path", "Draws a line from you to a target set with .mfpath. (Doesn`t work yet)");
    }

    public void setTarget(BlockPos pos) {
        this.target = pos;
    }

    public BlockPos getTarget() {
        return target;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (target == null || mc.player == null) return;

        Vec3 start = mc.player.getEyePosition();
        event.renderer.line(
            start.x, start.y, start.z,
            target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5,
            color.get()
        );
        event.renderer.box(new AABB(target), color.get(), color.get(), ShapeMode.Both, 0);
    }
}
