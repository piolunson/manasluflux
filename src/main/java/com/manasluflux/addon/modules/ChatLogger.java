package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Port of RyanWare's ChatLogger (SmilerRyan): logs incoming and outgoing chat to a file with
 * session support - a fresh log per game join, path configurable with %date%, %time%,
 * %player% and %server% placeholders. Rewritten for MC 26.2.
 */
public class ChatLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> pathFormat = sgGeneral.add(new StringSetting.Builder()
        .name("path-format")
        .description("Full log path format. Relative paths are relative to your .minecraft folder.")
        .defaultValue("manasluflux/chatlogs/%date%_%time%_%player%_%server%.log")
        .build()
    );

    private File logFile;
    private boolean sessionInitialized;

    public ChatLogger() {
        super(AddonTemplate.REWRITE_CATEGORY, "chat-logger", "Logs incoming and outgoing chat to a file with session support.");
    }

    @Override
    public void onActivate() {
        startNewSession();
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        startNewSession();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.getMessage() != null) log("[I] " + event.getMessage().getString());
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (event.message != null) log("[O] " + event.message);
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundChatCommandPacket packet) log("[O] /" + packet.command());
        else if (event.packet instanceof ServerboundChatCommandSignedPacket packet) log("[O] /" + packet.command());
    }

    private void startNewSession() {
        logFile = null;
        sessionInitialized = false;
        ensureSession();
    }

    private void ensureSession() {
        if (logFile != null) return;

        String player = "unknown";
        if (mc.player != null) player = mc.player.getName().getString();

        String server = "singleplayer";
        if (mc.getCurrentServer() != null) server = mc.getCurrentServer().ip;

        String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String time = new SimpleDateFormat("HH-mm-ss").format(new Date());

        String path = pathFormat.get()
            .replace("%server%", sanitize(server))
            .replace("%player%", sanitize(player))
            .replace("%date%", sanitize(date))
            .replace("%time%", sanitize(time));

        if (new File(path).isAbsolute()) logFile = new File(path);
        else logFile = new File(mc.gameDirectory, path);
    }

    private void log(String text) {
        try {
            ensureSession();
            if (logFile == null) return;

            if (!sessionInitialized) {
                File parent = logFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                sessionInitialized = true;
            }

            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            try (FileWriter writer = new FileWriter(logFile, true)) {
                writer.write("[" + timestamp + "] " + text + "\n");
            }
        } catch (IOException e) {
            error("Failed to write chat log: " + e.getMessage());
        }
    }

    private String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
