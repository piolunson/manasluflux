package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of RyanWare's AutoRespawn (SmilerRyan): automatically requests a respawn while
 * on the death screen, then optionally runs a list of commands/chat lines after
 * respawning, one per tick.
 */
public class AutoRespawn extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> commands = sgGeneral.add(new StringListSetting.Builder()
        .name("commands")
        .description("Commands (or chat lines) to run after respawning. One is sent per tick. Lines starting with / are commands.")
        .defaultValue()
        .build()
    );

    private final List<String> commandQueue = new ArrayList<>();
    private int commandIndex;
    private int joinGraceTicks;
    private boolean waitingForCommands;
    private boolean worldActive;

    public AutoRespawn() {
        super(AddonTemplate.REWRITE_CATEGORY, "mf-auto-respawn", "Automatically respawns you on the death screen and can run commands afterwards.");
    }

    @Override
    public void onActivate() {
        worldActive = false;
        resetState();
    }

    @Override
    public void onDeactivate() {
        worldActive = false;
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null) return;

        // Ignore the initial join/respawn burst so old commands aren't replayed.
        if (!worldActive) {
            worldActive = true;
            resetState();
            return;
        }

        if (mc.player.isDeadOrDying()) {
            mc.player.respawn();
            waitingForCommands = true;
            commandQueue.clear();
            commandQueue.addAll(commands.get());
            commandIndex = 0;
            return;
        }

        if (joinGraceTicks < 20) {
            joinGraceTicks++;
            return;
        }

        if (waitingForCommands && commandIndex < commandQueue.size()) {
            String line = commandQueue.get(commandIndex);
            if (line != null && !line.isBlank()) {
                String message = line.trim();
                if (message.startsWith("/")) mc.player.connection.sendCommand(message.substring(1));
                else mc.player.connection.sendChat(message);
            }
            commandIndex++;
            if (commandIndex >= commandQueue.size()) waitingForCommands = false;
        }
    }

    private void resetState() {
        waitingForCommands = false;
        commandQueue.clear();
        commandIndex = 0;
        joinGraceTicks = 0;
    }
}
