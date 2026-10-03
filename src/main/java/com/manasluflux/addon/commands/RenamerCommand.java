package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;

public class RenamerCommand extends Command {
    public RenamerCommand() {
        super("mfrename", "Renames the item in your hand (client-side only).");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("name", StringArgumentType.greedyString()).executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            String name = StringArgumentType.getString(context, "name");
            if (mc.player.getMainHandItem().isEmpty()) {
                Msg.error("Hold an item first.");
                return SINGLE_SUCCESS;
            }

            mc.player.getMainHandItem().set(DataComponents.CUSTOM_NAME, Component.literal(name));
            Msg.info("Renamed held item to §b%s§7 (client-side).", name);
            return SINGLE_SUCCESS;
        }));
    }
}
