package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerPlayer;

public class KillCommand extends Command {
    public KillCommand() {
        super("mfkill", "Kills your own player. Run 'mfkill confirm' to die.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            Msg.warning("This will kill you! Run 'mfkill confirm' to die.");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("confirm").executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            if (mc.player.isDeadOrDying()) {
                Msg.warning("You are already dead.");
                return SINGLE_SUCCESS;
            }

            // Singleplayer / LAN: kill the server-side player directly (real /kill damage).
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (serverPlayer != null) {
                    server.execute(() -> serverPlayer.kill(serverPlayer.level()));
                    Msg.info("Killed.");
                    return SINGLE_SUCCESS;
                }
            }

            // Multiplayer: the movement packets a client can send can't hurt you, so ask the
            // server instead - /suicide is allowed for everyone on most Essentials servers.
            mc.player.connection.sendCommand("suicide");
            Msg.info("Sent /suicide to the server. If it was denied, use /kill when you have permission.");
            return SINGLE_SUCCESS;
        }));
    }
}
