package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * Literally replaces every & in incoming chat with the section sign (which Minecraft renders as
 * color codes). Nothing else - no settings, no parsing, on = replace, off = don't.
 */
public class UniversalColoredChat extends Module {
    public UniversalColoredChat() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "universal-colored-chat", "Replaces every & in chat with the color code sign - nothing else, no settings.");
    }

    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        Component message = event.getMessage();
        if (message.getString().indexOf('&') == -1) return;

        MutableComponent replaced = Component.empty();
        message.visit((style, text) -> {
            replaced.append(Component.literal(text.replace('&', '\u00a7')).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        event.setMessage(replaced);
    }
}
