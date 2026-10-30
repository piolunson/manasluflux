package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.WireframeEntityRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * ESP with the Block Highlight look: you pick the entity types the way Meteor's ESP does, and every
 * match is drawn with the same box that module uses - a fill whose alpha comes from the side colour,
 * real edge thickness (edges drawn as thin cuboids, because Meteor's line renderer is always one
 * pixel), and a halo of translucent shells that grow and weaken outward.
 *
 * <p>The animation is Block Highlight's too. An entity that comes into view fades its box in, one
 * that dies, leaves the world or stops matching the filter fades out from the box it was last drawn
 * at (never a snap), and boxes follow the entity's own interpolated hitbox so they slide with it
 * instead of ticking.
 */
public class CustomEsp extends Module {
    /** Fade length for an entity appearing or leaving, in milliseconds. */
    private static final float ANIM_DURATION = 120f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to draw. Same picker as Meteor's ESP.")
        .defaultValue(EntityType.PLAYER)
        .build()
    );

    private final Setting<Boolean> ignoreSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-self")
        .description("Never draw your own box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Style> style = sgGeneral.add(new EnumSetting.Builder<Style>()
        .name("style")
        .description("Model draws the entity's own model quads, so the shape is the mob itself - head, arms, legs, wings, all posed. Box draws its hitbox like Block Highlight.")
        .defaultValue(Style.Model)
        .build()
    );

    private final Setting<SettingColor> color = sgRender.add(new ColorSetting.Builder()
        .name("color")
        .description("Colour of the box edges and of the glow.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> fill = sgRender.add(new BoolSetting.Builder()
        .name("fill")
        .description("Fill the sides of the box. The side alpha is the alpha channel of side-color, so 0 is a pure outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour and its alpha, for the faces of the box.")
        .defaultValue(new SettingColor(130, 80, 255, 45))
        .visible(fill::get)
        .build()
    );

    private final Setting<Double> lineThickness = sgRender.add(new DoubleSetting.Builder()
        .name("line-thickness")
        .description("Thickness of the box edges in blocks. Meteor's line renderer is always one pixel, so above 0 the edges are drawn as thin boxes instead. Box style only - a model is drawn from its own quads, which are always one-pixel lines.")
        .defaultValue(0.02)
        .range(0.0, 0.10)
        .sliderRange(0.0, 0.06)
        .visible(() -> style.get() == Style.Box)
        .build()
    );

    private final Setting<Boolean> glow = sgRender.add(new BoolSetting.Builder()
        .name("glow")
        .description("Draw a halo of translucent shells around the box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> glowLayers = sgRender.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("How many shells the halo is made of. More is smoother and costs more.")
        .defaultValue(3)
        .range(1, 6)
        .sliderRange(1, 6)
        .visible(glow::get)
        .build()
    );

    private final Setting<Double> glowSpread = sgRender.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far the outermost shell sits outside the model (as a fraction of its size) or the box (in blocks).")
        .defaultValue(0.12)
        .range(0.02, 0.40)
        .sliderRange(0.02, 0.30)
        .visible(glow::get)
        .build()
    );

    private final Setting<Double> glowStrength = sgRender.add(new DoubleSetting.Builder()
        .name("glow-strength")
        .description("Alpha of the innermost shell, as a fraction of the box colour's alpha. Each shell further out is weaker.")
        .defaultValue(0.35)
        .range(0.05, 1.0)
        .sliderRange(0.05, 1.0)
        .visible(glow::get)
        .build()
    );

    /** Draws the entity's model quads instead of its hitbox. */
    public enum Style {
        Model,
        Box
    }

    /** One entity's fade state: where its box was last drawn and how visible it is right now. */
    private static final class Tracked {
        private Entity entity;
        private Box box;
        private float alpha;
        private boolean seen;
    }

    private final Map<Integer, Tracked> tracked = new HashMap<>();
    private long lastNanos;

    public CustomEsp() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "custom-esp",
            "ESP where you pick the entities, drawn with Block Highlight's box: fill, real edge thickness and a glow halo.");
    }

    @Override
    public void onActivate() {
        tracked.clear();
        lastNanos = 0;
    }

    @Override
    public void onDeactivate() {
        tracked.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : (now - lastNanos) / 1_000_000f;
        lastNanos = now;

        float step = dt / ANIM_DURATION;
        Set<EntityType<?>> types = entities.get();

        for (Tracked t : tracked.values()) t.seen = false;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == null || !entity.isAlive()) continue;
            if (ignoreSelf.get() && entity == mc.player) continue;
            if (!types.contains(entity.getType())) continue;

            Tracked t = tracked.computeIfAbsent(entity.getId(), id -> new Tracked());

            t.seen = true;
            t.entity = entity;
            t.box = entity.getBoundingBox();
            t.alpha = Math.min(1f, t.alpha + step);
        }

        Iterator<Map.Entry<Integer, Tracked>> iterator = tracked.entrySet().iterator();

        while (iterator.hasNext()) {
            Tracked t = iterator.next().getValue();

            if (!t.seen) {
                // Gone: fade out from the last box it was drawn at, then forget it.
                t.alpha -= step;

                if (t.alpha <= 0f) {
                    iterator.remove();
                    continue;
                }
            }

            if (t.alpha <= 0f) continue;

            // The model needs a live entity to read its render state from; a fade-out falls back to
            // the box it was last seen with, which is all that is left of it.
            if (style.get() == Style.Model && t.entity != null && t.entity.isAlive() && !t.entity.isRemoved()) {
                drawModel(event, t.entity, t.alpha);
            } else if (t.box != null) {
                draw(event, t.box, t.alpha);
            }
        }
    }

    /**
     * The entity's own model, quad by quad, through Meteor's wireframe renderer: the same call
     * Meteor's ESP uses for its wireframe mode, so the pose and the rotations of every part come
     * from the entity itself rather than from a box around it. The glow is the same model drawn a
     * little larger and weaker, so the halo hugs the shape instead of box-bloating around it.
     */
    private void drawModel(Render3DEvent event, Entity entity, float alpha) {
        Color base = color.get();

        if (glow.get()) {
            int layers = glowLayers.get();
            double spread = glowSpread.get();
            double strength = glowStrength.get();

            for (int i = layers; i >= 1; i--) {
                double t = (double) i / layers;
                float shellAlpha = (float) (alpha * strength * (1.0 - 0.75 * t));
                if (shellAlpha <= 0.002f) continue;

                Color shellFill = withAlpha(base, Math.round(base.a * shellAlpha * 0.6f));
                Color shellLine = withAlpha(base, Math.round(base.a * shellAlpha));

                WireframeEntityRenderer.render(event, entity, 1.0 + spread * t, shellFill, shellLine, ShapeMode.Sides);
            }
        }

        Color line = withAlpha(base, Math.round(base.a * alpha));
        Color side = withAlpha(sideColor.get(), Math.round(sideColor.get().a * alpha));

        WireframeEntityRenderer.render(event, entity, 1.0, side, line, fill.get() ? ShapeMode.Both : ShapeMode.Lines);
    }

    private void draw(Render3DEvent event, Box box, float alpha) {
        double thickness = lineThickness.get();

        if (fill.get()) {
            Color side = sideColor.get();
            Color fillColor = withAlpha(side, Math.round(side.a * alpha));
            event.renderer.boxSides(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, fillColor, 0);
        }

        if (glow.get()) {
            int layers = glowLayers.get();
            double spread = glowSpread.get();
            double strength = glowStrength.get();
            Color base = color.get();

            // Outermost first: each shell is bigger and weaker, so the halo falls off toward the edge.
            for (int i = layers; i >= 1; i--) {
                double t = (double) i / layers;
                double grow = spread * t;
                float shellAlpha = (float) (alpha * strength * (1.0 - 0.75 * t));
                if (shellAlpha <= 0.002f) continue;

                Box shell = box.expand(grow);
                Color shellFill = withAlpha(base, Math.round(base.a * shellAlpha));
                event.renderer.boxSides(shell.minX, shell.minY, shell.minZ, shell.maxX, shell.maxY, shell.maxZ, shellFill, 0);

                Color edge = withAlpha(base, Math.round(base.a * shellAlpha * 0.8f));
                drawWire(event, shell, edge, thickness);
            }
        }

        drawWire(event, box, withAlpha(color.get(), Math.round(color.get().a * alpha)), thickness);
    }

    /**
     * The box outline. At thickness 0 this is Meteor's one-pixel line renderer; above it every edge
     * becomes a thin cuboid, which is the only way to get a real thickness out of it.
     */
    private void drawWire(Render3DEvent event, Box box, Color color, double thickness) {
        if (thickness <= 0.0) {
            event.renderer.boxLines(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, color, 0);
            return;
        }

        double h = thickness / 2.0;

        for (int i = 0; i < 2; i++) {
            double y = i == 0 ? box.minY : box.maxY;
            for (int j = 0; j < 2; j++) {
                double z = j == 0 ? box.minZ : box.maxZ;
                edge(event, box.minX, y - h, z - h, box.maxX, y + h, z + h, color);
            }
        }

        for (int i = 0; i < 2; i++) {
            double x = i == 0 ? box.minX : box.maxX;
            for (int j = 0; j < 2; j++) {
                double z = j == 0 ? box.minZ : box.maxZ;
                edge(event, x - h, box.minY, z - h, x + h, box.maxY, z + h, color);
            }
        }

        for (int i = 0; i < 2; i++) {
            double x = i == 0 ? box.minX : box.maxX;
            for (int j = 0; j < 2; j++) {
                double y = j == 0 ? box.minY : box.maxY;
                edge(event, x - h, y - h, box.minZ, x + h, y + h, box.maxZ, color);
            }
        }
    }

    private void edge(Render3DEvent event, double x1, double y1, double z1,
                      double x2, double y2, double z2, Color color) {
        event.renderer.box(x1, y1, z1, x2, y2, z2, color, color, ShapeMode.Sides, 0);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, Math.max(0, Math.min(255, alpha)));
    }
}
