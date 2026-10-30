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
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;

/**
 * Keeps you under a packets-per-second budget by throwing away the <b>least useful</b> packets first
 * instead of the next one that happens to arrive.
 *
 * <p>A plain limiter (see {@link PacketLimiter}) drops whatever comes after the counter runs out,
 * which during a crystal fight means it may drop the block place or the totem swap and keep the
 * sprint-state packet. This module sorts every outgoing packet into three classes and only then
 * decides:
 *
 * <ul>
 *   <li><b>Critical</b> - never dropped, whatever the budget says: keep-alive and pong (the
 *       connection dies without them), plus everything a fight actually needs - block and item
 *       use, entity interaction, inventory clicks (moving totems and crystals), hotbar switches,
 *       swings, player actions and respawn.</li>
 *   <li><b>Normal</b> - movement, input and mining. Dropped only once the budget is already
 *       stretched by a quarter, so walking still looks normal under light pressure.</li>
 *   <li><b>Low</b> - chat, sprint/sneak state, custom payloads, boat paddles, the rest. The first
 *       thing to go when the budget is hit.</li>
 * </ul>
 *
 * <p>The <b>preset</b> decides what counts as critical: <b>CPvP</b> ranks obsidian/crystal placement,
 * crystal breaks, totem swaps and swings top; <b>Generic</b> keeps only inventory clicks, block/item
 * use and respawn critical.
 *
 * <p>This is the filter half of <code>.mfpacketlimit</code> - that command can also probe the
 * server's own limit and report the counters this module keeps.
 */
public class PacketFilter extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> budget = sgGeneral.add(new IntSetting.Builder()
        .name("max-per-second")
        .description("Packets per second you are willing to send. 0 turns dropping off (the module still counts).")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 1000)
        .build()
    );

    private final Setting<Preset> preset = sgGeneral.add(new EnumSetting.Builder<Preset>()
        .name("preset")
        .description("Which packets count as critical. CPvP ranks placing, crystal breaks and totem swaps top; Generic only keeps inventory and use packets.")
        .defaultValue(Preset.CPvP)
        .build()
    );

    private final Setting<Boolean> notifier = sgGeneral.add(new BoolSetting.Builder()
        .name("notifier")
        .description("Say in chat once per second how many packets were filtered and how many are going out.")
        .defaultValue(true)
        .build()
    );

    public enum Preset { CPvP, Generic }

    /** How much a packet matters when the budget is tight. */
    private enum Importance { Critical, Normal, Low }

    private long windowStart;
    private int sentThisSecond;
    private int droppedThisSecond;
    private int peakPerSecond;
    private long lastNotice;

    // Probe (.mfpacketlimit test)
    private boolean probing;
    private int probePerTick;
    private int probePackets;
    private long probeStart;
    private int lastProbePackets;
    private long lastProbeMillis;
    private boolean lastProbeKicked;
    private boolean haveProbeResult;

    public PacketFilter() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "packet-filter",
            "Keeps you under a packets-per-second budget by dropping the least important packets first - crystal places and totem swaps never get dropped.");
    }

    @Override
    public void onActivate() {
        resetCounters();
    }

    @Override
    public void onDeactivate() {
        resetCounters();
        probing = false;
    }

    private void resetCounters() {
        windowStart = System.currentTimeMillis();
        sentThisSecond = 0;
        droppedThisSecond = 0;
        lastNotice = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        rotateWindow();
        if (probing) tickProbe();
    }

    /** Breaks the counters into a new second and reports what the last one looked like. */
    private void rotateWindow() {
        long now = System.currentTimeMillis();
        if (now - windowStart < 1000) return;

        if (sentThisSecond > peakPerSecond) peakPerSecond = sentThisSecond;

        if (notifier.get() && droppedThisSecond > 0 && now - lastNotice >= 1000) {
            lastNotice = now;
            warning("Filtered %d packet(s) last second - %d went out.", droppedThisSecond, sentThisSecond);
        }

        windowStart = now;
        sentThisSecond = 0;
        droppedThisSecond = 0;
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        // The probe floods on purpose; filtering its own flood would make the measurement useless.
        if (probing) {
            sentThisSecond++;
            probePackets++;
            return;
        }

        Importance importance = classify(event.packet);

        if (importance == Importance.Critical) {
            sentThisSecond++;
            return;
        }

        int limit = budget.get();

        if (limit > 0) {
            // Movement survives a little longer than chat: it only goes once the budget is
            // stretched a quarter past its limit, so ordinary walking is never cancelled while
            // there is anything cheaper left to drop.
            int normalLimit = limit + limit / 4;

            if (importance == Importance.Normal && sentThisSecond >= normalLimit) {
                event.cancel();
                droppedThisSecond++;
                return;
            }

            if (importance == Importance.Low && sentThisSecond >= limit) {
                event.cancel();
                droppedThisSecond++;
                return;
            }
        }

        sentThisSecond++;
    }

    private Importance classify(Object packet) {
        // The connection itself: always sent, no matter what.
        if (packet instanceof KeepAliveC2SPacket || packet instanceof CommonPongC2SPacket) {
            return Importance.Critical;
        }

        // Chat commands are never worth dropping - they include /tp and friend plugins.
        if (packet instanceof CommandExecutionC2SPacket || packet instanceof ChatCommandSignedC2SPacket) {
            return Importance.Critical;
        }

        return preset.get() == Preset.CPvP ? classifyCpvp(packet) : classifyGeneric(packet);
    }

    private Importance classifyCpvp(Object packet) {
        // Everything a crystal fight cannot do without: the blocks, the crystals, the totems,
        // the hits that break a crystal, and the hotbar/inventory moves that make them happen.
        if (packet instanceof PlayerInteractBlockC2SPacket
            || packet instanceof PlayerInteractEntityC2SPacket
            || packet instanceof PlayerInteractItemC2SPacket
            || packet instanceof ClickSlotC2SPacket
            || packet instanceof UpdateSelectedSlotC2SPacket
            || packet instanceof HandSwingC2SPacket
            || packet instanceof PlayerActionC2SPacket
            || packet instanceof ClientStatusC2SPacket) return Importance.Critical;

        if (packet instanceof PlayerMoveC2SPacket
            || packet instanceof PlayerInputC2SPacket) return Importance.Normal;

        return Importance.Low;
    }

    private Importance classifyGeneric(Object packet) {
        if (packet instanceof ClickSlotC2SPacket
            || packet instanceof PlayerInteractBlockC2SPacket
            || packet instanceof PlayerInteractItemC2SPacket
            || packet instanceof ClientStatusC2SPacket) return Importance.Critical;

        if (packet instanceof PlayerMoveC2SPacket
            || packet instanceof PlayerActionC2SPacket
            || packet instanceof PlayerInteractEntityC2SPacket
            || packet instanceof PlayerInputC2SPacket) return Importance.Normal;

        return Importance.Low;
    }

    // ---- Probe ------------------------------------------------------------------------------

    /** Starts flooding look packets. Returns false when there is nothing to send them to. */
    public boolean startProbe(int perTick) {
        if (mc.getNetworkHandler() == null || mc.player == null) return false;

        probing = true;
        probePerTick = Math.max(1, perTick);
        probePackets = 0;
        probeStart = System.currentTimeMillis();
        return true;
    }

    public boolean isProbing() {
        return probing;
    }

    private void tickProbe() {
        // Kicked, or the world went away: that is the answer we were measuring for.
        if (mc.getNetworkHandler() == null || mc.player == null) {
            finishProbe(true);
            return;
        }

        if (System.currentTimeMillis() - probeStart > 60_000) {
            finishProbe(false);
            return;
        }

        for (int i = 0; i < probePerTick; i++) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                mc.player.getYaw(), mc.player.getPitch(), mc.player.isOnGround(), false));
        }
    }

    private void finishProbe(boolean kicked) {
        probing = false;
        lastProbePackets = probePackets;
        lastProbeMillis = Math.max(1, System.currentTimeMillis() - probeStart);
        lastProbeKicked = kicked;
        haveProbeResult = true;

        double perSecond = lastProbePackets * 1000.0 / lastProbeMillis;

        // The kick usually takes the chat away with it, so this mainly lands in the log; the
        // result is kept for .mfpacketlimit check after rejoining.
        if (kicked) {
            error("Disconnected after %d packets in %.1fs (%.0f/s) - that is the limit. Run .mfpacketlimit check.",
                lastProbePackets, lastProbeMillis / 1000.0, perSecond);
        } else {
            info("No kick after %d packets in %.1fs (%.0f/s) - the server tolerates at least this much.",
                lastProbePackets, lastProbeMillis / 1000.0, perSecond);
        }
    }

    // ---- Readouts used by .mfpacketlimit ----------------------------------------------------

    public int budgetValue() {
        return budget.get();
    }

    public Preset presetValue() {
        return preset.get();
    }

    public int sentLastSecond() {
        return sentThisSecond;
    }

    public int droppedLastSecond() {
        return droppedThisSecond;
    }

    public int peakPerSecond() {
        return peakPerSecond;
    }

    public boolean haveProbeResult() {
        return haveProbeResult;
    }

    public int probePackets() {
        return lastProbePackets;
    }

    public long probeMillis() {
        return lastProbeMillis;
    }

    public boolean probeKicked() {
        return lastProbeKicked;
    }

    @Override
    public String getInfoString() {
        int limit = budget.get();
        return limit <= 0 ? sentThisSecond + "/s" : sentThisSecond + "/" + limit;
    }
}
