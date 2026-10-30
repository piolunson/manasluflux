package com.manasluflux.addon.commands;

import com.manasluflux.addon.gui.UpdateScreen;
import com.manasluflux.addon.update.UpdateChecker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

/**
 * .mfupdate - opens the updater and forces a version check right away (the detector already
 * checks every 30 seconds on its own).
 */
public class MfUpdateCommand extends Command {
    public MfUpdateCommand() {
        super("mfupdate", "ManasluFlux updater: check for a new version and open the update window.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            UpdateChecker.INSTANCE.checkNow();
            mc.setScreen(new UpdateScreen());
            return SINGLE_SUCCESS;
        });
    }
}
