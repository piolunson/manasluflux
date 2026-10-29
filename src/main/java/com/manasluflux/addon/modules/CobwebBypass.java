package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.commands.Msg;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes (or softens) the stuck-speed a cobweb applies to you, so walking or jumping
 * through webs doesn't turn into glue.
 *
 * <p>1.21.11 splits the old behaviour in two: {@code Entity#slowMovement} no longer touches
 * the velocity - it just stores a multiplier that {@code Entity#move} applies to that tick's
 * movement delta. So the whole trick is one number: the multiplier cobwebs hand to
 * {@code slowMovement} (vanilla {@code 0.25 / 0.05 / 0.25}, or {@code 0.5 / 0.25 / 0.5} with
 * the Weaving effect). {@link com.manasluflux.addon.mixin.EntitySlowMovementMixin} asks this
 * module for a replacement value and nothing else in the movement code changes.
 *
 * <p>Whether that is enough depends entirely on what the server does with your position:
 * <ul>
 *   <li><b>Vanilla</b> - the server only sanity-checks how far a packet moved you, never
 *       re-simulates the web. Full no-slow works, and most anarchy servers are this way.</li>
 *   <li><b>NCP / Themis</b> - distance- and limit-based checks, no stuck-speed prediction.
 *       Full speed usually passes; the profiles cap the per-axis step so a
 *       {@code SurvivalFly}-style limit isn't tripped.</li>
 *   <li><b>Grim</b> - a real physics engine, so it re-simulates cobwebs itself. Grim builds
 *       before the June 2026 stuck-speed rewrite only tolerated about 0.03/tick of prediction
 *       error, which is what the old "NoWeb" bypasses lived on; builds after it predict webs
 *       properly and nothing client-side avoids the flag. The Grim profile therefore only
 *       softens for the first couple of ticks (enough to launch you out of a web) and then
 *       hands the vanilla multiplier back, instead of claiming a sustained speed.</li>
 * </ul>
 *
 * <p>The lagback watcher is the honest part: if the server does pull you back while you were
 * using the trick, it says so and (optionally) drops to the vanilla multiplier for a moment.
 */
public class CobwebBypass extends Module {
    /** Vanilla cobweb slow: 0.25 horizontal, 0.05 vertical. */
    private static final Vec3d COBWEB = new Vec3d(0.25, 0.05, 0.25);
    /** Cobweb slow while the entity has Weaving, which already softens it to 0.5 / 0.25. */
    private static final Vec3d COBWEB_WEAVING = new Vec3d(0.5, 0.25, 0.5);
    private static final double EPSILON = 1.0E-6;
    /** A server position sync this far away is a correction, not the server agreeing with a packet. */
    private static final double LAGBACK_DISTANCE = 1.0;
    /** How long after the last softened tick a correction still counts as caused by it. */
    private static final int LAGBACK_WINDOW = 20;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Profile> profile = sgGeneral.add(new EnumSetting.Builder<Profile>()
        .name("anticheat")
        .description("Which server-side check profile to aim at. Picking one fills in the numbers below; Custom keeps whatever you set.")
        .defaultValue(Profile.Vanilla)
        .onChanged(this::applyProfile)
        .build()
    );

    private final Setting<Double> horizontal = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal")
        .description("How much of the horizontal web slow to remove, in percent. 0 = vanilla 0.25, 100 = no slow at all.")
        .defaultValue(100.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> vertical = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical")
        .description("How much of the vertical web slow to remove, in percent. 0 = vanilla 0.05, 100 = no slow at all (jumping out of webs becomes normal).")
        .defaultValue(100.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> maxSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-speed")
        .description("Hard cap on how much one axis may move per tick while in a web, in blocks. 0 turns the cap off.")
        .defaultValue(0.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> webTimer = sgGeneral.add(new DoubleSetting.Builder()
        .name("web-timer")
        .description("Speeds up walking only while you are in a web: the horizontal movement is multiplied by this factor (1.0 = off, 2.0 = twice as fast). Vertical movement is never touched.")
        .defaultValue(1.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Integer> burstTicks = sgGeneral.add(new IntSetting.Builder()
        .name("burst-ticks")
        .description("Only soften the first N ticks you spend in a web, then go back to vanilla slow. 0 softens every tick.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final SettingGroup sgSprint = settings.createGroup("Sprint Tweaks");
    private final SettingGroup sgTimer = settings.createGroup("Timer");

    private final Setting<Integer> stopSprintBefore = sgSprint.add(new IntSetting.Builder()
        .name("stop-sprint-before")
        .description("Stops your sprint when a cobweb is this many ticks of movement ahead. 0 turns it off.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> startSprintAfter = sgSprint.add(new IntSetting.Builder()
        .name("start-sprint-after")
        .description("While you are inside a web, sprint stays off until you have been in it for this many ticks. 0 turns it off.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Double> timerMultiplier = sgTimer.add(new DoubleSetting.Builder()
        .name("timer-multiplier")
        .description("Runs Meteor's Timer while you are inside a real cobweb and turns it back off the moment you leave it (1.0 = off, 2.0 = everything runs twice as fast).")
        .defaultValue(1.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> allStuckBlocks = sgGeneral.add(new BoolSetting.Builder()
        .name("all-stuck-blocks")
        .description("Softens every stuck-speed block, not just cobwebs (sweet berry bushes, powder snow and anything else that multiplies your movement).")
        .defaultValue(false)
        .build()
    );

    private final Setting<LagbackAction> onLagback = sgGeneral.add(new EnumSetting.Builder<LagbackAction>()
        .name("on-lagback")
        .description("What to do when the server pulls you back right after the trick was used.")
        .defaultValue(LagbackAction.Pause)
        .build()
    );

    private final Setting<Integer> pauseTicks = sgGeneral.add(new IntSetting.Builder()
        .name("pause-ticks")
        .description("How many ticks to fall back to the vanilla multiplier after a lagback (on-lagback = Pause).")
        .defaultValue(40)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> notifier = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-notifier")
        .description("Reports lagbacks that happen while the trick is active, in local chat.")
        .defaultValue(true)
        .build()
    );

    /** Ticks spent in a web in the current stretch, used by burst-ticks. */
    private int ticksInWeb;
    /** Set by the mixin whenever it asked for a multiplier this tick. */
    private boolean softenedThisTick;
    /** Ticks since the last softened tick (LAGBACK_WINDOW is the useful range). */
    private int ticksSinceSoften = Integer.MAX_VALUE;
    private int pauseLeft;
    private int lagbacks;
    private boolean loggedFailure;

    // ---- .mfwebspeed autoconfig -------------------------------------------------

    /** What the autoconfig is doing right now. */
    private enum TuneStage { Off, Approach, Measure }

    /** Ticks we may spend walking to a web before giving up on the current combo. */
    private static final int APPROACH_TIMEOUT = 140;
    /** A combo that has not left the web by then counts as "stuck" at this many ticks. */
    private static final int MAX_ESCAPE_TICKS = 60;
    /** How far away a web may be for the autoconfig to walk to it. */
    private static final int WEB_RADIUS = 6;
    /** Refuses to start a sweep bigger than this - each combo costs ~1-2 seconds. */
    private static final int MAX_COMBOS = 400;

    private TuneStage tuneStage = TuneStage.Off;
    private final List<double[]> tuneCombos = new ArrayList<>();
    private int tuneIndex;
    private int tuneStageTicks;
    private int tuneEscapeTicks;
    private boolean tuneLagback;
    private int tuneTested;
    private int tunePassed;
    private double tuneH;
    private double tuneV;
    private double tuneBestH;
    private double tuneBestV;
    private int tuneBestEscape = Integer.MAX_VALUE;
    private boolean tuneHaveBest;
    private double tuneSavedH;
    private double tuneSavedV;
    private double tuneSavedCap;
    private double tuneSavedTimer;
    private int tuneSavedBurst;
    private Profile tuneSavedProfile;
    private boolean tuneSavedForward;
    private boolean tuneSavedJump;
    private boolean tuneKeysHeld;
    private boolean tuneVerbose;
    /** True while this module is the one driving Meteor's Timer. */
    private boolean timerOverridden;

    public CobwebBypass() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "cobweb-bypass",
            "Removes the cobweb slow client-side (fully or by percent), with profiles for vanilla/NCP/Themis/Grim servers and a lagback watcher.");
    }

    @Override
    public void onActivate() {
        ticksInWeb = 0;
        softenedThisTick = false;
        ticksSinceSoften = Integer.MAX_VALUE;
        pauseLeft = 0;
        lagbacks = 0;
        loggedFailure = false;
    }

    @Override
    public void onDeactivate() {
        ticksInWeb = 0;
        softenedThisTick = false;
        pauseLeft = 0;
        stopTuning(false);
        releaseTimer();
    }

    @Override
    public String getInfoString() {
        if (pauseLeft > 0) return "paused";
        if (burstTicks.get() > 0) return profile.get() + " " + Math.min(ticksInWeb, burstTicks.get()) + "/" + burstTicks.get();
        return profile.get() + " " + (int) Math.round(horizontal.get()) + "%";
    }

    /**
     * Called from {@link com.manasluflux.addon.mixin.EntitySlowMovementMixin} for every
     * {@code slowMovement} call in the client. Returns {@code multiplier} unchanged (same
     * instance) when it has nothing to say, so the mixin can tell the two cases apart.
     */
    public static Vec3d modifyMultiplier(Entity entity, Vec3d multiplier) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || entity != mc.player) return multiplier;

        CobwebBypass module;
        try {
            module = Modules.get().get(CobwebBypass.class);
        } catch (Throwable t) {
            // Modules are not registered yet (or already torn down) - leave the game alone.
            return multiplier;
        }

        if (module == null || !module.isActive()) return multiplier;
        return module.soften(entity, multiplier);
    }

    /** Works out the replacement multiplier for one stuck-speed call. */
    private Vec3d soften(Entity entity, Vec3d vanilla) {
        boolean stuck = isCobwebSlow(vanilla) || allStuckBlocks.get();
        if (!stuck) return vanilla;

        softenedThisTick = true;

        // A setback or a burst that already ran out: let the vanilla number through.
        if (pauseLeft > 0) return vanilla;
        if (burstTicks.get() > 0 && ticksInWeb >= burstTicks.get()) return vanilla;

        double keepHorizontal = horizontal.get() / 100.0;
        double keepVertical = vertical.get() / 100.0;

        double x = softenAxis(vanilla.x, keepHorizontal);
        double y = softenAxis(vanilla.y, keepVertical);
        double z = softenAxis(vanilla.z, keepHorizontal);

        // Web timer: only while actually walking, and only the horizontal axes - a vertical
        // boost would launch you out of the web instead of speeding up the walk.
        Vec3d velocity = entity.getVelocity();
        double timer = webTimer.get();
        if (timer != 1.0 && Math.abs(velocity.x) + Math.abs(velocity.z) > 0.01) {
            x *= timer;
            z *= timer;
        }

        double cap = maxSpeed.get();
        if (cap > 0) {
            x = capAxis(x, velocity.x, cap);
            z = capAxis(z, velocity.z, cap);
        }

        if (Math.abs(x - vanilla.x) < EPSILON
            && Math.abs(y - vanilla.y) < EPSILON
            && Math.abs(z - vanilla.z) < EPSILON) return vanilla;

        return new Vec3d(x, y, z);
    }

    /** 0% keeps the vanilla multiplier, 100% turns it into no slow at all. */
    private static double softenAxis(double vanilla, double keep) {
        return vanilla + (1.0 - vanilla) * Math.min(keep, 1.0);
    }

    /**
     * The movement delta for this tick is velocity * multiplier, so capping the multiplier by
     * (cap / |velocity|) is what actually caps the step the server sees on that axis.
     */
    private static double capAxis(double multiplier, double velocity, double cap) {
        if (Math.abs(velocity) < EPSILON) return multiplier;
        return Math.min(multiplier, cap / Math.abs(velocity));
    }

    private static boolean isCobwebSlow(Vec3d multiplier) {
        return closeTo(multiplier, COBWEB) || closeTo(multiplier, COBWEB_WEAVING);
    }

    private static boolean closeTo(Vec3d a, Vec3d b) {
        return Math.abs(a.x - b.x) < 1.0E-5
            && Math.abs(a.y - b.y) < 1.0E-5
            && Math.abs(a.z - b.z) < 1.0E-5;
    }

    /** Sprint tweaks run before the player is ticked, so the sprint state is already correct for this tick's movement packet. */
    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.options == null) return;
        applySprintTweaks();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (tuneStage != TuneStage.Off) tickTuner();
        if (pauseLeft > 0) pauseLeft--;

        if (softenedThisTick) {
            ticksInWeb++;
            ticksSinceSoften = 0;
        } else {
            ticksInWeb = 0;
            if (ticksSinceSoften < LAGBACK_WINDOW) ticksSinceSoften++;
        }

        softenedThisTick = false;

        updateWebTimer();

        // Second pass: the client re-evaluates sprint during its own tick, so the tweak also has
        // to be applied after it, or a held sprint key would switch it straight back on.
        applySprintTweaks();
    }

    /**
     * Stops sprint a configured number of ticks before a web is reached, and keeps it off for the
     * first ticks inside one - both are purely what your legs do, nothing is sent differently.
     */
    private void applySprintTweaks() {
        if (mc.player == null) return;

        // Inside a web: sprint stays off for the first ticks, then comes back on by itself.
        if (insideWeb()) {
            int delay = startSprintAfter.get();
            if (delay <= 0) return;

            if (ticksInWeb < delay) {
                if (mc.player.isSprinting()) mc.player.setSprinting(false);
            } else if (!mc.player.isSprinting() && wantsToWalk()) {
                mc.player.setSprinting(true);
            }
            return;
        }

        // Outside a web: drop sprint a set amount of ticks before the web is reached.
        int lead = stopSprintBefore.get();
        if (lead > 0 && mc.player.isSprinting() && webAhead(lead)) {
            mc.player.setSprinting(false);
        }
    }

    /** Whether the player is holding a movement key, so re-enabling sprint cannot look like a ghost sprint. */
    private boolean wantsToWalk() {
        return mc.options != null && (mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed());
    }

    /** Walks the next {@code ticks} of your current velocity and reports whether a web is in it. */
    private boolean webAhead(int ticks) {
        if (mc.player == null || mc.world == null) return false;

        Vec3d velocity = mc.player.getVelocity();
        if (Math.abs(velocity.x) + Math.abs(velocity.z) < 0.02) return false;

        for (int i = 1; i <= ticks; i++) {
            BlockPos pos = BlockPos.ofFloored(mc.player.getEntityPos().add(velocity.multiply(i)));
            if (isWeb(pos) || isWeb(pos.up())) return true;
        }

        return false;
    }

    /** Meteor's Timer, on only while you actually stand in a cobweb. */
    private void updateWebTimer() {
        double multiplier = timerMultiplier.get();
        boolean enabled = multiplier > 0 && Math.abs(multiplier - 1.0) > 1.0E-4 && insideWeb();

        if (!enabled) {
            releaseTimer();
            return;
        }

        Timer timer = Modules.get().get(Timer.class);
        if (timer == null) return;

        timer.setOverride(multiplier);
        timerOverridden = true;
    }

    /** Hands the timer back to Meteor (its own module setting, or off). */
    private void releaseTimer() {
        if (!timerOverridden) return;

        Timer timer = Modules.get().get(Timer.class);
        if (timer != null) timer.setOverride(Timer.OFF);
        timerOverridden = false;
    }

    private boolean isWeb(BlockPos pos) {
        return mc.world != null && mc.world.getBlockState(pos).isOf(Blocks.COBWEB);
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof PlayerPositionLookS2CPacket packet)) return;
        if (mc.player == null) return;
        if (ticksSinceSoften > LAGBACK_WINDOW) return;

        EntityPosition change = packet.change();
        Vec3d serverPos = change.position();
        double distance = mc.player.getEntityPos().distanceTo(serverPos);
        if (distance < LAGBACK_DISTANCE) return;

        lagbacks++;
        ticksInWeb = 0;

        // While autoconfiguring a setback just marks the current combo as failed; pausing would
        // otherwise freeze the sweep in vanilla-slow mode and corrupt the next measurement.
        if (tuneStage != TuneStage.Off) {
            tuneLagback = true;
            return;
        }

        if (onLagback.get() == LagbackAction.Pause) pauseLeft = Math.max(pauseLeft, pauseTicks.get());

        if (notifier.get()) {
            if (lagbacks == 1) {
                Msg.warning("Lagback of %.1f block(s) right after the web trick - the server predicted it.", distance);
            } else if (lagbacks % 5 == 0) {
                Msg.warning("%d lagbacks while the web trick was active.", lagbacks);
            }
        }
    }

    private void applyProfile(Profile selected) {
        switch (selected) {
            case Vanilla -> {
                horizontal.set(100.0);
                vertical.set(100.0);
                maxSpeed.set(0.0);
                burstTicks.set(0);
            }
            case NCP -> {
                horizontal.set(100.0);
                vertical.set(100.0);
                maxSpeed.set(0.45);
                burstTicks.set(0);
            }
            case Themis -> {
                horizontal.set(85.0);
                vertical.set(80.0);
                maxSpeed.set(0.40);
                burstTicks.set(0);
            }
            case Grim -> {
                horizontal.set(100.0);
                vertical.set(100.0);
                maxSpeed.set(0.30);
                burstTicks.set(2);
            }
            case Custom -> {
                // Nothing: the sliders are the configuration here.
            }
        }
    }

    // ---- .mfwebspeed autoconfig -------------------------------------------------

    public boolean isTuning() {
        return tuneStage != TuneStage.Off;
    }

    public String tuneStatus() {
        if (!isTuning()) return "not running";
        return String.format("%d/%d tested, now horizontal %.0f / vertical %.0f",
            tuneTested, tuneCombos.size(), tuneH, tuneV);
    }

    public void setHorizontalPercent(double percent) {
        horizontal.set(percent);
    }

    public void setVerticalPercent(double percent) {
        vertical.set(percent);
    }

    /**
     * Starts a sweep over every (horizontal, vertical) pair from {@code 0} to {@code max},
     * plus {@code -5 .. -1} when {@code negative} is set. The module has to be active for the
     * tick events to arrive.
     */
    public boolean startTuning(double max, double step, boolean negative, boolean verbose) {
        if (mc.player == null || mc.world == null) {
            Msg.error("Not in a world.");
            return false;
        }

        if (isTuning()) stopTuning(false);

        tuneVerbose = verbose;

        List<Double> values = new ArrayList<>();
        if (negative) {
            // -5..-1 in ones: it is an opt-in corner of the sweep, so it is always tried whole
            // rather than being thinned out by the step.
            for (double v = -5.0; v < 0.0; v += 1.0) values.add(round(v));
        }
        for (double v = 0.0; v <= max + 1.0E-9; v += step) values.add(round(v));
        if (values.isEmpty() || values.get(values.size() - 1) < max - 1.0E-9) values.add(round(max));

        tuneCombos.clear();
        for (double h : values) {
            for (double v : values) tuneCombos.add(new double[] {h, v});
        }

        if (tuneCombos.size() > MAX_COMBOS) {
            Msg.error("%d combinations is too many (%.0f x %.0f values) - raise the step or lower the max, limit is %d.",
                tuneCombos.size(), (double) values.size(), (double) values.size(), MAX_COMBOS);
            return false;
        }

        if (nearestWeb() == null) {
            Msg.error("No cobweb within %d blocks - stand next to a web and run it again.", WEB_RADIUS);
            return false;
        }

        tuneSavedProfile = profile.get();
        tuneSavedH = horizontal.get();
        tuneSavedV = vertical.get();
        tuneSavedCap = maxSpeed.get();
        tuneSavedTimer = webTimer.get();
        tuneSavedBurst = burstTicks.get();
        tuneSavedForward = mc.options.forwardKey.isPressed();
        tuneSavedJump = mc.options.jumpKey.isPressed();

        // Clean two-parameter sweep: the profile must not overwrite the sliders, and a cap or a
        // burst would waste part of every combo on something we are not testing.
        profile.set(Profile.Custom);
        maxSpeed.set(0.0);
        webTimer.set(1.0);
        burstTicks.set(0);

        tuneIndex = 0;
        tuneTested = 0;
        tunePassed = 0;
        tuneHaveBest = false;
        tuneBestEscape = Integer.MAX_VALUE;
        applyCombo(0);

        Msg.info("Web speed autoconfig running: %d combo(s), %s..%s both ways. Keep your hands off the keys.",
            tuneCombos.size(), fmt(tuneCombos.get(0)[0]), fmt(max));
        if (tuneVerbose) Msg.info("Verbose: every combo and its result is printed as the sweep goes.");
        return true;
    }

    /** Stops the sweep. {@code restore} puts the pre-sweep settings back. */
    public void stopTuning(boolean restore) {
        if (tuneStage == TuneStage.Off) return;

        releaseKeys();

        if (restore) {
            horizontal.set(tuneSavedH);
            vertical.set(tuneSavedV);
            maxSpeed.set(tuneSavedCap);
            webTimer.set(tuneSavedTimer);
            burstTicks.set(tuneSavedBurst);
            profile.set(tuneSavedProfile);
        }

        tuneStage = TuneStage.Off;
        tuneCombos.clear();
        tuneIndex = 0;

        if (restore) Msg.info("Web speed autoconfig stopped, settings restored.");
    }

    private void tickTuner() {
        if (mc.player == null || mc.world == null) {
            stopTuning(true);
            return;
        }

        switch (tuneStage) {
            case Approach -> {
                holdKeys();

                if (insideWeb()) {
                    // Inside: start timing how long it takes to get out.
                    tuneStage = TuneStage.Measure;
                    tuneEscapeTicks = 0;
                    tuneStageTicks = 0;
                    return;
                }

                steerToWeb();
                tuneStageTicks++;

                if (tuneStageTicks > APPROACH_TIMEOUT) {
                    tuneStageTicks = 0;
                    if (nearestWeb() == null) {
                        Msg.error("Lost the cobweb (nothing within %d blocks) - autoconfig stopped.", WEB_RADIUS);
                        finishTuning();
                    }
                }
            }
            case Measure -> {
                holdKeys();
                tuneEscapeTicks++;

                if (!insideWeb() || tuneEscapeTicks >= MAX_ESCAPE_TICKS) finishCombo();
            }
            default -> {
            }
        }
    }

    private void applyCombo(int index) {
        double[] combo = tuneCombos.get(index);
        tuneH = combo[0];
        tuneV = combo[1];

        horizontal.set(tuneH);
        vertical.set(tuneV);

        tuneStage = TuneStage.Approach;
        tuneStageTicks = 0;
        tuneEscapeTicks = 0;
        tuneLagback = false;

        if (tuneVerbose) {
            Msg.info("[%d/%d] testing horizontal %s%% / vertical %s%%",
                index + 1, tuneCombos.size(), fmt(tuneH), fmt(tuneV));
        }
    }

    private void finishCombo() {
        tuneTested++;
        int escape = Math.min(tuneEscapeTicks, MAX_ESCAPE_TICKS);

        if (tuneVerbose) {
            String result;
            if (tuneLagback) result = "FAILED - the server pulled you back";
            else if (tuneEscapeTicks >= MAX_ESCAPE_TICKS) result = "stuck - never left the web";
            else result = "OK - out in " + escape + " tick(s)";

            Msg.info("  -> " + result);
        }

        if (!tuneLagback) {
            tunePassed++;
            boolean better = !tuneHaveBest
                || escape < tuneBestEscape
                || (escape == tuneBestEscape && tuneH > tuneBestH);

            if (better) {
                tuneHaveBest = true;
                tuneBestEscape = escape;
                tuneBestH = tuneH;
                tuneBestV = tuneV;
            }
        }

        if (++tuneIndex >= tuneCombos.size()) {
            finishTuning();
            return;
        }

        applyCombo(tuneIndex);
    }

    private void finishTuning() {
        releaseKeys();
        maxSpeed.set(tuneSavedCap);
        webTimer.set(tuneSavedTimer);
        burstTicks.set(tuneSavedBurst);

        if (tuneHaveBest) {
            horizontal.set(tuneBestH);
            vertical.set(tuneBestV);
            profile.set(Profile.Custom);

            Msg.info("Best web setting: horizontal %s%%, vertical %s%% - out of the web in %d tick(s), no lagback.",
                fmt(tuneBestH), fmt(tuneBestV), tuneBestEscape);
            Msg.info("%d of %d combos were not pulled back. Applied as Custom.", tunePassed, tuneTested);
        } else {
            horizontal.set(tuneSavedH);
            vertical.set(tuneSavedV);
            profile.set(tuneSavedProfile);

            Msg.warning("All %d combos were pulled back by the server - nothing applied. The anticheat simulates webs.", tuneTested);
        }

        tuneStage = TuneStage.Off;
        tuneCombos.clear();
        tuneIndex = 0;
    }

    private void holdKeys() {
        if (mc.options == null) return;

        tuneKeysHeld = true;
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(true);
    }

    private void releaseKeys() {
        if (mc.options == null || !tuneKeysHeld) return;

        mc.options.forwardKey.setPressed(tuneSavedForward);
        mc.options.jumpKey.setPressed(tuneSavedJump);
        tuneKeysHeld = false;
    }

    /** True when one of the blocks the player is standing in is a cobweb. */
    private boolean insideWeb() {
        if (mc.player == null || mc.world == null) return false;

        Box box = mc.player.getBoundingBox().expand(0.2);
        BlockPos min = BlockPos.ofFloored(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ);

        for (BlockPos pos : BlockPos.iterate(min, max)) {
            if (mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) return true;
        }
        return false;
    }

    /** Closest cobweb around the player, or null when there is none within {@link #WEB_RADIUS}. */
    private BlockPos nearestWeb() {
        if (mc.player == null || mc.world == null) return null;

        BlockPos origin = mc.player.getBlockPos();
        Vec3d me = mc.player.getEntityPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(origin.add(-WEB_RADIUS, -3, -WEB_RADIUS), origin.add(WEB_RADIUS, 3, WEB_RADIUS))) {
            if (!mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) continue;

            double distance = me.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.toImmutable();
            }
        }

        return best;
    }

    /** Turns the player towards the nearest web; forward is held, so this walks into it. */
    private void steerToWeb() {
        BlockPos web = nearestWeb();
        if (web == null) return;

        Vec3d me = mc.player.getEntityPos();
        double dx = web.getX() + 0.5 - me.x;
        double dz = web.getZ() + 0.5 - me.z;

        mc.player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String fmt(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format("%.1f", value);
    }

    public enum Profile {
        /** No movement prediction: full no-slow, the settings-most anarchy servers run. */
        Vanilla,
        /** Distance/limit checks without stuck-speed simulation. */
        NCP,
        /** Same idea as NCP, slightly tighter numbers. */
        Themis,
        /** Short burst only - Grim re-simulates webs itself. */
        Grim,
        /** Whatever the sliders say. */
        Custom
    }

    public enum LagbackAction {
        Notify,
        Pause,
        Ignore
    }
}
