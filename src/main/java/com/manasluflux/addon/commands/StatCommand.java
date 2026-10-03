package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;

public class StatCommand extends Command {
    public StatCommand() {
        super("mfstat", "Shows a few of your tracked stats (level, XP progress, play time, deaths).");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            StatsCounter stats = mc.player.getStats();
            int deaths = stats.getValue(Stats.CUSTOM, Stats.DEATHS);
            int jumps = stats.getValue(Stats.CUSTOM, Stats.JUMP);
            int playTime = stats.getValue(Stats.CUSTOM, Stats.PLAY_TIME);

            Msg.info("XP level: %d, progress: %.0f%%", mc.player.experienceLevel, mc.player.experienceProgress * 100);
            Msg.info("Deaths: %d, Jumps: %d, Play time: %d min", deaths, jumps, playTime / 20 / 60);
            return SINGLE_SUCCESS;
        });
    }
}
