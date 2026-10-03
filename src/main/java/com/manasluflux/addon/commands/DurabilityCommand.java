package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.item.ItemStack;

public class DurabilityCommand extends Command {
    public DurabilityCommand() {
        super("mfdurability", "Shows durability of the item in your main hand.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            ItemStack stack = mc.player.getMainHandItem();
            if (stack.isEmpty()) {
                Msg.error("You are not holding anything.");
                return SINGLE_SUCCESS;
            }
            if (!stack.isDamageableItem()) {
                Msg.info("%s is not damageable.", stack.getHoverName().getString());
                return SINGLE_SUCCESS;
            }

            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            double percent = left * 100.0 / max;
            Msg.info("%s: %d/%d (%.0f%%)", stack.getHoverName().getString(), left, max, percent);
            return SINGLE_SUCCESS;
        });
    }
}
