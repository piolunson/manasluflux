package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.manasluflux.addon.modules.Path;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;

public class PathCommand extends Command {
    public PathCommand() {
        super("mfpath", "Points a beacon line to a target position: mfpath <x> <y> <z> | mfpath off");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(argument("x", IntegerArgumentType.integer())
            .then(argument("y", IntegerArgumentType.integer())
                .then(argument("z", IntegerArgumentType.integer()).executes(context -> {
                    if (mc.player == null) {
                        Msg.error("Not in a world.");
                        return SINGLE_SUCCESS;
                    }
                    int x = IntegerArgumentType.getInteger(context, "x");
                    int y = IntegerArgumentType.getInteger(context, "y");
                    int z = IntegerArgumentType.getInteger(context, "z");

                    Path module = Modules.get().get(Path.class);
                    module.setTarget(new BlockPos(x, y, z));
                    if (!module.isActive()) module.toggle();
                    Msg.info("Path set to §b%d %d %d§7.", x, y, z);
                    return SINGLE_SUCCESS;
                }))));

        builder.then(literal("off").executes(context -> {
            Path module = Modules.get().get(Path.class);
            module.setTarget(null);
            if (module.isActive()) module.toggle();
            Msg.info("Path disabled.");
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            Path module = Modules.get().get(Path.class);
            BlockPos target = module.getTarget();
            if (target == null) {
                Msg.info("No path target. Usage: mfpath <x> <y> <z>");
            } else {
                Msg.info("Current target: §b%d %d %d§7.", target.getX(), target.getY(), target.getZ());
            }
            return SINGLE_SUCCESS;
        });
    }
}
