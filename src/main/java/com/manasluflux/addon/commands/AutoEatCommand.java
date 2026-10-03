package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.manasluflux.addon.modules.AutoEat;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class AutoEatCommand extends Command {
    public AutoEatCommand() {
        super("mfautoeat", "Configures the AutoEat module: mfautoeat <food threshold> | mfautoeat off");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("threshold", IntegerArgumentType.integer(1, 19)).executes(context -> {
            int threshold = IntegerArgumentType.getInteger(context, "threshold");
            AutoEat module = Modules.get().get(AutoEat.class);
            module.setThreshold(threshold);
            if (!module.isActive()) module.toggle();
            Msg.info("AutoEat enabled - eats when food drops below §b%d§7.", threshold);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("off").executes(context -> {
            AutoEat module = Modules.get().get(AutoEat.class);
            if (module.isActive()) module.toggle();
            Msg.info("AutoEat disabled.");
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            AutoEat module = Modules.get().get(AutoEat.class);
            Msg.info("AutoEat is %s§7 (threshold: §b%d§7).", module.isActive() ? "§aon" : "§coff", module.getThreshold());
            return SINGLE_SUCCESS;
        });
    }
}
