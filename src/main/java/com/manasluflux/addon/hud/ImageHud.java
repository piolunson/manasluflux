package com.manasluflux.addon.hud;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class ImageHud extends TextureHud {
    public static final HudElementInfo<ImageHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "image", "Displays an image file (png, jpg, bmp, first frame of a gif).", ImageHud::new);

    public ImageHud() {
        super(INFO, "Path to the image file, either absolute or relative to the Minecraft directory.");
    }

    @Override
    protected void load(byte[] bytes) {
        ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
        data.put(bytes).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer pixels = STBImage.stbi_load_from_memory(data, w, h, comp, 4);
            if (pixels == null) return;

            createTexture(w.get(0), h.get(0), pixels);
            STBImage.stbi_image_free(pixels);
        }
    }

    @Override
    protected void unload() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    @Override
    public void render(HudRenderer renderer) {
        if (texture == null) {
            renderMissing(renderer);
            return;
        }

        renderTexture(renderer);
    }
}
