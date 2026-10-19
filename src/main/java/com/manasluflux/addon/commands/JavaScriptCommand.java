package com.manasluflux.addon.commands;

import com.manasluflux.addon.AddonTemplate;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * .mfjavascript - asks a yes/no question in a vanilla confirmation dialog.
 * Yes -> deliberately crashes the game (with a proper crash report).
 * No -> confetti shower for a few seconds, then nothing.
 */
public class JavaScriptCommand extends Command {
    private static final long CONFETTI_DURATION_MS = 4000;
    private static final long CONFETTI_BURST_INTERVAL_MS = 150;
    private static final int[] CONFETTI_COLORS = {
        0xFF3050, 0x30FF50, 0x3050FF, 0xFFFF30, 0xFF30FF, 0x30FFFF, 0xFFA030, 0xFFFFFF
    };
    private static final int BURST_PARTICLES = 80;
    private static final float DUST_SCALE = 0.9f;

    private Thread confettiThread;

    public JavaScriptCommand() {
        super("mfjavascript", "Asks a question. Yes crashes the game, No rains confetti. Usage: .mfjavascript [question]");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            ask("Do you really want to execute this JavaScript?");
            return SINGLE_SUCCESS;
        });

        builder.then(argument("question", StringArgumentType.greedyString())
            .executes(context -> {
                ask(StringArgumentType.getString(context, "question"));
                return SINGLE_SUCCESS;
            })
        );
    }

    private void ask(String question) {
        if (mc.player == null) {
            error("Not in a world.");
            return;
        }

        BooleanConsumer callback = confirmed -> {
            if (confirmed) crashTheGame();
            else startConfetti();
        };

        ConfirmScreen screen = new ConfirmScreen(
            callback,
            Text.literal("mfjavascript"),
            Text.literal(question),
            Text.literal("YES (crash)"),
            Text.literal("NO (confetti)")
        );

        mc.setScreenAndRender(screen);
    }

    private void crashTheGame() {
        RuntimeException cause = new RuntimeException("mfjavascript: the user pressed YES. On purpose.");
        CrashReport report = new CrashReport("ManasluFlux - mfjavascript was confirmed", cause);
        mc.printCrashReport(report);
    }

    private void startConfetti() {
        stopConfetti();

        info("Confetti! (for %d seconds)", CONFETTI_DURATION_MS / 1000);

        long endTime = System.currentTimeMillis() + CONFETTI_DURATION_MS;

        confettiThread = new Thread(() -> {
            while (System.currentTimeMillis() < endTime) {
                mc.execute(this::confettiBurst);
                try {
                    Thread.sleep(CONFETTI_BURST_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
        }, "mfjavascript-confetti");
        confettiThread.setDaemon(true);
        confettiThread.start();
    }

    private void stopConfetti() {
        if (confettiThread != null) {
            confettiThread.interrupt();
            confettiThread = null;
        }
    }

    private void confettiBurst() {
        if (mc.player == null || mc.world == null) return;

        Vec3d pos = mc.player.getEntityPos();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < BURST_PARTICLES; i++) {
            int color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.length)];
            DustParticleEffect options = new DustParticleEffect(color, DUST_SCALE);

            double x = pos.x + random.nextDouble(-1.5, 1.5);
            double y = pos.y + random.nextDouble(0.5, 2.5);
            double z = pos.z + random.nextDouble(-1.5, 1.5);

            mc.world.addParticleClient(
                options,
                x, y, z,
                random.nextDouble(-0.15, 0.15),
                random.nextDouble(-0.05, 0.2),
                random.nextDouble(-0.15, 0.15)
            );
        }
    }
}
