package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * AddText - pairs with the .mfaddtext command. When enabled with "save-texts" on, lines added
 * with .mfaddtext are kept and re-shown when you (re)join a world, so your local chat survives relogs.
 */
public class AddText extends Module {
    private static final int MAX_SAVED = 100;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> saveTexts = sgGeneral.add(new BoolSetting.Builder()
        .name("save-texts")
        .description("Keeps .mfaddtext lines and shows them again when you (re)join a world.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> clearOnLeave = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-on-leave")
        .description("Clears saved lines when you leave a world instead of keeping them for the next join.")
        .defaultValue(false)
        .build()
    );

    private final Deque<String> lines = new ArrayDeque<>();

    public AddText() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "add-text", "Local-only chat lines added with .mfaddtext, optionally kept across relogs.");
    }

    @Override
    public void onActivate() {
        reshuffle();
    }

    public boolean persist() {
        return saveTexts.get();
    }

    public void add(String line) {
        lines.addLast(line);
        while (lines.size() > MAX_SAVED) lines.removeFirst();
    }

    public void clear() {
        lines.clear();
    }

    private void reshuffle() {
        // Vanilla clears chat on disconnect; re-add saved lines with fresh timestamps.
        List<String> saved = List.copyOf(lines);
        lines.clear();
        for (String line : saved) {
            com.manasluflux.addon.commands.AddTextCommand.show(line);
            lines.addLast(line);
        }
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        reshuffle();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (clearOnLeave.get()) lines.clear();
    }
}
