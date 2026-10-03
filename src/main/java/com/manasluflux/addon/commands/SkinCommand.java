package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.entity.player.PlayerSkin;

public class SkinCommand extends Command {
    public SkinCommand() {
        super("mfskin", "Prints the current skin texture for the local player.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            PlayerSkin skin = mc.player.getSkin();
            Msg.info("Skin for %s:", mc.player.getName().getString());
            Msg.info("Body: %s", skin.body().texturePath());
            Msg.info("Cape: %s", skin.cape().texturePath());
            return SINGLE_SUCCESS;
        });
    }
}
