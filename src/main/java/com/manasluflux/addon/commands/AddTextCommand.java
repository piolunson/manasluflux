package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

/**
 * .mfaddtext - local-only chat. Shows any text in your chat without sending anything to the server,
 * so nobody else sees it. This is the whole feature: it is a command only, with no module behind it.
 */
public class AddTextCommand extends Command {
    public AddTextCommand() {
        super("mfaddtext", "Local chat: .mfaddtext <text> shows text only for you, nothing is sent to the server.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
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

            return SINGLE_SUCCESS;
        }));
    }

    /**
     * Shows the text in chat client-side without sending any packet. Goes through the normal
     * ChatComponent path, so other client-side modules (colored chat, mute) still apply to it.
     */
    public static void show(String text) {
        if (mc.player == null || mc.inGameHud == null) return;

        mc.inGameHud.getChatHud().addMessage(Text.literal(text));
    }
}
