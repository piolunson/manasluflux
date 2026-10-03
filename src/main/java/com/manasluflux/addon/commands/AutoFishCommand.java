package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.manasluflux.addon.modules.AutoFish;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class AutoFishCommand extends Command {
    public AutoFishCommand() {
        super("mfautofish", "Toggles the AutoFish module: mfautofish on|off|status");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("on").executes(context -> {
            AutoFish module = Modules.get().get(AutoFish.class);
            if (!module.isActive()) module.toggle();
            Msg.info("AutoFish enabled - hold a fishing rod and cast it.");
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("off").executes(context -> {
            AutoFish module = Modules.get().get(AutoFish.class);
            if (module.isActive()) module.toggle();
            Msg.info("AutoFish disabled.");
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            AutoFish module = Modules.get().get(AutoFish.class);
            Msg.info("AutoFish is %s§7.", module.isActive() ? "§aon" : "§coff");
            return SINGLE_SUCCESS;
        });
    }
}
