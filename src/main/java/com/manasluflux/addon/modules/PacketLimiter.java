package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;

/**
 * Port of RyanWare's PacketLimiter (SmilerRyan): caps how many packets you send per tick so
 * laggy modules can't get you kicked for flooding. Keep-alive and pong packets are always
 * allowed so the connection itself never breaks.
 */
public class PacketLimiter extends Module {
    private final Setting<Integer> maxPackets = settings.getDefaultGroup().add(new IntSetting.Builder()
        .name("max-packets")
        .description("Maximum packets allowed per tick.")
        .defaultValue(100)
        .min(1)
        .sliderMax(1000)
        .build()
    );

    private final Setting<Boolean> debugMode = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Show the packet counters in chat every second.")
        .defaultValue(false)
        .build()
    );

    private int packetsSent;
    private int highestPacketsSent;
    private int debugTimer;

    public PacketLimiter() {
        super(AddonTemplate.REWRITE_CATEGORY, "packet-limiter", "Limits the packets you send per tick to prevent packet-flood kicks.");
    }

    @Override
    public void onActivate() {
        packetsSent = 0;
        highestPacketsSent = 0;
        debugTimer = 0;
    }

    @Override
    public void onDeactivate() {
        packetsSent = 0;
        highestPacketsSent = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (packetsSent > highestPacketsSent) highestPacketsSent = packetsSent;

        if (debugMode.get() && ++debugTimer >= 20) {
            debugTimer = 0;
            info("Highest: " + highestPacketsSent + ", sent this tick: " + packetsSent + ", max: " + maxPackets.get() + ".");
        }

        packetsSent = 0;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (event.packet instanceof ServerboundKeepAlivePacket || event.packet instanceof ServerboundPongPacket) return;

        if (packetsSent >= maxPackets.get()) {
            event.cancel();
            return;
        }

        packetsSent++;
    }
}
