package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.manasluflux.addon.modules.AutoLog;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class AutoLogCommand extends Command {
    public AutoLogCommand() {
        super("mfautolog", "Configures the AutoLog module: mfautolog <health%> | mfautolog off");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("health", IntegerArgumentType.integer(1, 99)).executes(context -> {
            int threshold = IntegerArgumentType.getInteger(context, "health");
            AutoLog module = Modules.get().get(AutoLog.class);
            module.setThreshold(threshold);
            if (!module.isActive()) module.toggle();
            Msg.info("AutoLog enabled - will disconnect below §c%d%%§7 health.", threshold);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("off").executes(context -> {
            AutoLog module = Modules.get().get(AutoLog.class);
            if (module.isActive()) module.toggle();
            Msg.info("AutoLog disabled.");
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            AutoLog module = Modules.get().get(AutoLog.class);
            Msg.info("AutoLog is %s§7 (threshold: §c%d%%§7).", module.isActive() ? "§aon" : "§coff", module.getThreshold());
            return SINGLE_SUCCESS;
        });
    }
}
