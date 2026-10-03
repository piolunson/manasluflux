package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.manasluflux.addon.modules.Waypoint;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.BlockPos;

public class WaypointCommand extends Command {
    public WaypointCommand() {
        super("mfwaypoint", "Adds, lists or clears waypoints for the Waypoint module (tracers/render).");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("add").then(argument("name", StringArgumentType.word()).executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            String name = StringArgumentType.getString(context, "name");
            Waypoint module = Modules.get().get(Waypoint.class);
            module.add(name, BlockPos.containing(mc.player.position()));
            Msg.info("Added waypoint %s at %d %d %d.", name,
                mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ());
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(context -> {
            Waypoint module = Modules.get().get(Waypoint.class);
            var entries = module.all();
            if (entries.isEmpty()) {
                Msg.info("No waypoints saved.");
                return SINGLE_SUCCESS;
            }
            Msg.info("Waypoints:");
            for (var e : entries.entrySet()) {
                Msg.info(" %s: %d %d %d", e.getKey(), e.getValue().getX(), e.getValue().getY(), e.getValue().getZ());
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            Waypoint module = Modules.get().get(Waypoint.class);
            int count = module.all().size();
            module.clear();
            Msg.info("Cleared %d waypoint(s).", count);
            return SINGLE_SUCCESS;
        }));
    }
}
