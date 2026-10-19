package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class KillCommand extends Command {
    public KillCommand() {
        super("mfkill", "Kills your own player. Run 'mfkill confirm' to die.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Msg.warning("This will kill you! Run 'mfkill confirm' to die.");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("confirm").executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            if (mc.player.isDead()) {
                Msg.warning("You are already dead.");
                return SINGLE_SUCCESS;
            }

            // Singleplayer / LAN: kill the server-side player directly (real /kill damage).
            IntegratedServer server = mc.getServer();
            if (server != null) {
                ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(mc.player.getUuid());
                if (serverPlayer != null) {
                    server.execute(() -> serverPlayer.kill(serverPlayer.getEntityWorld()));
                    Msg.info("Killed.");
                    return SINGLE_SUCCESS;
                }
            }

            // Multiplayer: the movement packets a client can send can't hurt you, so ask the
            // server instead - /suicide is allowed for everyone on most Essentials servers.
            mc.player.networkHandler.sendChatCommand("suicide");
            Msg.info("Sent /suicide to the server. If it was denied, use /kill when you have permission.");
            return SINGLE_SUCCESS;
        }));
    }
}
