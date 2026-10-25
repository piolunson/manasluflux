package com.manasluflux.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * .mfhelp - built-in tutorial for the Discord RPC module: every {} placeholder,
 * how the images/name work, and example lines you can paste into Line 1 / Line 2.
 */
public class MfHelpCommand extends Command {
    public MfHelpCommand() {
        super("mfhelp", "ManasluFlux tutorial: every Discord RPC {placeholder} explained, with examples.");
    }

    private static void line(String text, Formatting color) {
        mc.player.sendMessage(Text.literal("   " + text).formatted(color), false);
    }

    private static void header(String text) {
        mc.player.sendMessage(Text.literal("").append(Text.literal("== ")).formatted(Formatting.DARK_GRAY)
            .append(Text.literal(text).formatted(Formatting.GOLD, Formatting.BOLD)), false);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) return SINGLE_SUCCESS;

            mc.player.sendMessage(Text.literal("[ManasluFlux] Discord RPC tutorial - type .mfhelp again anytime.").formatted(Formatting.AQUA), false);

            header("Every {} placeholder (use in Line 1 / Line 2 / image-text)");
            line("{player}    your nickname (e.g. Steve)", Formatting.GREEN);
            line("{server}   server address or Singleplayer", Formatting.GREEN);
            line("{ping}     your latency in ms", Formatting.GREEN);
            line("{pos}      your XYZ position", Formatting.GREEN);
            line("{dimension} overworld / the_nether / the_end", Formatting.GREEN);
            line("{cps}      crystals blown up per second (needs crystals-per-second on)", Formatting.GREEN);
            line("{version}  ManasluFlux version (0.7.x)", Formatting.GREEN);
            line("{health}   your HP, rounded up", Formatting.GREEN);

            header("Example lines (paste into the module settings)");
            line("Line 1: Crystaling on {server}", Formatting.YELLOW);
            line("Line 2: {cps} CPS | {ping} ms | {health} HP", Formatting.YELLOW);
            line("Line 1: Mining {dimension} @ {pos}", Formatting.YELLOW);
            line("image-text: ManasluFlux {version}  (hover text)", Formatting.YELLOW);

            header("Discord name & logo");
            line("They come from your Discord application (application-id setting).", Formatting.LIGHT_PURPLE);
            line("Big image = key from the app's Art Assets tab (default: manasluflux).", Formatting.LIGHT_PURPLE);
            line("Full guide in the repo: discord-assets/README.md", Formatting.LIGHT_PURPLE);

            return SINGLE_SUCCESS;
        });
    }
}
