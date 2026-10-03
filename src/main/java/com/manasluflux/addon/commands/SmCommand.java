package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * .mfsm - situation module switcher. Toggles any module (ManasluFlux's or Meteor's) by name,
 * with tab-completion suggestions (podpowiedzi) showing Polish on/off state in the tooltip.
 */
public class SmCommand extends Command {
    private static final SuggestionProvider<ClientSuggestionProvider> MODULE_SUGGESTIONS = (context, suggestions) -> {
        String remaining = suggestions.getRemainingLowerCase();
        for (Module module : Modules.get().getAll()) {
            boolean nameMatch = module.name.toLowerCase(Locale.ROOT).startsWith(remaining);
            boolean titleMatch = module.title.toLowerCase(Locale.ROOT).contains(remaining);
            if (remaining.isEmpty() || nameMatch || titleMatch) {
                String state = module.isActive()
                    ? ChatFormatting.GREEN + "włączony"
                    : ChatFormatting.RED + "wyłączony";
                suggestions.suggest(module.name, Component.literal(module.title + " — " + state));
            }
        }
        return suggestions.buildFuture();
    };

    private static final SuggestionProvider<ClientSuggestionProvider> STATE_SUGGESTIONS = (context, suggestions) -> {
        suggestions.suggest("on", Component.literal("włącz (on)"));
        suggestions.suggest("off", Component.literal("wyłącz (off)"));
        suggestions.suggest("toggle", Component.literal("przełącz (toggle)"));
        return suggestions.buildFuture();
    };

    public SmCommand() {
        super("mfsm", "Situation module switcher: .mfsm <module> [on|off|toggle] - tab suggestions included.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(context -> {
            int active = Modules.get().getActive().size();
            int total = Modules.get().getCount();
            Msg.info("Usage: .mfsm <module> [on|off|toggle] - %d/%d modules active.", active, total);
            return SINGLE_SUCCESS;
        });

        builder.then(argument("module", StringArgumentType.word())
            .suggests(MODULE_SUGGESTIONS)
            .executes(context -> {
                Module module = Modules.get().get(StringArgumentType.getString(context, "module"));
                if (module == null) {
                    Msg.error("Module not found - use tab completion for suggestions.");
                    return SINGLE_SUCCESS;
                }

                module.toggle();
                Msg.info("%s: %s", module.title, module.isActive() ? "włączony (on)" : "wyłączony (off)");
                return SINGLE_SUCCESS;
            })
            .then(argument("state", StringArgumentType.word())
                .suggests(STATE_SUGGESTIONS)
                .executes(context -> {
                    Module module = Modules.get().get(StringArgumentType.getString(context, "module"));
                    if (module == null) {
                        Msg.error("Module not found - use tab completion for suggestions.");
                        return SINGLE_SUCCESS;
                    }

                    String state = StringArgumentType.getString(context, "state").toLowerCase(Locale.ROOT);
                    switch (state) {
                        case "on", "wl", "wlacz", "włącz" -> {
                            if (!module.isActive()) module.toggle();
                        }
                        case "off", "wyl", "wylacz", "wyłącz" -> {
                            if (module.isActive()) module.toggle();
                        }
                        case "toggle", "t" -> module.toggle();
                        default -> {
                            Msg.error("Unknown state '%s' - use on, off or toggle.", state);
                            return SINGLE_SUCCESS;
                        }
                    }

                    Msg.info("%s: %s", module.title, module.isActive() ? "włączony (on)" : "wyłączony (off)");
                    return SINGLE_SUCCESS;
                })));
    }
}
