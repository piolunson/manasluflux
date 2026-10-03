package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.List;
import java.util.Random;

/**
 * Port of RyanWare's DeathCommands (SmilerRyan): sends a random message (or command) from a
 * list a configurable delay after you die, with a configurable chance. Rewritten for MC 26.2.
 */
public class DeathCommands extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ticks")
        .description("Minimum ticks to wait before sending the message (20 ticks = 1 second).")
        .defaultValue(0)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ticks")
        .description("Maximum ticks to wait before sending the message (20 ticks = 1 second).")
        .defaultValue(40)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Double> chance = sgGeneral.add(new DoubleSetting.Builder()
        .name("chance")
        .description("Chance to send the message when you die (0.0 = never, 1.0 = always).")
        .defaultValue(1.0)
        .min(0.0)
        .max(1.0)
        .sliderMax(1.0)
        .build()
    );

    private final Setting<List<String>> deathMessages = sgGeneral.add(new StringListSetting.Builder()
        .name("death-messages")
        .description("Messages to randomly choose from when dying. Lines starting with / are sent as commands.")
        .defaultValue(List.of("gg", "rip", ",", ",,,"))
        .build()
    );

    private final Random random = new Random();

    private boolean wasAlive = true;
    private int deathDelayTicks = -1;
    private String pendingMessage;

    public DeathCommands() {
        super(AddonTemplate.REWRITE_CATEGORY, "death-commands", "Sends a random message or command when you die.");
    }

    @Override
    public void onActivate() {
        resetState();
    }

    @Override
    public void onDeactivate() {
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        boolean isAlive = mc.player.getHealth() > 0;
        if (wasAlive && !isAlive && random.nextDouble() < chance.get()) {
            int min = Math.min(minDelay.get(), maxDelay.get());
            int max = Math.max(minDelay.get(), maxDelay.get());
            deathDelayTicks = min + random.nextInt(max - min + 1);

            List<String> messages = deathMessages.get();
            pendingMessage = messages.isEmpty() ? null : messages.get(random.nextInt(messages.size()));
        }
        wasAlive = isAlive;

        if (deathDelayTicks >= 0 && pendingMessage != null) {
            if (deathDelayTicks == 0) {
                String message = pendingMessage.trim();
                if (message.startsWith("/")) mc.player.connection.sendCommand(message.substring(1));
                else mc.player.connection.sendChat(message);

                pendingMessage = null;
                deathDelayTicks = -1;
            } else {
                deathDelayTicks--;
            }
        }
    }

    private void resetState() {
        wasAlive = true;
        deathDelayTicks = -1;
        pendingMessage = null;
    }
}
