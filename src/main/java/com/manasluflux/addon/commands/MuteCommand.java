package com.manasluflux.addon.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.manasluflux.addon.modules.Mute;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

public class MuteCommand extends Command {
    public MuteCommand() {
        super("mfmute", "Client-side chat filter: mfmute add|remove|list|clear [player], mfmute phrase add|remove|list|clear [phrase]");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.then(literal("add").then(argument("player", StringArgumentType.word()).executes(context -> {
            String name = StringArgumentType.getString(context, "player");
            Mute module = Modules.get().get(Mute.class);
            if (module.mute(name)) {
                Msg.info("Muted §b%s§7 (client-side).", name);
            } else {
                Msg.warning("%s is already muted.", name);
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("remove").then(argument("player", StringArgumentType.word()).executes(context -> {
            String name = StringArgumentType.getString(context, "player");
            Mute module = Modules.get().get(Mute.class);
            if (module.unmute(name)) {
                Msg.info("Unmuted §b%s§7.", name);
            } else {
                Msg.warning("%s was not muted.", name);
            }
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(context -> {
            Mute module = Modules.get().get(Mute.class);
            var muted = module.muted();
            if (muted.isEmpty()) {
                Msg.info("Nobody is muted.");
            } else {
                Msg.info("Muted players: §b%s§7.", String.join("§7, §b", muted));
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            Mute module = Modules.get().get(Mute.class);
            int count = module.muted().size();
            module.clear();
            Msg.info("Unmuted %d player(s).", count);
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("phrase")
            .then(literal("add").then(argument("phrase", StringArgumentType.greedyString()).executes(context -> {
                String phrase = StringArgumentType.getString(context, "phrase");
                Mute module = Modules.get().get(Mute.class);
                if (module.mutePhrase(phrase)) {
                    Msg.info("Muted phrase \u00a7b%s\u00a77 (client-side).", phrase.trim());
                } else {
                    Msg.warning("Phrase is empty or already muted.");
                }
                return SINGLE_SUCCESS;
            })))
            .then(literal("remove").then(argument("phrase", StringArgumentType.greedyString()).executes(context -> {
                String phrase = StringArgumentType.getString(context, "phrase");
                Mute module = Modules.get().get(Mute.class);
                if (module.unmutePhrase(phrase)) {
                    Msg.info("Unmuted phrase \u00a7b%s\u00a77.", phrase.trim());
                } else {
                    Msg.warning("That phrase was not muted.");
                }
                return SINGLE_SUCCESS;
            })))
            .then(literal("list").executes(context -> {
                Mute module = Modules.get().get(Mute.class);
                var phrases = module.mutedPhrases();
                if (phrases.isEmpty()) {
                    Msg.info("No phrases are muted.");
                } else {
                    Msg.info("Muted phrases: \u00a7b%s\u00a77.", String.join("\u00a77, \u00a7b", phrases));
                }
                return SINGLE_SUCCESS;
            }))
            .then(literal("clear").executes(context -> {
                Mute module = Modules.get().get(Mute.class);
                int count = module.mutedPhrases().size();
                module.clearPhrases();
                Msg.info("Unmuted %d phrase(s).", count);
                return SINGLE_SUCCESS;
            }))
        );
    }
}
