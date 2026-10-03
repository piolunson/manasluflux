package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class Waypoint extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("The color of the waypoint markers.")
        .defaultValue(new SettingColor(225, 25, 25, 150))
        .build()
    );

    private final Map<String, BlockPos> waypoints = new HashMap<>();
    private boolean needsRender;

    public Waypoint() {
        super(AddonTemplate.CATEGORY, "waypoint", "Stores waypoints added with .mfwaypoint and renders beacon-like beams to them.");
    }

    public void add(String name, BlockPos pos) {
        waypoints.put(name, pos.immutable());
    }

    public Map<String, BlockPos> all() {
        return waypoints;
    }

    public void clear() {
        waypoints.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        needsRender = !waypoints.isEmpty();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (!needsRender || mc.player == null) return;

        for (Map.Entry<String, BlockPos> entry : waypoints.entrySet()) {
            BlockPos pos = entry.getValue();
            int height = Math.max(pos.getY(), BlockPos.containing(mc.player.position()).getY()) + 64 - pos.getY();

            // Beacon-style vertical beam
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                event.renderer.line(
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                    pos.getX() + 0.5 + dir.getStepX() * 0.4, pos.getY() + height, pos.getZ() + 0.5 + dir.getStepZ() * 0.4,
                    color.get()
                );
            }
            event.renderer.line(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5, color.get());

            event.renderer.box(new AABB(pos), color.get(), color.get(), ShapeMode.Both, 0);
        }
    }
}
