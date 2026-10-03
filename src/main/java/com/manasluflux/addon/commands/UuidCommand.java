package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class UuidCommand extends Command {
    public UuidCommand() {
        super("mfuuid", "Shows your in-game UUID.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            mc.keyboardHandler.setClipboard(mc.player.getStringUUID());
            Msg.info("UUID: §b%s§7 (copied to clipboard).", mc.player.getStringUUID());
            return SINGLE_SUCCESS;
        });
    }
}
