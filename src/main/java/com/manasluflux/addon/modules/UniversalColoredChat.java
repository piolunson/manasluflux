package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;

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
        Text message = event.getMessage();
        if (message.getString().indexOf('&') == -1) return;

        MutableText replaced = Text.empty();
        message.visit((style, text) -> {
            replaced.append(Text.literal(text.replace('&', '\u00a7')).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);

        event.setMessage(replaced);
    }
}
