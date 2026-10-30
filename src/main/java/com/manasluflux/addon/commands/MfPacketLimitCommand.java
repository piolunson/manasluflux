package com.manasluflux.addon.commands;

import com.manasluflux.addon.modules.PacketFilter;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

/**
 * .mfpacketlimit - the packet budget, in one command.
 *
 * <ul>
 *   <li><b>test [packets-per-tick]</b> floods the server with look packets until it kicks you and
 *       reports how many went out before that happened. It will get you kicked - that is the
 *       measurement.</li>
 *   <li><b>check</b> reports the counters the {@link PacketFilter} module keeps, plus the result of
 *       the last test, so the numbers survive the kick.</li>
 * </ul>
 */
public class MfPacketLimitCommand extends Command {
    public MfPacketLimitCommand() {
        super("mfpacketlimit", "Measures the server's packet limit and shows the Packet Filter budget and counters.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            Msg.info("Usage: .mfpacketlimit test [packets-per-tick] - flood until kicked, or .mfpacketlimit check - show the budget and counters.");
            Msg.info("Set the budget in the Packet Filter module (max-per-second, 0 = off).");
            return SINGLE_SUCCESS;
        });

        builder.then(literal("test")
            .executes(context -> runTest(20))
            .then(argument("per-tick", IntegerArgumentType.integer(1, 2600))
                .executes(context -> runTest(IntegerArgumentType.getInteger(context, "per-tick")))));

        builder.then(literal("check").executes(context -> {
            check();
            return SINGLE_SUCCESS;
        }));
    }

    private int runTest(int perTick) {
        PacketFilter filter = module();
        if (filter == null) {
            Msg.error("Packet Filter module is missing.");
            return SINGLE_SUCCESS;
        }

        if (filter.isProbing()) {
            Msg.error("A test is already running.");
            return SINGLE_SUCCESS;
        }

        if (!filter.startProbe(perTick)) {
            Msg.error("Connect to a server first - there is nothing to send packets to.");
            return SINGLE_SUCCESS;
        }

        if (!filter.isActive()) {
            Msg.warning("Packet Filter is off, so the Packet Filter counters stay empty after the kick.");
        }

        Msg.warning("Flooding the server with %d look packets per tick until it kicks you. This WILL disconnect you - that is the measurement.", perTick);
        Msg.info("When you rejoin, .mfpacketlimit check shows how many packets it took.");
        return SINGLE_SUCCESS;
    }

    private void check() {
        PacketFilter filter = module();
        if (filter == null) {
            Msg.error("Packet Filter module is missing.");
            return;
        }

        int limit = filter.budgetValue();
        String budgetText = limit <= 0 ? "off (counting only)" : limit + "/s";

        Msg.info("Packet Filter: %s, preset %s.", filter.isActive() ? "on" : "off", filter.presetValue());
        Msg.info("Budget: %s - now %d/s sent, %d filtered, peak %d/s.",
            budgetText, filter.sentLastSecond(), filter.droppedLastSecond(), filter.peakPerSecond());

        if (!filter.haveProbeResult()) {
            Msg.info("No limit test yet - run .mfpacketlimit test.");
            return;
        }

        double perSecond = filter.probePackets() * 1000.0 / filter.probeMillis();

        if (filter.probeKicked()) {
            Msg.info("Last test: kicked after %d packets in %.1fs (%.0f/s).",
                filter.probePackets(), filter.probeMillis() / 1000.0, perSecond);
        } else {
            Msg.info("Last test: no kick after %d packets in %.1fs (%.0f/s).",
                filter.probePackets(), filter.probeMillis() / 1000.0, perSecond);
        }
    }

    private PacketFilter module() {
        return Modules.get().get(PacketFilter.class);
    }
}
