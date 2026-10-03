package com.manasluflux.addon.commands;

import com.manasluflux.addon.modules.AddText;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * .mfaddtext - local-only chat. Shows any text in your chat without sending anything to the server,
 * so nobody else sees it. Optionally kept after relogging via the (toggleable) add-text module.
 */
public class AddTextCommand extends Command {
    public AddTextCommand() {
        super("mfaddtext", "Local chat: .mfaddtext <text> shows text only for you, nothing is sent to the server.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            Msg.info("Usage: .mfaddtext <text> - shows the text in your chat only (client-side).");
            return SINGLE_SUCCESS;
        });

        builder.then(argument("text", StringArgumentType.greedyString()).executes(context -> {
            String text = StringArgumentType.getString(context, "text");
            if (text.isBlank()) {
                Msg.warning("Nothing to show.");
                return SINGLE_SUCCESS;
            }

            show(text);

            AddText module = Modules.get().get(AddText.class);
            if (module != null && module.isActive() && module.persist()) module.add(text);

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            AddText module = Modules.get().get(AddText.class);
            if (module != null) module.clear();
            Msg.info("Cleared saved local chat lines.");
            return SINGLE_SUCCESS;
        }));
    }

    /**
     * Shows the text in chat client-side without sending any packet. Goes through the normal
     * ChatComponent path, so other client-side modules (colored chat, mute) still apply to it.
     */
    public static void show(String text) {
        if (mc.player == null || mc.gui == null) return;

        mc.gui.chatListener().handleSystemMessage(Component.literal(text), false);
    }
}
