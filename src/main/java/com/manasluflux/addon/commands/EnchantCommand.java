package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.core.component.DataComponents;

public class EnchantCommand extends Command {
    public EnchantCommand() {
        super("mfenchant", "Toggles a fake enchant glow on the held item (visual only).");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("clear").executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;
            mc.player.getMainHandItem().remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            Msg.info("Removed fake glint from held item.");
            return SINGLE_SUCCESS;
        }));

        builder.executes(context -> {
            if (mc.player == null) {
                Msg.error("Not in a world.");
                return SINGLE_SUCCESS;
            }
            if (mc.player.getMainHandItem().isEmpty()) {
                Msg.error("Hold an item first.");
                return SINGLE_SUCCESS;
            }
            mc.player.getMainHandItem().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            Msg.info("Applied fake enchant glint (visual only - runs 'mfenchant clear' to remove).");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("level").then(argument("level", IntegerArgumentType.integer(1, 255)).executes(context -> {
            int level = IntegerArgumentType.getInteger(context, "level");
            Msg.warning("Client-side mods cannot grant real enchantments - level %d would need server-side cheats.", level);
            return SINGLE_SUCCESS;
        })));
    }
}
