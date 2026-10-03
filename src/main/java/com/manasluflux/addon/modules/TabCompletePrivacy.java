package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.List;

/**
 * Port of RyanWare's TabCompletePrivacy (SmilerRyan): cancels tab-complete suggestion packets
 * that would leak private or dangerous commands to the server - either all of them or only
 * ones matching your prefixes, words or symbols.
 */
public class TabCompletePrivacy extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> blockAll = sgGeneral.add(new BoolSetting.Builder()
        .name("block-all")
        .description("Blocks all tab completion packets.")
        .defaultValue(false)
        .build()
    );

    private final Setting<List<String>> blockedPrefixes = sgGeneral.add(new StringListSetting.Builder()
        .name("blocked-prefixes")
        .description("Block tab completion when the command starts with one of these prefixes.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> blockedWords = sgGeneral.add(new StringListSetting.Builder()
        .name("blocked-words")
        .description("Block tab completion when the command contains one of these words.")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<String> blockedSymbols = sgGeneral.add(new StringSetting.Builder()
        .name("blocked-symbols")
        .description("Block tab completion when the command contains any of these symbols.")
        .defaultValue("@[]{}=")
        .build()
    );

    public TabCompletePrivacy() {
        super(AddonTemplate.REWRITE_CATEGORY, "tab-complete-privacy", "Blocks tab completion packets containing private or dangerous input.");
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!(event.packet instanceof ServerboundCommandSuggestionPacket packet)) return;

        String command = packet.getCommand();
        String lower = command.toLowerCase();

        if (blockAll.get()) {
            event.cancel();
            return;
        }

        for (String prefix : blockedPrefixes.get()) {
            if (!prefix.isEmpty() && lower.startsWith(prefix.toLowerCase())) {
                event.cancel();
                return;
            }
        }

        for (String word : blockedWords.get()) {
            if (!word.isEmpty() && lower.contains(word.toLowerCase())) {
                event.cancel();
                return;
            }
        }

        for (char symbol : blockedSymbols.get().toCharArray()) {
            if (command.indexOf(symbol) != -1) {
                event.cancel();
                return;
            }
        }
    }
}
