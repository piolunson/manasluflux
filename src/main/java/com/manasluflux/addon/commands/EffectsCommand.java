package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.Collection;

public class EffectsCommand extends Command {
    public EffectsCommand() {
        super("mfeffects", "Lists your active potion effects.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }

            Collection<StatusEffectInstance> effects = mc.player.getStatusEffects();
            if (effects.isEmpty()) {
                Msg.info("No active effects.");
                return SINGLE_SUCCESS;
            }

            StringBuilder sb = new StringBuilder("Active effects: ");
            boolean first = true;
            for (StatusEffectInstance instance : effects) {
                if (!first) sb.append(", ");
                sb.append(instance.getEffectType().value().getName().getString())
                    .append(" (").append(instance.getAmplifier() + 1)
                    .append(", ").append(instance.getDuration() / 20).append("s)");
                first = false;
            }
            Msg.info(sb.toString());
            return SINGLE_SUCCESS;
        });
    }
}
