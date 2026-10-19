package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.text.Text;

public class RenamerCommand extends Command {
    public RenamerCommand() {
        super("mfrename", "Renames the item in your hand (client-side only).");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("name", StringArgumentType.greedyString()).executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            String name = StringArgumentType.getString(context, "name");
            if (mc.player.getMainHandStack().isEmpty()) {
                Msg.error("Hold an item first.");
                return SINGLE_SUCCESS;
            }

            mc.player.getMainHandStack().set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
            Msg.info("Renamed held item to §b%s§7 (client-side).", name);
            return SINGLE_SUCCESS;
        }));
    }
}
