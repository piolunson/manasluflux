package com.manasluflux.addon.hud;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import org.lwjgl.BufferUtils;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;

public class GifHud extends TextureHud {
    public static final HudElementInfo<GifHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "gif", "Displays an animated GIF file.", GifHud::new);

    private static final int MIN_FRAME_DELAY_MS = 10;
    private static final int DEFAULT_FRAME_DELAY_MS = 100;

    // Flat buffer holding every frame back to back, owned until unload. One row of frameBytes bytes per frame.
    private ByteBuffer frames;
    private int[] delays;
    private int frameCount;
    private int frameBytes;

    private int currentFrame;
    private double frameTimer;

    public GifHud() {
        super(INFO, "Path to the GIF file, either absolute or relative to the Minecraft directory.");
    }

    @Override
    protected void load(byte[] bytes) {
        if (bytes.length < 6 || bytes[0] != 'G' || bytes[1] != 'I' || bytes[2] != 'F') return;

        ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
        data.put(bytes).flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer delaysPtr = stack.mallocPointer(1);
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer z = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            ByteBuffer flatFrames = STBImage.stbi_load_gif_from_memory(data, delaysPtr, w, h, z, comp, 4);
            if (flatFrames == null) return;

            int width = w.get(0);
            int height = h.get(0);
            int count = Math.max(1, z.get(0));

            delays = new int[count];
            long delaysAddress = delaysPtr.get(0);
            if (delaysAddress != 0) {
                IntBuffer delayBuffer = MemoryUtil.memIntBuffer(delaysAddress, count);
                for (int i = 0; i < count; i++) delays[i] = Math.max(MIN_FRAME_DELAY_MS, delayBuffer.get(i));
                MemoryUtil.nmemFree(delaysAddress);
            } else {
                Arrays.fill(delays, DEFAULT_FRAME_DELAY_MS);
            }

            frames = flatFrames;
            frameCount = count;
            frameBytes = width * height * 4;
            currentFrame = 0;
            frameTimer = 0;

            createTexture(width, height, frameSlice(0));
        }
    }

    @Override
    protected void unload() {
        if (texture != null) {
            texture.close();
            texture = null;
        }

        if (frames != null) {
            STBImage.stbi_image_free(frames);
            frames = null;
        }

        delays = null;
        frameCount = 0;
        currentFrame = 0;
        frameTimer = 0;
    }

    @Override
    public void render(HudRenderer renderer) {
        if (texture == null || frames == null) {
            renderMissing(renderer);
            return;
        }

        if (frameCount > 1) {
            frameTimer += renderer.delta;

            int steps = 0;
            while (frameTimer >= delays[currentFrame] / 1000.0 && steps < frameCount) {
                frameTimer -= delays[currentFrame] / 1000.0;
                currentFrame = (currentFrame + 1) % frameCount;
                steps++;
            }

            if (steps > 0) texture.upload(frameSlice(currentFrame));
        }

        renderTexture(renderer);
    }

    private ByteBuffer frameSlice(int frame) {
        return frames.slice(frame * frameBytes, frameBytes);
    }
}
