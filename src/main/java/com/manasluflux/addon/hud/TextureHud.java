package com.manasluflux.addon.hud;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static meteordevelopment.meteorclient.MeteorClient.mc;

abstract class TextureHud extends HudElement {
    protected final SettingGroup sgGeneral;

    protected final Setting<String> path;
    protected final Setting<Double> scale;
    protected final Setting<Double> opacity;
    protected final Setting<Boolean> smooth;

    protected Texture texture;
    private String triedPath;
    private int texWidth, texHeight;

    private final Color renderColor = new Color();

    protected TextureHud(HudElementInfo<?> info, String pathDescription) {
        super(info);

        sgGeneral = settings.getDefaultGroup();

        path = sgGeneral.add(new StringSetting.Builder()
            .name("file-path")
            .description(pathDescription)
            .defaultValue("")
            .wide()
            .build()
        );

        scale = sgGeneral.add(new DoubleSetting.Builder()
            .name("scale")
            .description("Scale of the image relative to its native size.")
            .defaultValue(1)
            .min(0.1)
            .sliderRange(0.1, 5)
            .onChanged(_ -> applySize())
            .build()
        );

        opacity = sgGeneral.add(new DoubleSetting.Builder()
            .name("opacity")
            .description("Opacity of the image.")
            .defaultValue(1)
            .min(0)
            .sliderMax(1)
            .build()
        );

        smooth = sgGeneral.add(new BoolSetting.Builder()
            .name("smooth")
            .description("Use linear filtering instead of nearest neighbour.")
            .defaultValue(true)
            .onChanged(_ -> reload())
            .build()
        );
    }

    @Override
    public void tick(HudRenderer renderer) {
        String current = path.get();
        if (!current.equals(triedPath)) {
            triedPath = current;
            reload();
        }
    }

    @Override
    public void remove() {
        unload();
        super.remove();
    }

    protected void reload() {
        unload();

        Path file = resolve(path.get());
        if (file == null) return;

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException _) {
            return;
        }

        load(bytes);
        applySize();
    }

    // Decodes the file bytes and uploads the texture. Called on the main thread from tick().
    protected abstract void load(byte[] bytes);

    // Releases every resource owned by the element.
    protected abstract void unload();

    protected void createTexture(int width, int height, ByteBuffer rgba) {
        texWidth = width;
        texHeight = height;

        FilterMode filter = smooth.get() ? FilterMode.LINEAR : FilterMode.NEAREST;
        texture = new Texture(width, height, GpuFormat.RGBA8_UNORM, filter, filter);
        texture.upload(rgba);
    }

    protected void applySize() {
        if (texture == null) return;
        setSize(texWidth * scale.get(), texHeight * scale.get());
    }

    protected void renderTexture(HudRenderer renderer) {
        if (texture == null) return;

        renderColor.set(255, 255, 255, (int) (opacity.get() * 255));

        Renderer2D.TEXTURE.begin();
        Renderer2D.TEXTURE.texQuad(x, y, getWidth(), getHeight(), renderColor);
        Renderer2D.TEXTURE.render(texture.getTextureView(), texture.getSampler());
    }

    protected void renderMissing(HudRenderer renderer) {
        if (!isInEditor()) return;

        String msg = "Missing " + info.name;
        setSize(renderer.textWidth(msg, true), renderer.textHeight(true));
        renderer.quad(x, y, getWidth(), getHeight(), new Color(25, 25, 25, 50));
        renderer.text(msg, x, y, Color.RED, true);
    }

    protected Path resolve(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;

        Path file = Path.of(raw);
        if (Files.exists(file)) return file;

        Path relative = mc.gameDirectory.toPath().resolve(raw);
        if (Files.exists(relative)) return relative;

        return null;
    }
}
