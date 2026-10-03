package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class FpsCommand extends Command {
    public FpsCommand() {
        super("mffps", "Shows the current client FPS.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            int fps = mc.getFps();
            Msg.info("FPS: §b%d§7.", fps);
            return SINGLE_SUCCESS;
        });
    }
}
