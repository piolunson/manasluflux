package com.manasluflux.addon.commands;

import com.manasluflux.addon.util.MfFakePlayer;
import com.manasluflux.addon.util.MfFakePlayerEntity;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/**
 * {@code .mffakeplayer} - spawns fake players the client treats as real players (own player-list
 * entry, in {@code world.getPlayers()}), so auras, player counts and anything else that only
 * reacts to real players picks them up.
 */
public class MfFakePlayerCommand extends Command {
    public MfFakePlayerCommand() {
        super("mffakeplayer", "Spawns fake players that the client treats as real players (auras target them). Subcommands: add, remove, clear, list, tp, health.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Msg.info("Usage: .mffakeplayer add [name] | remove <name> | clear | list | tp | health <hp>");
            Msg.info("Spawns where you look. They are real players for the client: own tab-list entry and world player list.");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("add").executes(context -> {
            add(null, 20, true);
            return SINGLE_SUCCESS;
        }).then(argument("name", StringArgumentType.word()).executes(context -> {
            add(StringArgumentType.getString(context, "name"), 20, true);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("remove").then(argument("name", StringArgumentType.word()).executes(context -> {
            String name = StringArgumentType.getString(context, "name");
            int removed = MfFakePlayer.remove(name);
            if (removed == 0) Msg.error("No fake player named %s.", name);
            else Msg.info("Removed %d fake player(s) named %s.", removed, name);
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("clear").executes(context -> {
            int count = MfFakePlayer.clear();
            Msg.info(count == 0 ? "No fake players to remove." : "Removed %d fake player(s).", count);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("list").executes(context -> {
            var all = MfFakePlayer.all();
            if (all.isEmpty()) {
                Msg.info("No fake players spawned.");
                return SINGLE_SUCCESS;
            }

            Msg.info("Fake players (%d):", all.size());
            for (MfFakePlayerEntity fake : all) {
                Msg.info(" %s - %.1f hp, at %d %d %d",
                    fake.getGameProfile().name(), fake.getHealth(),
                    fake.getBlockX(), fake.getBlockY(), fake.getBlockZ());
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("tp").executes(context -> {
            int count = MfFakePlayer.teleport();
            if (count == 0) Msg.error("No fake players spawned.");
            else Msg.info("Moved %d fake player(s) to your crosshair.", count);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("health").then(argument("hp", FloatArgumentType.floatArg(1, 10000)).executes(context -> {
            float hp = FloatArgumentType.getFloat(context, "hp");
            int count = MfFakePlayer.setHealth(hp);
            if (count == 0) Msg.error("No fake players spawned.");
            else Msg.info("Set %d fake player(s) to %.1f hp.", count, hp);
            return SINGLE_SUCCESS;
        })));
    }

    private void add(String name, float health, boolean copyInv) {
        if (mc.player == null || mc.world == null) {
            Msg.error("Not in a world.");
            return;
        }

        MfFakePlayerEntity fake = MfFakePlayer.add(name, health, copyInv);
        if (fake == null) {
            Msg.error("Could not spawn a fake player.");
            return;
        }

        Msg.info("Spawned fake player %s at %d %d %d - it is a real player for the client (tab list + player list).",
            fake.getGameProfile().name(), fake.getBlockX(), fake.getBlockY(), fake.getBlockZ());
    }
}
