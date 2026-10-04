package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ping spoofer with three modes:
 *  - Real:  nothing is changed (module effectively idle).
 *  - More:  your replies are delayed, so your real ping grows by the configured amount.
 *  - Spoof: replies are sent normally, but the tab-list latency is patched to a fake value.
 */
public class PingSpoofer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Real = no change. More = actually add latency by delaying replies. Spoof = only fake the tab-list ping.")
        .defaultValue(Mode.More)
        .build()
    );

    private final Setting<Integer> amount = sgGeneral.add(new IntSetting.Builder()
        .name("amount")
        .description("More: milliseconds added on top of your real ping. Spoof: the fake ping shown in the tab list.")
        .defaultValue(150)
        .min(0)
        .sliderMax(2000)
        .build()
    );

    private final Setting<Integer> jitter = sgGeneral.add(new IntSetting.Builder()
        .name("jitter")
        .description("Random +- milliseconds added to each delay, so it looks less artificial. Only used in More mode.")
        .defaultValue(20)
        .min(0)
        .sliderMax(500)
        .build()
    );

    private final Setting<Boolean> debugMode = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Print your real and effective ping in chat every second.")
        .defaultValue(false)
        .build()
    );

    public enum Mode {
        Real, More, Spoof;
    }

    private static final class Pending {
        final Packet<?> packet;
        final long dueTime;

        Pending(Packet<?> packet, long dueTime) {
            this.packet = packet;
            this.dueTime = dueTime;
        }
    }

    // Written from the netty thread (packet events), drained from the client thread (ticks).
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private int debugTimer;

    public PingSpoofer() {
        super(AddonTemplate.CATEGORY, "ping-spoofer", "Shows a different ping in the tab list. Modes: Real (no change), More (actually adds latency by delaying replies) or Spoof (only fakes the displayed ping).");
    }

    @Override
    public void onActivate() {
        debugTimer = 0;
    }

    @Override
    public void onDeactivate() {
        // Flush anything we swallowed so the connection stays clean.
        if (mc.player != null) flush(Long.MAX_VALUE);
        pending.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        flush(System.currentTimeMillis());

        if (debugMode.get() && ++debugTimer >= 20) {
            debugTimer = 0;
            int real = realPing();
            int shown = mode.get() == Mode.More ? real + amount.get() : mode.get() == Mode.Spoof ? amount.get() : real;
            info("Mode: %s, real ping: %dms, displayed ping: %dms.", mode.get(), real, shown);
        }
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mode.get() == Mode.Real) return;

        // The server measures ping as (reply time - request time), so swallowing the
        // request and answering it later genuinely increases the latency it sees.
        if (event.packet instanceof ClientboundKeepAlivePacket packet) {
            event.cancel();
            schedule(new ServerboundKeepAlivePacket(packet.getId()));
        } else if (event.packet instanceof ClientboundPingPacket packet) {
            event.cancel();
            schedule(new ServerboundPongPacket(packet.getId()));
        }
    }

    private void schedule(Packet<?> reply) {
        long delay = switch (mode.get()) {
            case More -> realPing() + amount.get() + jitterAmount();
            default -> 0L;
        };
        UUID key = UUID.randomUUID();
        pending.put(key, new Pending(reply, System.currentTimeMillis() + Math.max(0, delay)));
    }

    private void flush(long now) {
        if (pending.isEmpty()) return;

        ClientPacketListener connection = mc.getConnection();
        if (connection == null) {
            pending.clear();
            return;
        }

        Iterator<Map.Entry<UUID, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            if (now >= entry.getValue().dueTime) {
                connection.send(entry.getValue().packet);
                it.remove();
            }
        }
    }

    private int jitterAmount() {
        int j = jitter.get();
        return j > 0 ? ThreadLocalRandom.current().nextInt(-j, j + 1) : 0;
    }

    private int realPing() {
        if (mc.player == null || mc.getConnection() == null) return 0;
        PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info != null ? info.getLatency() : 0;
    }
}
