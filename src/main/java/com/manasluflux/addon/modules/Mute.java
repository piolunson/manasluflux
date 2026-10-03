package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Mute extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> muted = sgGeneral.add(new StringListSetting.Builder()
        .name("muted-players")
        .description("Players whose chat messages will be hidden (also editable with .mfmute).")
        .defaultValue(List.of())
        .build()
    );

    private final Setting<List<String>> mutedPhrases = sgGeneral.add(new StringListSetting.Builder()
        .name("muted-phrases")
        .description("Chat messages containing any of these phrases will be hidden, regardless of sender (also editable with .mfmute phrase).")
        .defaultValue(List.of())
        .build()
    );

    public Mute() {
        super(AddonTemplate.CATEGORY, "mute", "Hides chat messages from muted players or containing muted phrases (client-side).");
    }

    public boolean mute(String name) {
        List<String> current = muted.get();
        if (current.stream().anyMatch(s -> s.equalsIgnoreCase(name))) return false;
        List<String> updated = new ArrayList<>(current);
        updated.add(name);
        muted.set(updated);
        return true;
    }

    public boolean unmute(String name) {
        List<String> current = muted.get();
        List<String> updated = current.stream().filter(s -> !s.equalsIgnoreCase(name)).collect(Collectors.toList());
        if (updated.size() == current.size()) return false;
        muted.set(updated);
        return true;
    }

    public Set<String> muted() {
        return muted.get().stream().collect(Collectors.toSet());
    }

    public boolean mutePhrase(String phrase) {
        String trimmed = phrase.trim();
        if (trimmed.isEmpty()) return false;
        List<String> current = mutedPhrases.get();
        if (current.stream().anyMatch(s -> s.equalsIgnoreCase(trimmed))) return false;
        List<String> updated = new ArrayList<>(current);
        updated.add(trimmed);
        mutedPhrases.set(updated);
        return true;
    }

    public boolean unmutePhrase(String phrase) {
        List<String> current = mutedPhrases.get();
        List<String> updated = current.stream().filter(s -> !s.equalsIgnoreCase(phrase.trim())).collect(Collectors.toList());
        if (updated.size() == current.size()) return false;
        mutedPhrases.set(updated);
        return true;
    }

    public Set<String> mutedPhrases() {
        return mutedPhrases.get().stream().collect(Collectors.toSet());
    }

    public void clearPhrases() {
        mutedPhrases.set(List.of());
    }

    public void clear() {
        muted.set(List.of());
    }

    private boolean isMuted(String text) {
        String lower = text.toLowerCase();

        boolean playerMatch = muted.get().stream().anyMatch(name -> {
            String n = name.toLowerCase();
            return n.length() > 0 && lower.contains(n);
        });
        if (playerMatch) return true;

        return mutedPhrases.get().stream().anyMatch(phrase -> {
            String p = phrase.toLowerCase();
            return p.length() > 0 && lower.contains(p);
        });
    }

    /**
     * Filters the final displayed chat message (player chat and system chat alike) - the full
     * decorated line including the sender name, so the plain contains-checks below work.
     */
    @EventHandler
    private void onMessageReceive(ReceiveMessageEvent event) {
        if (isMuted(event.getMessage().getString())) event.cancel();
    }
}
