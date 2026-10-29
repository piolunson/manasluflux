package com.manasluflux.addon.commands;

import com.manasluflux.addon.modules.CobwebBypass;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

/**
 * {@code .mfwebspeed} - autoconfig for Cobweb Bypass.
 *
 * <p>It takes your keys, walks you into the nearest cobweb and then tries every
 * (horizontal, vertical) pair from 0 up to the max you pass, timing how long it takes to get
 * out of the web and dropping any combo the server pulls you back on. The fastest combo that
 * survived is applied as {@code Custom}.
 */
public class MfWebSpeedCommand extends Command {
    /** Step used when the command does not get one. */
    private static final double DEFAULT_STEP = 20.0;

    public MfWebSpeedCommand() {
        super("mfwebspeed", "Autoconfig for Cobweb Bypass: walks into cobwebs and tries every horizontal/vertical pair from 0 to your max (optionally -5..-1), keeping the fastest one the server does not lag you back on. 'verbose' prints every combo as it is tested.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            CobwebBypass module = Modules.get().get(CobwebBypass.class);

            Msg.info("Usage: .mfwebspeed <max> [step] [negative] [verbose] | .mfwebspeed stop");
            Msg.info("  <max>      highest percentage to try (0-<max>, both horizontal and vertical)");
            Msg.info("  [step]     gap between values, default 20");
            Msg.info("  [negative] also try -5..-1 (stickier than vanilla), off by default");
            Msg.info("  [verbose]  print every combo as it is tested, with OK / FAILED / stuck");
            Msg.info("Walks you into the nearest cobweb and keeps the fastest combo the server does not pull you back on.");
            if (module != null) Msg.info("Status: %s", module.tuneStatus());

            return SINGLE_SUCCESS;
        });

        builder.then(literal("stop").executes(context -> {
            CobwebBypass module = Modules.get().get(CobwebBypass.class);

            if (module == null || !module.isTuning()) {
                Msg.info("The web speed autoconfig is not running.");
                return SINGLE_SUCCESS;
            }

            module.stopTuning(true);
            return SINGLE_SUCCESS;
        }));

        builder.then(argument("max", DoubleArgumentType.doubleArg(0, 10000))
            .executes(context -> start(context, "max", DEFAULT_STEP, false, false))
            .then(literal("verbose").executes(context -> start(context, "max", DEFAULT_STEP, false, true)))
            .then(literal("negative")
                .executes(context -> start(context, "max", DEFAULT_STEP, true, false))
                .then(literal("verbose").executes(context -> start(context, "max", DEFAULT_STEP, true, true))))
            .then(argument("step", DoubleArgumentType.doubleArg(1, 10000))
                .executes(context -> start(context, "step", false, false))
                .then(literal("verbose").executes(context -> start(context, "step", false, true)))
                .then(literal("negative")
                    .executes(context -> start(context, "step", true, false))
                    .then(literal("verbose").executes(context -> start(context, "step", true, true))))));
    }

    private int start(CommandContext<CommandSource> context, String stepArg, boolean negative, boolean verbose) {
        return start(context, stepArg, DoubleArgumentType.getDouble(context, stepArg), negative, verbose);
    }

    private int start(CommandContext<CommandSource> context, String stepArg, double step, boolean negative, boolean verbose) {
        CobwebBypass module = Modules.get().get(CobwebBypass.class);
        if (module == null) {
            Msg.error("Cobweb Bypass is not registered.");
            return SINGLE_SUCCESS;
        }

        if (mc.player == null || mc.world == null) {
            Msg.error("Not in a world.");
            return SINGLE_SUCCESS;
        }

        double max = DoubleArgumentType.getDouble(context, "max");
        if (max < -5.0 || max > 10000.0) {
            Msg.error("Max has to be between 0 and 10000.");
            return SINGLE_SUCCESS;
        }

        // Tick events only reach active modules, so the sweep has to run with the module on.
        if (!module.isActive()) module.toggle();

        module.startTuning(max, step, negative, verbose);
        return SINGLE_SUCCESS;
    }
}
