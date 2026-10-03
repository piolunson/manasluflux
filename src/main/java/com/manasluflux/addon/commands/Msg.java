package com.manasluflux.addon.commands;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public final class Msg {
    private Msg() {}

    private static String prefix() {
        return "[ManasluFlux] ";
    }

    public static void info(String message, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(prefix() + String.format(message, args)));
        }
    }

    public static void error(String message, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(prefix() + "Error: " + String.format(message, args)));
        }
    }

    public static void warning(String message, Object... args) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(prefix() + "Warning: " + String.format(message, args)));
        }
    }
}
