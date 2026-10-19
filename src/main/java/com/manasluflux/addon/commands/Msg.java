package com.manasluflux.addon.commands;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public final class Msg {
    private Msg() {}

    private static String prefix() {
        return "[ManasluFlux] ";
    }

    public static void info(String message, Object... args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(prefix() + String.format(message, args)), false);
        }
    }

    public static void error(String message, Object... args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(prefix() + "Error: " + String.format(message, args)), false);
        }
    }

    public static void warning(String message, Object... args) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal(prefix() + "Warning: " + String.format(message, args)), false);
        }
    }
}
