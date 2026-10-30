package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;

/**
 * Puts the current frame on the system clipboard, ready to paste into Discord instead of digging a
 * file out of the screenshots folder and uploading it.
 *
 * <p>It is a one-shot: bind a key, press it, the picture is on the clipboard and the module
 * switches itself back off. The frame is grabbed through vanilla's own
 * {@link ScreenshotRecorder#takeScreenshot}, so what lands on the clipboard is exactly what the
 * game rendered - no chat text or HUD is added or removed compared with F2.
 *
 * <p>The pixels are read on the render thread (that is where the framebuffer lives) and the
 * clipboard write is handed to a short-lived daemon thread, because on Windows a clipboard write
 * can block for a moment while other applications read the image.
 */
public class Screenshot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> copyToClipboard = sgGeneral.add(new BoolSetting.Builder()
        .name("copy-to-clipboard")
        .description("Put the screenshot on the clipboard so you can paste it straight into a chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Prints a line in chat once the screenshot has been copied.")
        .defaultValue(true)
        .build()
    );

    public Screenshot() {
        super(AddonTemplate.UTILITY_CATEGORY, "screenshot",
            "Copies a screenshot of the game to your clipboard instead of saving a file - paste it straight into Discord.");
    }

    @Override
    public void onActivate() {
        if (mc.getFramebuffer() == null) {
            toggle();
            return;
        }

        // takeScreenshot reads the framebuffer, so it has to happen on the render thread - which is
        // the thread the module was toggled from.
        ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), this::onCaptured);
    }

    private void onCaptured(NativeImage nativeImage) {
        try {
            if (!copyToClipboard.get()) {
                if (chatFeedback.get()) info("Screenshot captured, but copying to the clipboard is off.");
                return;
            }

            int width = nativeImage.getWidth();
            int height = nativeImage.getHeight();

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

            // getColorArgb already hands back ARGB, which is the order BufferedImage wants.
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setRGB(x, y, nativeImage.getColorArgb(x, y));
                }
            }

            Thread copy = new Thread(() -> copyToSystemClipboard(image), "ManasluFlux-Screenshot");
            copy.setDaemon(true);
            copy.start();
        } finally {
            nativeImage.close();

            // One-shot: the key press does its job and the module goes back to off.
            if (isActive()) toggle();
        }
    }

    private void copyToSystemClipboard(BufferedImage image) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageTransferable(image), null);

            if (chatFeedback.get()) {
                info("Screenshot copied to clipboard (%d×%d).", image.getWidth(), image.getHeight());
            }
        } catch (Throwable t) {
            error("Could not copy the screenshot to the clipboard: %s", t.getMessage());
        }
    }

    /** Wraps a {@link BufferedImage} as clipboard content. */
    private static class ImageTransferable implements Transferable {
        private final BufferedImage image;

        private ImageTransferable(BufferedImage image) {
            this.image = image;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);

            return image;
        }
    }
}
