package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Port of RyanWare's AutoResponder (SmilerRyan): watches incoming chat and replies when a
 * message contains one of your trigger words - "trigger=response" pairs, multiple matches
 * pick a random response. Rewritten for MC 26.2 with an anti-loop cooldown; lines starting
 * with / are sent as commands.
 */
public class AutoResponder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> triggerResponses = sgGeneral.add(new StringListSetting.Builder()
        .name("trigger-responses")
        .description("Trigger=response pairs. If a chat message contains a trigger, a matching response is sent (random pick on multiple matches).")
        .defaultValue("yo=Yo.", "To cancel this request=Accept.")
        .build()
    );

    private final Setting<Integer> cooldownSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown")
        .description("Seconds to wait between responses so you don't end up in a reply loop.")
        .defaultValue(3)
        .min(0)
        .sliderMax(30)
        .build()
    );

    private final Random random = new Random();
    private long lastResponseMs;

    public AutoResponder() {
        super(AddonTemplate.REWRITE_CATEGORY, "auto-responder", "Reads chat and automatically responds to messages containing your trigger words.");
    }

    @Override
    public void onActivate() {
        lastResponseMs = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastResponseMs < cooldownSeconds.get() * 1000L) return;

        String message = event.getMessage().getString();

        List<String> possibleResponses = new ArrayList<>();
        for (String pair : triggerResponses.get()) {
            int idx = pair.indexOf('=');
            if (idx == -1) continue;

            String trigger = pair.substring(0, idx);
            String response = pair.substring(idx + 1);
            if (!trigger.isEmpty() && message.contains(trigger)) possibleResponses.add(response);
        }

        if (possibleResponses.isEmpty()) return;

        String response = possibleResponses.get(random.nextInt(possibleResponses.size())).trim();
        if (response.isEmpty()) return;

        if (response.startsWith("/")) mc.player.connection.sendCommand(response.substring(1));
        else mc.player.connection.sendChat(response);

        lastResponseMs = now;
    }
}
