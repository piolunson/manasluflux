package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class DayCommand extends Command {
    public DayCommand() {
        super("mfday", "Displays the current in-game day and time of day.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.level == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            long dayTime = mc.level.getOverworldClockTime();
            long day = dayTime / 24000L + 1;
            long ticks = dayTime % 24000L;
            int hours = (int) ((ticks / 1000L + 6) % 24);
            int minutes = (int) (ticks % 1000L * 60L / 1000L);
            Msg.info("Day %d, time %02d:%02d (%d ticks into the day).", day, hours, minutes, ticks);
            return SINGLE_SUCCESS;
        });
    }
}
