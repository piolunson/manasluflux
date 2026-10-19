package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;

public class DurabilityCommand extends Command {
    public DurabilityCommand() {
        super("mfdurability", "Shows durability of the item in your main hand.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandStack();
            if (stack.isEmpty()) {
                Msg.error("You are not holding anything.");
                return SINGLE_SUCCESS;
            }
            if (!stack.isDamageable()) {
                Msg.info("%s is not damageable.", stack.getName().getString());
                return SINGLE_SUCCESS;
            }

            int max = stack.getMaxDamage();
            int left = max - stack.getDamage();
            double percent = left * 100.0 / max;
            Msg.info("%s: %d/%d (%.0f%%)", stack.getName().getString(), left, max, percent);
            return SINGLE_SUCCESS;
        });
    }
}
