package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class PingCommand extends Command {
    public PingCommand() {
        super("mfping", "Shows your current latency to the server.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.getConnection() != null && mc.player != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                int ping = info == null ? 0 : info.getLatency();
                Msg.info("Ping: %s%dms§7.", ping <= 0 ? "§a" : ping < 100 ? "§a" : ping < 250 ? "§e" : "§c", ping);
            } else {
                Msg.error("Not connected to a server.");
            }
            return SINGLE_SUCCESS;
        });
    }
}
