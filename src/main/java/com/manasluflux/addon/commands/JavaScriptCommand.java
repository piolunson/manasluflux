package com.manasluflux.addon.commands;

import com.manasluflux.addon.AddonTemplate;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

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
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
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
            Component.literal("mfjavascript"),
            Component.literal(question),
            Component.literal("YES (crash)"),
            Component.literal("NO (confetti)")
        );

        mc.setScreenAndShow(screen);
    }

    private void crashTheGame() {
        RuntimeException cause = new RuntimeException("mfjavascript: the user pressed YES. On purpose.");
        CrashReport report = new CrashReport("ManasluFlux - mfjavascript was confirmed", cause);
        Minecraft.crash(mc, mc.gameDirectory, report, 0);
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
                } catch (InterruptedException _) {
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
        if (mc.player == null || mc.level == null) return;

        Vec3 pos = mc.player.position();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < BURST_PARTICLES; i++) {
            int color = CONFETTI_COLORS[random.nextInt(CONFETTI_COLORS.length)];
            DustParticleOptions options = new DustParticleOptions(color, DUST_SCALE);

            double x = pos.x + random.nextDouble(-1.5, 1.5);
            double y = pos.y + random.nextDouble(0.5, 2.5);
            double z = pos.z + random.nextDouble(-1.5, 1.5);

            mc.level.addParticle(
                options,
                x, y, z,
                random.nextDouble(-0.15, 0.15),
                random.nextDouble(-0.05, 0.2),
                random.nextDouble(-0.15, 0.15)
            );
        }
    }
}
