package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;

public class HealCommand extends Command {
    public HealCommand() {
        super("mfheal", "Shows how much food and health you are missing (client-side info only).");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            float missing = mc.player.getMaxHealth() - mc.player.getHealth();
            int missingFood = 20 - mc.player.getHungerManager().getFoodLevel();

            ItemStack offhand = mc.player.getOffHandStack();
            boolean hasGoldenApple = !offhand.isEmpty() && offhand.getItem().getTranslationKey().contains("golden_apple");

            if (missing <= 0 && missingFood == 0) {
                Msg.info("You are at full health and food.");
            } else {
                Msg.info("Missing §c%.1f§7 health, §6%d§7 food.%s", missing, missingFood,
                    hasGoldenApple ? " §a(Golden apple in offhand!)" : "");
            }
            return SINGLE_SUCCESS;
        });
    }
}
