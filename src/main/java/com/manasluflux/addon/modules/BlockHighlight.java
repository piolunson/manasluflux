package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.shape.VoxelShape;

/**
 * Port of Homovore's <b>BlockHighlight</b> (leonetics/homovore-public, AGPL-3.0), plus a glow.
 *
 * <p>The box you are looking at is not just drawn where it is: it animates. Moving your crosshair
 * from one block to the next <b>morphs</b> the old box into the new one over {@value #ANIM_DURATION}ms
 * while shrinking it slightly at the halfway point, looking away <b>fades it out</b> (from wherever it
 * had got to, so a fade never snaps), and looking back <b>fades it in</b> again. Every transition is
 * eased with a smoothstep, so nothing starts or stops abruptly. The vanilla hit outline is hidden
 * while the module is on, so this is the only box on screen.
 *
 * <p>The glow is this port's addition: behind the crisp outline, {@code glow-layers} translucent
 * shells are drawn outward from the block, each one bigger and weaker than the one inside it. That
 * reads as a halo around the box - the same trick cheap bloom uses - using only Meteor's renderer,
 * and it follows the animation (it fades with the box and shrinks with it while shifting).
 */
public class BlockHighlight extends Module {
    /** Homovore's transition length, in milliseconds. */
    private static final float ANIM_DURATION = 60f;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> color = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Colour of the box.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    private final Setting<Boolean> fill = sgGeneral.add(new BoolSetting.Builder()
        .name("fill")
        .description("Fill the sides of the box. The side alpha is the alpha channel of side-color, so 0 is a pure outline.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgGeneral.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour and its alpha, for the faces of the box.")
        .defaultValue(new SettingColor(130, 80, 255, 45))
        .visible(fill::get)
        .build()
    );

    private final Setting<Double> lineThickness = sgGeneral.add(new DoubleSetting.Builder()
        .name("line-thickness")
        .description("Thickness of the box edges in blocks. Meteor's line renderer is always one pixel, so above 0 the edges are drawn as thin boxes instead.")
        .defaultValue(0.02)
        .range(0.0, 0.10)
        .sliderRange(0.0, 0.06)
        .build()
    );

    private final Setting<Boolean> glow = sgGeneral.add(new BoolSetting.Builder()
        .name("glow")
        .description("Draw a halo of translucent shells around the box.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> glowLayers = sgGeneral.add(new IntSetting.Builder()
        .name("glow-layers")
        .description("How many shells the halo is made of. More is smoother and costs more.")
        .defaultValue(3)
        .range(1, 6)
        .sliderRange(1, 6)
        .visible(glow::get)
        .build()
    );

    private final Setting<Double> glowSpread = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-spread")
        .description("How far the outermost shell sits outside the block, in blocks.")
        .defaultValue(0.12)
        .range(0.02, 0.40)
        .sliderRange(0.02, 0.30)
        .visible(glow::get)
        .build()
    );

    private final Setting<Double> glowStrength = sgGeneral.add(new DoubleSetting.Builder()
        .name("glow-strength")
        .description("Alpha of the innermost shell, as a fraction of the box colour's alpha. Each shell further out is weaker.")
        .defaultValue(0.35)
        .range(0.05, 1.0)
        .sliderRange(0.05, 1.0)
        .visible(glow::get)
        .build()
    );

    private enum State {
        Idle,
        FadingIn,
        Visible,
        Shifting,
        FadingOut
    }

    private State state = State.Idle;
    private Box fromBox;
    private Box toBox;
    private float progress;
    private BlockPos lastPos;
    private long lastNanos;

    public BlockHighlight() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "block-highlight",
            "Draws the block you are looking at, animating between blocks and fading in and out, with a glow around it.");
    }

    @Override
    public void onActivate() {
        state = State.Idle;
        fromBox = null;
        toBox = null;
        progress = 0f;
        lastPos = null;
        lastNanos = 0;

        // Homovore cancels the vanilla hit outline in its render event; Meteor draws it from a mixin,
        // so the flag on the game renderer is the switch that hides it.
        if (mc.gameRenderer != null) mc.gameRenderer.setBlockOutlineEnabled(false);
    }

    @Override
    public void onDeactivate() {
        if (mc.gameRenderer != null) mc.gameRenderer.setBlockOutlineEnabled(true);
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0f : (now - lastNanos) / 1_000_000f;
        lastNanos = now;

        BlockPos targetPos = null;
        Box targetBox = null;
        if (mc.crosshairTarget instanceof BlockHitResult result) {
            BlockPos pos = result.getBlockPos();
            VoxelShape shape = mc.world.getBlockState(pos).getOutlineShape(mc.world, pos);
            if (!shape.isEmpty()) {
                Box box = shape.getBoundingBox().offset(pos);
                if (!mc.player.getBoundingBox().intersects(box)) {
                    targetPos = pos;
                    targetBox = box;
                }
            }
        }

        boolean targetChanged = targetPos != null && !targetPos.equals(lastPos);
        boolean targetLost = targetPos == null && lastPos != null;
        lastPos = targetPos;

        switch (state) {
            case Idle -> {
                if (targetBox != null) {
                    fromBox = targetBox;
                    toBox = targetBox;
                    progress = 0f;
                    state = State.FadingIn;
                }
            }
            case FadingIn -> {
                progress = Math.min(1f, progress + dt / ANIM_DURATION);
                if (targetChanged) {
                    fromBox = animatedBox();
                    toBox = targetBox;
                    progress = 0f;
                    state = State.Shifting;
                } else if (targetLost) {
                    progress = 1f - progress;
                    state = State.FadingOut;
                } else if (progress >= 1f) {
                    state = State.Visible;
                }
            }
            case Visible -> {
                progress = 1f;
                if (targetChanged) {
                    fromBox = toBox;
                    toBox = targetBox;
                    progress = 0f;
                    state = State.Shifting;
                } else if (targetLost) {
                    progress = 0f;
                    state = State.FadingOut;
                }
            }
            case Shifting -> {
                progress = Math.min(1f, progress + dt / ANIM_DURATION);
                if (targetChanged) {
                    fromBox = animatedBox();
                    toBox = targetBox;
                    progress = 0f;
                } else if (targetLost) {
                    fromBox = animatedBox();
                    toBox = fromBox;
                    progress = 0f;
                    state = State.FadingOut;
                } else if (progress >= 1f) {
                    state = State.Visible;
                }
            }
            case FadingOut -> {
                progress = Math.min(1f, progress + dt / ANIM_DURATION);
                if (targetBox != null) {
                    fromBox = targetBox;
                    toBox = targetBox;
                    progress = 0f;
                    state = State.FadingIn;
                } else if (progress >= 1f) {
                    state = State.Idle;
                    fromBox = null;
                    toBox = null;
                }
            }
        }

        if (state == State.Idle || fromBox == null || toBox == null) return;

        float alpha = switch (state) {
            case FadingIn -> smoothstep(progress);
            case FadingOut -> 1f - smoothstep(progress);
            default -> 1f;
        };

        // A small pinch at the halfway point of a shift, so the box reads as moving rather than cutting.
        float scale = state == State.Shifting
            ? 1f - 0.2f * (float) Math.sin(progress * Math.PI)
            : 1f;

        Box box = scaledBox(animatedBox(), scale);

        Color line = color.get();
        draw(event, box, line, alpha);
    }

    private void draw(Render3DEvent event, Box box, Color base, float alpha) {
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

        drawWire(event, box, withAlpha(base, Math.round(base.a * alpha)), thickness);
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

    private Box animatedBox() {
        float t = smoothstep(progress);
        return new Box(
            lerp(fromBox.minX, toBox.minX, t),
            lerp(fromBox.minY, toBox.minY, t),
            lerp(fromBox.minZ, toBox.minZ, t),
            lerp(fromBox.maxX, toBox.maxX, t),
            lerp(fromBox.maxY, toBox.maxY, t),
            lerp(fromBox.maxZ, toBox.maxZ, t)
        );
    }

    /** Homovore's shape morph: the box grows outward from its own centre. */
    private static Box scaledBox(Box box, double scale) {
        if (scale == 1.0) return box;
        double cx = (box.minX + box.maxX) / 2;
        double cy = (box.minY + box.maxY) / 2;
        double cz = (box.minZ + box.maxZ) / 2;
        double hw = (box.maxX - box.minX) / 2 * scale;
        double hh = (box.maxY - box.minY) / 2 * scale;
        double hd = (box.maxZ - box.minZ) / 2 * scale;
        return new Box(cx - hw, cy - hh, cz - hd, cx + hw, cy + hh, cz + hd);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.r, color.g, color.b, Math.max(0, Math.min(255, alpha)));
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
