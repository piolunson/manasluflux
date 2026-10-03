package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("mfcoords", "Copies your coordinates to the clipboard and prints them.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            String coords = String.format("%.0f %d %.0f", mc.player.getX(), mc.player.getBlockY(), mc.player.getZ());
            mc.keyboardHandler.setClipboard(coords);
            Msg.info("Coordinates: §b%s§7 (copied to clipboard).", coords);
            return SINGLE_SUCCESS;
        });
    }
}
