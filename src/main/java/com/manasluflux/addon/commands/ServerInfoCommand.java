package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class ServerInfoCommand extends Command {
    public ServerInfoCommand() {
        super("mfserver", "Shows info about the server you are connected to.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.getConnection() == null) {
                Msg.error("Not connected to a server.");
                return SINGLE_SUCCESS;
            }

            var serverData = mc.getCurrentServer();
            if (serverData != null) {
                Msg.info("Server: §b%s§7 (%s)", serverData.name, serverData.ip);
            } else {
                Msg.info("Singleplayer world.");
            }
            Msg.info("Players online: §b%d§7.", mc.getConnection().getOnlinePlayers().size());
            return SINGLE_SUCCESS;
        });
    }
}
