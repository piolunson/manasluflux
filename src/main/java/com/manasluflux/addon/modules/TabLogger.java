package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Port of RyanWare's TabLogger (SmilerRyan): keeps a persistent per-server history of every
 * player that appears in the tab list, logging uuid, name and every recorded ping value.
 * File I/O happens on a background thread. Rewritten for MC 26.2.
 */
public class TabLogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> ignoreZeroPing = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-zero-ping")
        .description("Ignore players with 0 ping (usually NPCs or just-loaded entries).")
        .defaultValue(true)
        .build()
    );

    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ManasluFlux-TabLogger");
        t.setDaemon(true);
        return t;
    });

    private Set<Snapshot> lastSnapshots = new HashSet<>();

    public TabLogger() {
        super(AddonTemplate.REWRITE_CATEGORY, "tab-logger", "Logs the tab list (uuid, name, ping history) to a per-server text file.");
    }

    @Override
    public void onDeactivate() {
        lastSnapshots = new HashSet<>();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null || mc.player == null || mc.getConnection() == null) return;

        Set<Snapshot> current = new HashSet<>();
        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            if (entry.getProfile() == null || entry.getProfile().id() == null || entry.getProfile().name() == null) continue;

            String uuid = entry.getProfile().id().toString();
            String name = entry.getProfile().name();
            if (uuid.isEmpty() || name.isEmpty()) continue;

            int ping = entry.getLatency();
            if (ignoreZeroPing.get() && ping == 0) continue;

            current.add(new Snapshot(uuid, name, ping));
        }

        if (current.equals(lastSnapshots)) return;

        Set<Snapshot> toProcess = new HashSet<>(current);
        lastSnapshots = current;

        String serverIp = mc.getCurrentServer() != null ? mc.getCurrentServer().ip.replaceAll(":", "_") : "singleplayer";
        logExecutor.submit(() -> processAndWriteLogs(toProcess, serverIp));
    }

    private void processAndWriteLogs(Set<Snapshot> snapshots, String serverIp) {
        try {
            File folder = new File(mc.gameDirectory, "manasluflux" + File.separator + "tab-logger");
            if (!folder.exists()) folder.mkdirs();

            File logFile = new File(folder, serverIp + ".txt");

            List<String> lines = logFile.exists()
                ? new ArrayList<>(Files.readAllLines(logFile.toPath(), StandardCharsets.UTF_8))
                : new ArrayList<>();

            boolean modified = false;

            for (Snapshot snapshot : snapshots) {
                boolean found = false;

                for (int i = 0; i < lines.size(); i++) {
                    String[] parts = lines.get(i).split(",", -1);
                    if (parts.length < 2 || !parts[0].equalsIgnoreCase(snapshot.uuid) || !parts[1].equalsIgnoreCase(snapshot.name)) continue;

                    found = true;
                    int lastPing = -1;
                    try {
                        lastPing = Integer.parseInt(parts[parts.length - 1]);
                    } catch (NumberFormatException ignored) {}

                    if (lastPing != snapshot.ping) {
                        lines.set(i, lines.get(i) + "," + snapshot.ping);
                        modified = true;
                    }
                    break;
                }

                if (!found) {
                    lines.add(snapshot.uuid + "," + snapshot.name + "," + snapshot.ping);
                    modified = true;
                }
            }

            if (modified) Files.write(logFile.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            warning("TabLogger failed to write: " + e.getMessage());
        }
    }

    private static final class Snapshot {
        final String uuid;
        final String name;
        final int ping;

        Snapshot(String uuid, String name, int ping) {
            this.uuid = uuid;
            this.name = name;
            this.ping = ping;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Snapshot that)) return false;
            return ping == that.ping && uuid.equals(that.uuid) && name.equals(that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(uuid, name, ping);
        }
    }
}
