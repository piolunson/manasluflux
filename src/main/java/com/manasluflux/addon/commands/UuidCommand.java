package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class UuidCommand extends Command {
    public UuidCommand() {
        super("mfuuid", "Shows your in-game UUID.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            mc.keyboard.setClipboard(mc.player.getUuidAsString());
            Msg.info("UUID: §b%s§7 (copied to clipboard).", mc.player.getUuidAsString());
            return SINGLE_SUCCESS;
        });
    }
}
