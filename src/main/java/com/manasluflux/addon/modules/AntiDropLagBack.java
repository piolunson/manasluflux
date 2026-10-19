package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.commands.Msg;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.entity.EntityPosition;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Detects the position-sync teleports a vanilla server sends when its anticheat
 * "rubberbands" the player (server snaps you back to where it thinks you are).
 * Corrections are counted inside a sliding real-time window; going over the cap
 * activates a PAUSE: every other active module is deactivated for a configured
 * number of ticks (so whatever causes the rubberbands stops), then everything
 * is switched back on. A chat notifier reports each rubberband and each pause.
 * Purely client-side: packets are observed, never modified or cancelled.
 */
public class AntiDropLagBack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> maxRubberbands = sgGeneral.add(new IntSetting.Builder()
        .name("rubberbands")
        .description("How many correction teleports inside the window it takes before modules are paused.")
        .defaultValue(3)
        .min(1)
        .sliderMax(50)
        .build()
    );

    private final Setting<Integer> windowSeconds = sgGeneral.add(new IntSetting.Builder()
        .name("window-seconds")
        .description("Sliding window in real seconds that the rubberband count is measured in.")
        .defaultValue(5)
        .min(1)
        .sliderMax(60)
        .build()
    );

    private final Setting<Integer> pauseTicks = sgGeneral.add(new IntSetting.Builder()
        .name("pause-ticks")
        .description("How many ticks the other modules stay paused once the limit triggers.")
        .defaultValue(40)
        .min(1)
        .sliderMax(400)
        .build()
    );

    private final Setting<Double> minDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-distance")
        .description("Minimum displacement (blocks) between your client position and the server's position for a sync to count as a rubberband — filters harmless small corrections.")
        .defaultValue(1.5)
        .min(0.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Boolean> notifier = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notifier")
        .description("Announce every detected rubberband and every pause/resume in local chat (client-side only).")
        .defaultValue(true)
        .build()
    );

    // Timestamps (ms) of recent rubberband corrections, guarded by synchronized access:
    // packet events can arrive off the client thread.
    private final Deque<Long> hits = new ArrayDeque<>();
    private volatile int pauseLeft;
    private volatile boolean pausePending;

    public AntiDropLagBack() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "anti-drop-lagback", "Hazey-style lagback watcher: counts rubberband corrections in a time window and pauses everything for a set amount of ticks when you're getting rubberbanded too hard. Purely client-side.");
    }

    @Override
    public void onActivate() {
        synchronized (hits) { hits.clear(); }
        pauseLeft = 0;
        pausePending = false;
    }

    @Override
    public void onDeactivate() {
        endPause(true);
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlayerPositionLookS2CPacket packet)) return;

        // Teleport id 0 is the plain movement accept; real lagbacks/corrections carry id >= 1
        // or include position relatives. Treat every sync as a candidate and filter by distance.
        EntityPosition change = packet.change();
        Vec3d serverPos = change.position();
        Vec3d clientPos = mc.player != null ? mc.player.getEntityPos() : null;
        if (clientPos == null) return;

        double dist = clientPos.distanceTo(serverPos);
        if (dist < minDistance.get()) return;

        long now = System.currentTimeMillis();
        int recent;
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() > windowSeconds.get() * 1000L) hits.pollFirst();
            hits.addLast(now);
            recent = hits.size();
        }

        if (notifier.get()) Msg.info("§eRubberband§7: server pulled you §b%.1f§7 block(s) back (§b%d§7 in window).", dist, recent);

        // While paused, don't let more corrections queue another pause.
        if (recent >= maxRubberbands.get() && pauseLeft <= 0 && !pausePending) {
            pausePending = true;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (pausePending && pauseLeft <= 0) {
            pausePending = false;
            startPause();
        }

        if (pauseLeft > 0 && --pauseLeft == 0) {
            endPause(false);
        }
    }

    /** Snapshot of every active module but this one, then all deactivated. */
    private final List<Module> pausedModules = new ArrayList<>();

    private void startPause() {
        pausedModules.clear();
        for (Module m : Modules.get().getActive()) {
            if (m == this || pausedModules.contains(m)) continue;
            pausedModules.add(m);
        }
        // Deactivate (order does not matter, all of them need to be off)
        for (Module m : pausedModules) m.toggle();
        pauseLeft = pauseTicks.get();

        if (notifier.get()) Msg.info("§cLagback storm — paused §b%d§7 module(s) for §b%d§7 ticks.", pausedModules.size(), pauseLeft);
    }

    private void endPause(boolean fromDeactivate) {
        if (!pausedModules.isEmpty()) {
            for (Module m : pausedModules) {
                // toggle() flips them back on, except when this module itself is shutting down
                if (m.isActive()) continue;
                m.toggle();
            }
            if (notifier.get() && !fromDeactivate) Msg.info("§aResumed §b%d§7 module(s).", pausedModules.size());
            pausedModules.clear();
        }
        pauseLeft = 0;
        pausePending = false;
    }
}
