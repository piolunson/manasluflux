package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.List;

/**
 * Automatically logs you in on auth servers: sends "/<command> <password>" (e.g.
 * "/login 12345678") shortly after joining, and can also react to chat triggers like
 * "register" or "wrong password". The password is stored in the Meteor config file -
 * anyone with access to your config can read it, so don't reuse an important password.
 */
public class AutoLogin extends Module {
    public enum Mode {
        ALWAYS,
        TRIGGER
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> command = sgGeneral.add(new StringSetting.Builder()
        .name("command")
        .description("The login command to send, without the leading slash.")
        .defaultValue("login")
        .build()
    );

    private final Setting<String> password = sgGeneral.add(new StringSetting.Builder()
        .name("password")
        .description("The password to send with the command. Stored in plain text in the Meteor config - don't use an important password.")
        .defaultValue("12345678")
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Always send after joining, or only when a chat message contains a trigger word.")
        .defaultValue(Mode.ALWAYS)
        .build()
    );

    private final Setting<List<String>> triggers = sgGeneral.add(new StringListSetting.Builder()
        .name("triggers")
        .description("Chat words that make the module send the login command in TRIGGER mode.")
        .defaultValue(List.of("register", "log in", "logged in"))
        .visible(() -> mode.get() == Mode.TRIGGER)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Ticks to wait after joining before sending the command (20 ticks = 1 second).")
        .defaultValue(20)
        .min(0)
        .sliderMax(200)
        .build()
    );

    private final Setting<Boolean> repeat = sgGeneral.add(new BoolSetting.Builder()
        .name("repeat-on-trigger")
        .description("In TRIGGER mode, keep reacting to later chat messages too (e.g. 'wrong password'), not just the first one.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.TRIGGER)
        .build()
    );

    private final Setting<Integer> cooldown = sgGeneral.add(new IntSetting.Builder()
        .name("cooldown-seconds")
        .description("Minimum seconds between two sends so the server never sees spam.")
        .defaultValue(10)
        .min(1)
        .sliderMax(120)
        .build()
    );

    private int ticksUntilSend = -1;
    private long lastSentMs;

    public AutoLogin() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "auto-login", "Sends your login command with a password automatically after joining a server.");
    }

    @Override
    public void onActivate() {
        ticksUntilSend = -1;
        lastSentMs = 0;
    }

    @Override
    public void onDeactivate() {
        ticksUntilSend = -1;
    }

    @EventHandler
    private void onGameJoined(GameJoinedEvent event) {
        if (mode.get() == Mode.ALWAYS) ticksUntilSend = delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (ticksUntilSend < 0) return;
        if (ticksUntilSend-- == 0) sendLogin();
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (mc.player == null || mode.get() != Mode.TRIGGER) return;

        String message = event.getMessage().getString().toLowerCase();
        for (String trigger : triggers.get()) {
            if (!trigger.isEmpty() && message.contains(trigger.toLowerCase())) {
                if (repeat.get() || lastSentMs == 0) sendLogin();
                return;
            }
        }
    }

    private void sendLogin() {
        ticksUntilSend = -1;
        if (mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastSentMs < cooldown.get() * 1000L) return;

        String cmd = command.get().trim();
        if (cmd.isEmpty()) return;
        if (cmd.startsWith("/")) cmd = cmd.substring(1);

        mc.player.connection.sendCommand(cmd + " " + password.get());
        lastSentMs = now;

        info("Login command sent.");
    }
}
