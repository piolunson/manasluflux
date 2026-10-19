package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.stat.Stats;
import net.minecraft.stat.StatHandler;

public class StatCommand extends Command {
    public StatCommand() {
        super("mfstat", "Shows a few of your tracked stats (level, XP progress, play time, deaths).");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            StatHandler stats = mc.player.getStatHandler();
            int deaths = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS));
            int jumps = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.JUMP));
            int playTime = stats.getStat(Stats.CUSTOM.getOrCreateStat(Stats.PLAY_TIME));

            Msg.info("XP level: %d, progress: %.0f%%", mc.player.experienceLevel, mc.player.experienceProgress * 100);
            Msg.info("Deaths: %d, Jumps: %d, Play time: %d min", deaths, jumps, playTime / 20 / 60);
            return SINGLE_SUCCESS;
        });
    }
}
