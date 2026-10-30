package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.homovore.PlacementManager;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PiglinEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombifiedPiglinEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.GoatEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Port of Homovore's <b>AutoCrystal</b> ({@code dev.leonetic.features.modules.combat.AutoCrystalModule},
 * leonetics/homovore-public, AGPL-3.0), replacing the BlackOut {@code crystal-aura}.
 *
 * <p>What is kept, because it is what makes the original different:
 * <ul>
 *   <li><b>Base place</b> - below the target, layer by layer from their feet down, placing the
 *       obsidian ourselves and putting the crystal on it, whenever that bound damage beats a normal
 *       placement;</li>
 *   <li><b>Anti Chinese</b> - crystal the burrow blocks (piston, sticky piston, piston head, moving
 *       piston, redstone block) around you, place only next to one, break only next to one;</li>
 *   <li><b>Damage sync</b> - skip targets in the invulnerability window who have turtle master and
 *       no armour gap, because the pop will not land;</li>
 *   <li><b>Crystal tracking</b> - a placement we made is remembered for
 *       {@value #CRYSTAL_TRACK_MS}ms and re-preferred while it is pending or the crystal is alive,
 *       so the aura keeps working one spot instead of hopping;</li>
 *   <li><b>Instant break</b> - with {@code break-delay} at 0 a crystal is broken straight off its
 *       spawn packet, before the next tick;</li>
 *   <li><b>Anti surround</b> - blocks Speed Mine is partway through are ignored by the exposure
 *       raycast, so the aura does not think a target is covered by a block that is about to go;</li>
 *   <li>the dynamic minimum damage (a target under 20% health or with broken armour needs
 *       {@code min-damage / 4}), and the no-self-pop rule.</li>
 * </ul>
 *
 * <p>Adapted to Meteor: settings are Meteor settings, placement goes through the ported
 * {@link PlacementManager}, rotation through {@link Rotations} (a silent look packet that the break
 * packet follows), damage through {@link DamageUtils#crystalDamage} - the same vanilla explosion
 * model the original hand-rolls - and the box render is Homovore's gradient / smoothed box / pulse
 * on Meteor's renderer.
 */
public class AutoCrystal extends Module {
    private static final double PLACE_RANGE = 6.0;
    private static final double BASE_PLACE_RANGE = 6.0;
    private static final double BREAK_RANGE = 3.0;

    /** Below this fraction of max health, or with broken armour, min damage is divided by this. */
    private static final int BALANCE = 4;
    private static final double HEALTH_BALANCE = 0.20;

    private static final boolean NO_SELF_POP = true;

    private static final long PLACE_PENDING_MS = 40;
    private static final long CRYSTAL_TRACK_MS = 1750;

    private static final int[][] FACE_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("Targets");
    private final SettingGroup sgBase = settings.createGroup("Base Place");
    private final SettingGroup sgAntiChinese = settings.createGroup("Anti Chinese");
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgExtra = settings.createGroup("Extra");

    // ------------------------------------------------------------------ general

    private final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
        .name("place")
        .description("Place crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Milliseconds between placements.")
        .defaultValue(0)
        .range(0, 2000)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Boolean> breakCrystals = sgGeneral.add(new BoolSetting.Builder()
        .name("break")
        .description("Break crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> breakDelay = sgGeneral.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Milliseconds between breaks. At 0 the spawn-packet break path is used as well.")
        .defaultValue(0)
        .range(0, 2000)
        .sliderRange(0, 500)
        .build()
    );

    private final Setting<Boolean> look = sgGeneral.add(new BoolSetting.Builder()
        .name("look")
        .description("Turn towards the spot before placing or breaking, so nothing depends on where you happen to be aiming.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Only the server sees the turn; your camera stays where you put it.")
        .defaultValue(true)
        .visible(look::get)
        .build()
    );

    private final Setting<Double> minDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("Only place or break if the hit does at least this much damage.")
        .defaultValue(4.0)
        .range(0.0, 36.0)
        .sliderRange(0.0, 12.0)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Never take more than this from our own crystal.")
        .defaultValue(4.0)
        .range(0.0, 36.0)
        .sliderRange(0.0, 12.0)
        .build()
    );

    private final Setting<Boolean> damageSync = sgGeneral.add(new BoolSetting.Builder()
        .name("damage-sync")
        .description("Skip targets whose armour is not popped yet, so the break is not eaten by the invulnerability window.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> antiSurround = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-surround")
        .description("Treat blocks Speed Mine is already breaking as gone when checking exposure.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> antiSurroundCompletion = sgGeneral.add(new IntSetting.Builder()
        .name("anti-surround-completion")
        .description("How far through a break Speed Mine has to be before the block counts as gone, in percent.")
        .defaultValue(70)
        .range(0, 100)
        .sliderRange(0, 100)
        .visible(antiSurround::get)
        .build()
    );

    // ------------------------------------------------------------------ targets

    private final Setting<Boolean> players = sgTargets.add(new BoolSetting.Builder()
        .name("players")
        .description("Target players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> ignoreFriends = sgTargets.add(new BoolSetting.Builder()
        .name("ignore-friends")
        .description("Never target Meteor friends.")
        .defaultValue(true)
        .visible(players::get)
        .build()
    );

    private final Setting<Boolean> hostiles = sgTargets.add(new BoolSetting.Builder()
        .name("hostiles")
        .description("Target hostile mobs.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> neutrals = sgTargets.add(new BoolSetting.Builder()
        .name("neutrals")
        .description("Target neutral mobs (endermen, piglins, spiders, wolves, golems...).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> passives = sgTargets.add(new BoolSetting.Builder()
        .name("passives")
        .description("Target passive mobs.")
        .defaultValue(false)
        .build()
    );

    // ------------------------------------------------------------------ base place

    private final Setting<Boolean> basePlace = sgBase.add(new BoolSetting.Builder()
        .name("base-place")
        .description("Place the obsidian base ourselves under the target when that hits harder than a ready-made crystal spot.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> basePlaceEnemyRange = sgBase.add(new DoubleSetting.Builder()
        .name("base-place-enemy-range")
        .description("Only base place against a target within this distance.")
        .defaultValue(7.0)
        .range(0.1, 15.0)
        .sliderRange(1.0, 15.0)
        .visible(basePlace::get)
        .build()
    );

    private final Setting<Double> basePlaceMinDamage = sgBase.add(new DoubleSetting.Builder()
        .name("base-place-min-damage")
        .description("Minimum bound damage for a base placement.")
        .defaultValue(5.0)
        .range(0.0, 36.0)
        .sliderRange(0.0, 12.0)
        .visible(basePlace::get)
        .build()
    );

    // ------------------------------------------------------------------ anti chinese

    private final Setting<Boolean> antiChinese = sgAntiChinese.add(new BoolSetting.Builder()
        .name("anti-chinese")
        .description("Crystal the burrow blocks around you when a player is close.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> antiChineseRange = sgAntiChinese.add(new DoubleSetting.Builder()
        .name("range")
        .description("Only crystal burrow blocks this close to you.")
        .defaultValue(5.0)
        .range(1.0, 5.14)
        .sliderRange(1.0, 5.14)
        .visible(antiChinese::get)
        .build()
    );

    private final Setting<Double> antiChineseEnemyRange = sgAntiChinese.add(new DoubleSetting.Builder()
        .name("enemy-range")
        .description("Only run the anti-chinese path when a target is this close.")
        .defaultValue(8.0)
        .range(1.0, 30.0)
        .sliderRange(1.0, 20.0)
        .visible(antiChinese::get)
        .build()
    );

    // ------------------------------------------------------------------ render

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
        .name("render")
        .description("How the last placement is drawn.")
        .defaultValue(RenderMode.Gradient)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour.")
        .defaultValue(new SettingColor(130, 80, 255, 45))
        .visible(() -> renderMode.get() != RenderMode.None)
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line colour.")
        .defaultValue(new SettingColor(130, 80, 255, 255))
        .visible(() -> renderMode.get() != RenderMode.None)
        .build()
    );

    private final Setting<Double> lineWidth = sgRender.add(new DoubleSetting.Builder()
        .name("line-width")
        .description("Thickness of the edges in blocks. Meteor's line renderer is one pixel, so above 0 the edges are drawn as thin boxes.")
        .defaultValue(0.01)
        .range(0.0, 0.05)
        .sliderRange(0.0, 0.03)
        .visible(() -> renderMode.get() != RenderMode.None)
        .build()
    );

    private final Setting<Double> gradientHeight = sgRender.add(new DoubleSetting.Builder()
        .name("gradient-height")
        .description("How far the gradient walls reach down, in blocks.")
        .defaultValue(0.7)
        .range(0.0, 1.0)
        .sliderRange(0.0, 1.0)
        .visible(() -> renderMode.get() == RenderMode.Gradient || renderMode.get() == RenderMode.GradientSmooth)
        .build()
    );

    private final Setting<Integer> renderTime = sgRender.add(new IntSetting.Builder()
        .name("render-time")
        .description("Ticks the placement stays drawn.")
        .defaultValue(10)
        .range(1, 40)
        .sliderRange(1, 40)
        .visible(() -> renderMode.get() != RenderMode.None)
        .build()
    );

    private final Setting<Integer> smoothness = sgRender.add(new IntSetting.Builder()
        .name("smoothness")
        .description("How quickly the drawn box catches up to the real one. 1 = instant.")
        .defaultValue(10)
        .range(1, 50)
        .sliderRange(1, 50)
        .visible(() -> renderMode.get() == RenderMode.BoxSmooth || renderMode.get() == RenderMode.GradientSmooth)
        .build()
    );

    private final Setting<SettingColor> pulseColor = sgRender.add(new ColorSetting.Builder()
        .name("pulse-color")
        .description("Colour the pulse expands in.")
        .defaultValue(new SettingColor(130, 80, 255, 150))
        .visible(() -> renderMode.get() == RenderMode.Pulse)
        .build()
    );

    private final Setting<Double> pulseSpeed = sgRender.add(new DoubleSetting.Builder()
        .name("pulse-speed")
        .description("Pulses per second.")
        .defaultValue(2.0)
        .range(0.2, 6.0)
        .sliderRange(0.2, 6.0)
        .visible(() -> renderMode.get() == RenderMode.Pulse)
        .build()
    );

    private final Setting<Double> pulseExpand = sgRender.add(new DoubleSetting.Builder()
        .name("pulse-expand")
        .description("How far the box grows on a pulse.")
        .defaultValue(0.35)
        .range(0.0, 1.0)
        .sliderRange(0.0, 0.5)
        .visible(() -> renderMode.get() == RenderMode.Pulse)
        .build()
    );

    // ------------------------------------------------------------------ extra

    private final Setting<Boolean> debug = sgExtra.add(new BoolSetting.Builder()
        .name("debug")
        .description("Print the per-tick scan time in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> packetLog = sgExtra.add(new BoolSetting.Builder()
        .name("packet-log")
        .description("Log outgoing packet counts and placement attempts, for tuning against a server.")
        .defaultValue(false)
        .build()
    );

    // ------------------------------------------------------------------ state

    private final PlacementManager placements = new PlacementManager();
    private final PlacementManager.Listener blockListener = this::onPlacementBlockUpdate;
    private final MsTimer placeTimer = new MsTimer();
    private final MsTimer breakTimer = new MsTimer();
    private final Map<BlockPos, Long> crystalPlaces = new HashMap<>();
    private final Set<Integer> deadIds = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> seeThrough = new HashSet<>();

    private float lastBestDamage;
    private BlockPos lastPlaceAir;
    private BlockPos renderPos;
    private long renderStartMs;
    private Box smoothBox;
    private double lastCalcMs;

    private int diagPlaceAttempt;
    private int diagPlaceSent;
    private int diagBreakSent;
    private int diagInstaBreak;
    private long diagWindowStart;
    private int diagWindowSends;
    private int diagPeakWindow;
    private long diagLifetimeSends;
    private long diagLastTickSends;
    private int diagPeakTickSends;

    public AutoCrystal() {
        super(AddonTemplate.COMBAT_CATEGORY, "auto-crystal",
            "Places and breaks end crystals, ported from Homovore: base place, anti-chinese, damage sync and crystal tracking.");
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onActivate() {
        crystalPlaces.clear();
        deadIds.clear();
        seeThrough.clear();
        placeTimer.reset();
        breakTimer.reset();
        lastBestDamage = 0;
        lastPlaceAir = null;
        lastCalcMs = 0;
        renderPos = null;
        smoothBox = null;
        resetDiag();
        placements.setLook(lookMode());
        placements.addListener(blockListener);
    }

    @Override
    public void onDeactivate() {
        placements.removeListener(blockListener);
        placements.clear();
        crystalPlaces.clear();
        deadIds.clear();
        seeThrough.clear();
        lastBestDamage = 0;
        lastPlaceAir = null;
        renderPos = null;
        smoothBox = null;
    }

    @Override
    public String getInfoString() {
        return lastBestDamage > 0 ? String.format("%.1f", lastBestDamage) : null;
    }

    // ------------------------------------------------------------------ tick

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.player.isDead()) return;

        placements.setLook(lookMode());
        placements.flushQueue();

        if (shouldDefer()) {
            lastBestDamage = 0;
            return;
        }

        ageCrystalPlaces();
        updateSeeThrough();

        boolean antiChineseReady = antiChinese.get() && enemyNearby(antiChineseEnemyRange.get());

        boolean broke = false;
        if (breakCrystals.get() && breakTimer.passed(breakDelay.get())) {
            EndCrystalEntity target = findBestCrystal(collectTargets(15.0));
            if (target != null) {
                breakCrystal(target);
                breakTimer.reset();
                broke = true;
            }
        }

        if (!broke && antiChineseReady && breakTimer.passed(breakDelay.get())) {
            EndCrystalEntity crystal = findAntiChineseCrystal();
            if (crystal != null) {
                breakCrystal(crystal);
                breakTimer.reset();
            }
        }

        boolean canPlace = place.get() && placeTimer.passed(placeDelay.get());

        long calcStart = System.nanoTime();
        double scanRadius = 18.0 + (basePlace.get() ? BASE_PLACE_RANGE : 0.0);
        List<TargetCache> targets = collectTargets(scanRadius);

        PlaceTarget crystalTarget = canPlace ? findBestPlace(targets) : null;
        BasePlaceTarget baseTarget = basePlace.get() ? findBestBasePlace(targets) : null;
        lastCalcMs = (System.nanoTime() - calcStart) / 1_000_000.0;

        if (debug.get()) info("calc %.3f ms", lastCalcMs);

        boolean baseWins = baseTarget != null && (crystalTarget == null || baseTarget.damage > crystalTarget.damage);
        boolean placed = false;

        if (baseWins) {
            if (enqueueBase(baseTarget)) {
                placements.flushQueue();
                if (canPlace) {
                    doPlace(new PlaceTarget(baseTarget.base, baseTarget.damage), true);
                    placeTimer.reset();
                    placed = true;
                }
            }
        } else if (crystalTarget != null) {
            doPlace(crystalTarget, false);
            placeTimer.reset();
            placed = true;
        }

        if (!placed && antiChineseReady && canPlace) {
            PlaceTarget burrow = findAntiChinesePlace();
            if (burrow != null) {
                doPlace(burrow, false);
                placeTimer.reset();
            }
        }

        if (packetLog.get()) {
            int tickDelta = (int) (diagLifetimeSends - diagLastTickSends);
            diagLastTickSends = diagLifetimeSends;
            if (tickDelta > diagPeakTickSends) diagPeakTickSends = tickDelta;

            if (diagWindowStart == 0) {
                diagWindowStart = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - diagWindowStart >= 1000) {
                flushDiag();
            }
        }
    }

    /** Homovore holds fire while we are eating or blocking on the main hand. */
    private boolean shouldDefer() {
        return mc.player.isUsingItem() && mc.player.getActiveHand() == Hand.MAIN_HAND;
    }

    private PlacementManager.Look lookMode() {
        if (!look.get()) return PlacementManager.Look.Off;
        return silent.get() ? PlacementManager.Look.Silent : PlacementManager.Look.Visible;
    }

    /** Turn to an angle and run the packet that needs it, honouring {@code look} and {@code silent}. */
    private void aim(float yaw, float pitch, Runnable action) {
        if (mc.player == null) return;

        switch (lookMode()) {
            case Off -> action.run();
            case Silent -> Rotations.rotate(yaw, pitch, 60, action);
            case Visible -> {
                mc.player.setYaw(yaw);
                mc.player.setPitch(pitch);
                action.run();
            }
        }
    }

    // ------------------------------------------------------------------ packets

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.world == null) return;

        if (event.packet instanceof EntityStatusS2CPacket status) {
            if (status.getStatus() != 3) return;
            Entity entity = status.getEntity(mc.world);
            if (entity != null) deadIds.add(entity.getId());
            return;
        }

        if (event.packet instanceof EntitySpawnS2CPacket spawn) {
            mc.execute(() -> onCrystalSpawn(spawn));
            return;
        }

        if (event.packet instanceof BlockUpdateS2CPacket update) {
            handleBlockUpdate(update);
            return;
        }

        if (event.packet instanceof BundleS2CPacket bundle) {
            for (var sub : bundle.getPackets()) {
                if (sub instanceof BlockUpdateS2CPacket update) handleBlockUpdate(update);
            }
        }
    }

    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!packetLog.get()) return;
        diagWindowSends++;
        diagLifetimeSends++;
    }

    /** Homovore's spawn-packet break: with no break delay a fresh crystal is popped before the tick. */
    private void onCrystalSpawn(EntitySpawnS2CPacket packet) {
        if (!breakCrystals.get() || breakDelay.get() != 0) return;
        if (packet.getEntityType() != EntityType.END_CRYSTAL) return;
        if (mc.player == null || mc.player.isDead() || shouldDefer()) return;

        Vec3d pos = new Vec3d(packet.getX(), packet.getY(), packet.getZ());
        Box box = EntityType.END_CRYSTAL.getDimensions().getBoxAt(pos);

        Vec3d reachEye = bestReachEye(box);
        if (sqDistToBox(reachEye, box) > BREAK_RANGE * BREAK_RANGE) return;
        if (!selfDamageOk(pos, null, playerHealth())) return;

        float[] angles = Angles.of(reachEye, getClosestPointToEye(reachEye, box));
        if (!canBreakCrystal(box, reachEye, angles[0], angles[1])) return;

        diagInstaBreak++;

        int id = packet.getEntityId();
        aim(angles[0], angles[1], () -> {
            if (mc.world == null) return;
            Entity spawned = mc.world.getEntityById(id);
            if (spawned instanceof EndCrystalEntity crystal) attack(crystal);
        });
    }

    private void handleBlockUpdate(BlockUpdateS2CPacket packet) {
        BlockPos pos = packet.getPos().toImmutable();
        boolean nowAir = packet.getState().isAir();
        if (!nowAir) crystalPlaces.remove(pos);

        mc.execute(() -> {
            for (PlacementManager.Listener listener : placements.listeners()) {
                listener.onBlockUpdate(pos, nowAir);
            }
            placements.flushQueue();
        });
    }

    /** A block turned to air: something may now be placeable, and a fresh crystal may be breakable. */
    private void onPlacementBlockUpdate(BlockPos pos, boolean nowAir) {
        if (!nowAir || mc.player == null || mc.player.isDead() || shouldDefer()) return;
        if (mc.player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(pos)) > 64.0) return;

        updateSeeThrough();
        List<TargetCache> targets = collectTargets(18.0);

        if (breakCrystals.get() && breakTimer.passed(breakDelay.get())) {
            EndCrystalEntity crystal = findBestCrystal(targets);
            if (crystal != null) {
                breakCrystal(crystal);
                breakTimer.reset();
                return;
            }
        }

        if (!place.get() || !placeTimer.passed(placeDelay.get())) return;

        PlaceTarget target = findBestPlace(targets);
        if (target != null) {
            doPlace(target, false);
            placeTimer.reset();
        }
    }

    // ------------------------------------------------------------------ break

    private EndCrystalEntity findBestCrystal(List<TargetCache> targets) {
        EndCrystalEntity best = null;
        float bestDamage = 0;
        double breakRangeSq = BREAK_RANGE * BREAK_RANGE;
        Box searchArea = mc.player.getBoundingBox().expand(BREAK_RANGE + 2.0);

        for (Entity entity : mc.world.getOtherEntities(null, searchArea)) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;

            Box box = crystal.getBoundingBox();
            if (sqDistToBox(bestReachEye(box), box) > breakRangeSq) continue;

            Vec3d center = crystal.getEntityPos();
            if (!anyTargetWithin(targets, center, 144.0)) continue;
            if (!selfDamageOk(center, null, playerHealth())) continue;

            float total = 0;
            boolean any = false;
            for (TargetCache target : targets) {
                if (target.pos.squaredDistanceTo(center) > 144.0) continue;
                if (damageSync.get() && shouldHold(target)) continue;

                float dmg = damage(target, center, null);
                if (dmg < dynamicMin(target)) continue;

                total += dmg;
                any = true;
            }

            if (!any || total <= bestDamage) continue;

            bestDamage = total;
            best = crystal;
        }

        lastBestDamage = bestDamage;
        return best;
    }

    private void breakCrystal(EndCrystalEntity crystal) {
        Vec3d eye = mc.player.getEyePos();
        Box box = crystal.getBoundingBox();
        float[] angles = Angles.of(eye, getClosestPointToEye(eye, box));
        if (!canBreakCrystal(box, bestReachEye(box), angles[0], angles[1])) return;

        diagBreakSent++;

        attack(crystal, angles[0], angles[1]);
    }

    private void attack(EndCrystalEntity crystal, float yaw, float pitch) {
        int id = crystal.getId();

        aim(yaw, pitch, () -> {
            if (mc.world == null) return;
            Entity entity = mc.world.getEntityById(id);
            if (entity instanceof EndCrystalEntity live) attack(live);
        });
    }

    /**
     * Swing at the crystal itself. The look packet goes out first, then the attack and the swing, so
     * the server sees the break from the position it was aimed from. An id the client has already
     * dropped is never sent: the server kicks for attacking an entity it does not know.
     */
    private void attack(EndCrystalEntity crystal) {
        if (crystal == null || crystal.isRemoved() || mc.player == null || mc.player.networkHandler == null) return;
        if (mc.world == null || mc.world.getEntityById(crystal.getId()) != crystal) return;

        mc.player.networkHandler.sendPacket(
            PlayerInteractEntityC2SPacket.attack(crystal, mc.player.isSneaking()));
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean canBreakCrystal(Box box, Vec3d eye, float yaw, float pitch) {
        if (box.contains(eye)) return true;
        Vec3d reachEnd = eye.add(lookVector(yaw, pitch).multiply(BREAK_RANGE));
        return box.raycast(eye, reachEnd).isPresent();
    }

    /**
     * Homovore's damage sync: a target with turtle master, no armour gap and a fresh invulnerability
     * window will eat the hit, so do not spend a crystal on them.
     */
    private boolean shouldHold(TargetCache target) {
        if (!(target.entity instanceof PlayerEntity player)) return false;

        boolean turtleMaster = player.hasStatusEffect(StatusEffects.RESISTANCE)
            && player.hasStatusEffect(StatusEffects.SLOWNESS);
        return turtleMaster && !hasArmorGap(player) && player.timeUntilRegen > 10;
    }

    private boolean hasArmorGap(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (player.getEquippedStack(slot).isEmpty()) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ place

    private PlaceTarget findBestPlace(List<TargetCache> targets) {
        Vec3d eye = mc.player.getEyePos();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(PLACE_RANGE);
        long now = System.currentTimeMillis();
        boolean inEnd = mc.world.getRegistryKey().equals(World.END);

        double placeRangeSq = PLACE_RANGE * PLACE_RANGE;
        double breakRangeSq = BREAK_RANGE * BREAK_RANGE;
        float health = playerHealth();

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int rr = r * r;
        List<PlaceCandidate> candidates = new ArrayList<>();

        for (int x = -r; x <= r; x++) {
            int xx = x * x;
            for (int y = -r; y <= r; y++) {
                int xxyy = xx + y * y;
                for (int z = -r; z <= r; z++) {
                    if (xxyy + z * z > rr) continue;

                    cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    BlockState baseState = mc.world.getBlockState(cursor);
                    if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) continue;

                    int bx = cursor.getX();
                    int by = cursor.getY();
                    int bz = cursor.getZ();
                    int ay = by + 1;

                    cursor.set(bx, ay, bz);
                    BlockState airState = mc.world.getBlockState(cursor);
                    boolean open = airState.isAir() || (airState.isOf(Blocks.FIRE) && inEnd);
                    if (!open || sqDistToBox(eye, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq) continue;

                    double cx = bx + 0.5;
                    double cy = by + 1.0;
                    double cz = bz + 0.5;
                    Vec3d center = new Vec3d(cx, cy, cz);

                    Box crystalBox = new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
                    if (sqDistToBox(bestReachEye(crystalBox), crystalBox) > breakRangeSq) continue;

                    float bound = 0;
                    boolean any = false;
                    for (TargetCache target : targets) {
                        if (target.pos.squaredDistanceTo(center) > 144.0) continue;

                        float dmg = boundDamage(target, center);
                        if (dmg < dynamicMin(target)) continue;

                        bound += dmg;
                        any = true;
                    }
                    if (!any) continue;

                    candidates.add(new PlaceCandidate(new BlockPos(bx, by, bz), new BlockPos(bx, ay, bz), center, bound));
                }
            }
        }

        if (candidates.isEmpty()) return null;

        candidates.sort((a, b) -> Float.compare(b.bound, a.bound));
        BlockPos locked = lockedPlacement(now, breakRangeSq);

        PlaceTarget best = null;
        for (PlaceCandidate candidate : candidates) {
            if (best != null && candidate.bound <= best.damage) break;
            if (locked != null && !candidate.airPos.equals(locked)) continue;
            if (isBlocked(candidate.airPos) || isPending(candidate.airPos, now)) continue;
            if (!selfDamageOk(candidate.crystalCenter, null, health)) continue;

            float total = 0;
            boolean any = false;
            for (TargetCache target : targets) {
                if (target.pos.squaredDistanceTo(candidate.crystalCenter) > 144.0) continue;

                float dmg = damage(target, candidate.crystalCenter, null);
                if (dmg < dynamicMin(target)) continue;

                total += dmg;
                any = true;
            }
            if (!any || (best != null && total <= best.damage)) continue;

            best = new PlaceTarget(candidate.base, total);
        }

        return best;
    }

    /**
     * Homovore's crystal tracking: while our last placement is still pending or its crystal is alive
     * and reachable, only that spot may be used again, so the aura finishes a spot instead of hopping.
     */
    private BlockPos lockedPlacement(long now, double breakRangeSq) {
        if (lastPlaceAir == null) return null;

        if (isPending(lastPlaceAir, now) || hasLiveCrystalAt(lastPlaceAir)) {
            double cx = lastPlaceAir.getX() + 0.5;
            double cy = lastPlaceAir.getY();
            double cz = lastPlaceAir.getZ() + 0.5;
            Box crystalBox = new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
            if (sqDistToBox(bestReachEye(crystalBox), crystalBox) <= breakRangeSq) return lastPlaceAir;
        }

        lastPlaceAir = null;
        return null;
    }

    /**
     * Homovore's base place: from the target's feet downwards, one layer at a time, the best base we
     * could put an obsidian on ourselves. The chain is kept as a queue so the obsidian and the
     * crystal land in one batch.
     */
    private BasePlaceTarget findBestBasePlace(List<TargetCache> targets) {
        LivingEntity primary = findClosestBaseTarget();
        if (primary == null) return null;

        Vec3d eye = mc.player.getEyePos();
        Vec3d playerCenter = mc.player.getEntityPos();
        float health = playerHealth();
        double baseMin = basePlaceMinDamage.get();

        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(BASE_PLACE_RANGE);
        int rr = r * r;
        double rangeSq = BASE_PLACE_RANGE * BASE_PLACE_RANGE;

        int topLayerY = primary.getBlockPos().getY() - 1;
        int bottomLayerY = playerPos.getY() - r;

        for (int by = topLayerY; by >= bottomLayerY; by--) {
            BlockPos layerBest = null;
            float layerDamage = 0;

            int dy = by - playerPos.getY();
            int dyy = dy * dy;
            if (dyy > rr) continue;

            for (int dx = -r; dx <= r; dx++) {
                int dxxdyy = dx * dx + dyy;
                for (int dz = -r; dz <= r; dz++) {
                    if (dxxdyy + dz * dz > rr) continue;

                    BlockPos base = new BlockPos(playerPos.getX() + dx, by, playerPos.getZ() + dz);
                    BlockPos crystalPos = base.up();
                    BlockState crystalState = mc.world.getBlockState(crystalPos);
                    if (!crystalState.isAir() && !crystalState.isReplaceable()) continue;
                    if (isBlocked(crystalPos)) continue;
                    if (playerCenter.squaredDistanceTo(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5) > rangeSq) continue;
                    if (!canPlaceBase(base)) continue;
                    if (sqDistToBox(eye, crystalPos.getX(), crystalPos.getY(), crystalPos.getZ(),
                        crystalPos.getX() + 1, crystalPos.getY() + 1, crystalPos.getZ() + 1) > PLACE_RANGE * PLACE_RANGE) continue;

                    Vec3d center = new Vec3d(crystalPos.getX() + 0.5, crystalPos.getY(), crystalPos.getZ() + 0.5);
                    Box crystalBox = new Box(center.x - 1.0, center.y, center.z - 1.0, center.x + 1.0, center.y + 2.0, center.z + 1.0);
                    if (sqDistToBox(bestReachEye(crystalBox), crystalBox) > BREAK_RANGE * BREAK_RANGE) continue;
                    if (!anyTargetWithin(targets, center, 144.0)) continue;

                    float bound = 0;
                    boolean any = false;
                    for (TargetCache target : targets) {
                        if (target.pos.squaredDistanceTo(center) > 144.0) continue;

                        float dmg = boundDamage(target, center);
                        if (dmg < dynamicMin(target)) continue;

                        bound += dmg;
                        any = true;
                    }
                    if (!any || bound < baseMin || bound <= layerDamage) continue;

                    float total = 0;
                    boolean anyHit = false;
                    for (TargetCache target : targets) {
                        if (target.pos.squaredDistanceTo(center) > 144.0) continue;

                        float dmg = damage(target, center, base);
                        if (dmg < dynamicMin(target)) continue;

                        total += dmg;
                        anyHit = true;
                    }
                    if (!anyHit || total < baseMin || total <= layerDamage) continue;

                    layerDamage = total;
                    layerBest = base;
                }
            }

            if (layerBest != null) return new BasePlaceTarget(layerBest, layerDamage);
        }

        return null;
    }

    private boolean enqueueBase(BasePlaceTarget target) {
        if (mc.player == null) return false;
        if (!InvUtils.findInHotbar(Items.OBSIDIAN).found()) return false;
        return placements.enqueue(target.base, Items.OBSIDIAN);
    }

    private LivingEntity findClosestBaseTarget() {
        double range = basePlaceEnemyRange.get();
        double rangeSq = range * range;

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Box area = mc.player.getBoundingBox().expand(range);

        for (Entity entity : mc.world.getOtherEntities(mc.player, area)) {
            if (!(entity instanceof PlayerEntity player) || !isTarget(player)) continue;

            double dist = mc.player.squaredDistanceTo(player);
            if (dist > rangeSq || dist >= bestDist) continue;

            bestDist = dist;
            best = player;
        }

        return best;
    }

    /** Whether we could physically put an obsidian down here: in reach, empty, and nothing standing in it. */
    private boolean canPlaceBase(BlockPos pos) {
        if (mc.world.isOutOfHeightLimit(pos)) return false;
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (Vec3d.ofCenter(pos).distanceTo(mc.player.getEyePos()) > 5.154) return false;
        if (mc.player.getBoundingBox().intersects(new Box(pos))) return false;

        for (Entity entity : mc.world.getOtherEntities(mc.player, new Box(pos))) {
            if (entity instanceof ItemEntity || entity instanceof EndCrystalEntity) continue;
            return false;
        }

        return true;
    }

    private void doPlace(PlaceTarget target, boolean trustBase) {
        lastBestDamage = target.damage;
        diagPlaceAttempt++;

        if (placements.placeCrystal(target.base, trustBase)) {
            diagPlaceSent++;
            recordPlace(target.base.up());
        }
    }

    private void recordPlace(BlockPos airPos) {
        crystalPlaces.put(airPos, System.currentTimeMillis());
        lastPlaceAir = airPos;
        markRender(airPos);
    }

    private boolean isPending(BlockPos airPos, long now) {
        Long placedAt = crystalPlaces.get(airPos);
        return placedAt != null && now - placedAt < PLACE_PENDING_MS;
    }

    private boolean hasLiveCrystalAt(BlockPos airPos) {
        for (Entity entity : mc.world.getOtherEntities(null, new Box(airPos))) {
            if (entity instanceof EndCrystalEntity crystal && crystal.getBlockPos().equals(airPos)) return true;
        }
        return false;
    }

    /** Anything other than our own fresh crystal in the spot means the placement would fail. */
    private boolean isBlocked(BlockPos airPos) {
        for (Entity entity : mc.world.getOtherEntities(null, new Box(airPos))) {
            if (entity instanceof ItemEntity) continue;
            if (entity instanceof EndCrystalEntity crystal) {
                if (crystal.age < 5) continue;
                if (crystal.getBlockPos().equals(airPos)) continue;
            }
            return true;
        }
        return false;
    }

    private void ageCrystalPlaces() {
        long now = System.currentTimeMillis();
        crystalPlaces.entrySet().removeIf(entry -> now - entry.getValue() > CRYSTAL_TRACK_MS);
        if (mc.player.age % 100 == 0) deadIds.clear();
    }

    // ------------------------------------------------------------------ anti chinese

    private boolean enemyNearby(double range) {
        double rangeSq = range * range;
        Box area = mc.player.getBoundingBox().expand(range);

        for (Entity entity : mc.world.getOtherEntities(mc.player, area)) {
            if (!(entity instanceof PlayerEntity player) || !isTarget(player)) continue;
            if (mc.player.squaredDistanceTo(player) <= rangeSq) return true;
        }

        return false;
    }

    /** Place a crystal next to a burrow block, nearest first. */
    private PlaceTarget findAntiChinesePlace() {
        Vec3d eye = mc.player.getEyePos();
        BlockPos playerPos = mc.player.getBlockPos();
        int r = (int) Math.ceil(PLACE_RANGE);
        long now = System.currentTimeMillis();
        boolean inEnd = mc.world.getRegistryKey().equals(World.END);

        double placeRangeSq = PLACE_RANGE * PLACE_RANGE;
        double breakRangeSq = BREAK_RANGE * BREAK_RANGE;
        float health = playerHealth();

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        int rr = r * r;
        PlaceTarget best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            int xx = x * x;
            for (int y = -r; y <= r; y++) {
                int xxyy = xx + y * y;
                for (int z = -r; z <= r; z++) {
                    if (xxyy + z * z > rr) continue;

                    cursor.set(playerPos.getX() + x, playerPos.getY() + y, playerPos.getZ() + z);
                    BlockState baseState = mc.world.getBlockState(cursor);
                    if (!baseState.isOf(Blocks.OBSIDIAN) && !baseState.isOf(Blocks.BEDROCK)) continue;

                    int bx = cursor.getX();
                    int by = cursor.getY();
                    int bz = cursor.getZ();
                    int ay = by + 1;

                    cursor.set(bx, ay, bz);
                    BlockState airState = mc.world.getBlockState(cursor);
                    boolean open = airState.isAir() || (airState.isOf(Blocks.FIRE) && inEnd);
                    if (!open || sqDistToBox(eye, bx, ay, bz, bx + 1, ay + 1, bz + 1) > placeRangeSq) continue;

                    double cx = bx + 0.5;
                    double cy = by + 1.0;
                    double cz = bz + 0.5;
                    Vec3d center = new Vec3d(cx, cy, cz);

                    Box crystalBox = new Box(cx - 1.0, cy, cz - 1.0, cx + 1.0, cy + 2.0, cz + 1.0);
                    if (sqDistToBox(bestReachEye(crystalBox), crystalBox) > breakRangeSq) continue;

                    BlockPos airPos = new BlockPos(bx, ay, bz);
                    double distSq = eye.squaredDistanceTo(center);
                    if (distSq >= bestDistSq) continue;
                    if (!adjacentToBurrowBlock(airPos) || isBlocked(airPos) || isPending(airPos, now)) continue;
                    if (!selfDamageOk(center, null, health)) continue;

                    bestDistSq = distSq;
                    best = new PlaceTarget(new BlockPos(bx, by, bz), 0);
                }
            }
        }

        return best;
    }

    /** Break only a crystal that is next to a burrow block, nearest first. */
    private EndCrystalEntity findAntiChineseCrystal() {
        double breakRangeSq = BREAK_RANGE * BREAK_RANGE;
        EndCrystalEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Box searchArea = mc.player.getBoundingBox().expand(BREAK_RANGE + 2.0);

        for (Entity entity : mc.world.getOtherEntities(null, searchArea)) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;

            Box box = crystal.getBoundingBox();
            if (sqDistToBox(bestReachEye(box), box) > breakRangeSq) continue;

            Vec3d center = crystal.getEntityPos();
            if (!nearBurrowBlock(center) || !selfDamageOk(center, null, playerHealth())) continue;

            double dist = mc.player.squaredDistanceTo(crystal);
            if (dist >= bestDist) continue;

            bestDist = dist;
            best = crystal;
        }

        return best;
    }

    private boolean isBurrowBlock(BlockState state) {
        return state.isOf(Blocks.REDSTONE_BLOCK)
            || state.isOf(Blocks.PISTON)
            || state.isOf(Blocks.STICKY_PISTON)
            || state.isOf(Blocks.PISTON_HEAD)
            || state.isOf(Blocks.MOVING_PISTON);
    }

    private boolean nearBurrowBlock(Vec3d center) {
        int r = (int) Math.ceil(BREAK_RANGE);
        double radiusSq = BREAK_RANGE * BREAK_RANGE;
        double playerRangeSq = antiChineseRange.get() * antiChineseRange.get();
        Vec3d playerPos = mc.player.getEntityPos();

        int cx = MathHelper.floor(center.x);
        int cy = MathHelper.floor(center.y);
        int cz = MathHelper.floor(center.z);

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    int bx = cx + dx;
                    int by = cy + dy;
                    int bz = cz + dz;

                    double ox = bx + 0.5 - center.x;
                    double oy = by + 0.5 - center.y;
                    double oz = bz + 0.5 - center.z;
                    if (ox * ox + oy * oy + oz * oz > radiusSq) continue;
                    if (playerPos.squaredDistanceTo(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq) continue;

                    cursor.set(bx, by, bz);
                    if (isBurrowBlock(mc.world.getBlockState(cursor))) return true;
                }
            }
        }

        return false;
    }

    private boolean adjacentToBurrowBlock(BlockPos airPos) {
        double playerRangeSq = antiChineseRange.get() * antiChineseRange.get();
        Vec3d playerPos = mc.player.getEntityPos();

        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int[] offset : FACE_OFFSETS) {
            int bx = airPos.getX() + offset[0];
            int by = airPos.getY() + offset[1];
            int bz = airPos.getZ() + offset[2];
            if (playerPos.squaredDistanceTo(bx + 0.5, by + 0.5, bz + 0.5) > playerRangeSq) continue;

            cursor.set(bx, by, bz);
            if (isBurrowBlock(mc.world.getBlockState(cursor))) return true;
        }

        return false;
    }

    // ------------------------------------------------------------------ targets

    private List<TargetCache> collectTargets(double scanRadius) {
        List<TargetCache> out = new ArrayList<>();
        Box area = mc.player.getBoundingBox().expand(scanRadius);

        for (Entity entity : mc.world.getOtherEntities(mc.player, area)) {
            if (!(entity instanceof LivingEntity living) || living.isDead()) continue;
            if (deadIds.contains(entity.getId()) || !isTarget(living)) continue;

            boolean armorBroken = living instanceof PlayerEntity player && isAnyArmorBroken(player);
            out.add(new TargetCache(living, living.getEntityPos(), living.getBoundingBox(),
                living.getHealth(), living.getAbsorptionAmount(), armorBroken));
        }

        return out;
    }

    private boolean isTarget(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (living == mc.player || living.isDead()) return false;

        if (entity instanceof PlayerEntity player) {
            return players.get() && (!ignoreFriends.get() || !Friends.get().isFriend(player));
        }

        if (hostiles.get() && isHostile(entity)) return true;
        if (neutrals.get() && isNeutral(entity)) return true;
        return passives.get() && isPassive(entity);
    }

    private static boolean isHostile(Entity entity) {
        return entity instanceof Monster
            || entity instanceof WitherEntity
            || entity instanceof EnderDragonEntity;
    }

    private static boolean isNeutral(Entity entity) {
        return entity instanceof EndermanEntity
            || entity instanceof PiglinEntity
            || entity instanceof ZombifiedPiglinEntity
            || entity instanceof SpiderEntity
            || entity instanceof CaveSpiderEntity
            || entity instanceof PolarBearEntity
            || entity instanceof WolfEntity
            || entity instanceof BeeEntity
            || entity instanceof GoatEntity
            || entity instanceof IronGolemEntity;
    }

    private static boolean isPassive(Entity entity) {
        return entity instanceof PassiveEntity;
    }

    private boolean isAnyArmorBroken(PlayerEntity player) {
        int threshold = (int) (HEALTH_BALANCE * 100);

        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            var stack = player.getEquippedStack(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0) continue;

            int durabilityPct = 100 - (stack.getDamage() * 100 / stack.getMaxDamage());
            if (durabilityPct < threshold) return true;
        }

        return false;
    }

    private void updateSeeThrough() {
        seeThrough.clear();
        if (!antiSurround.get()) return;

        SpeedMine mine = Modules.get().get(SpeedMine.class);
        if (mine == null || !mine.isActive()) return;

        mine.collectMiningPositions(seeThrough, antiSurroundCompletion.get() / 100.0);
    }

    private static boolean anyTargetWithin(List<TargetCache> targets, Vec3d center, double maxDistSq) {
        for (TargetCache target : targets) {
            if (target.pos.squaredDistanceTo(center) <= maxDistSq) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ damage

    /** Meteor's crystal damage is the same vanilla model Homovore hand-rolls, including the exposure raycast. */
    private float damage(TargetCache target, Vec3d crystalPos, BlockPos phantomBase) {
        Box box = target.box;
        return DamageUtils.crystalDamage(target.entity, box.getCenter(), box, crystalPos, hitFactory(phantomBase));
    }

    private float selfDamage(Vec3d crystalPos, BlockPos phantomBase) {
        Box box = mc.player.getBoundingBox();
        return DamageUtils.crystalDamage(mc.player, box.getCenter(), box, crystalPos, hitFactory(phantomBase));
    }

    /**
     * Homovore's {@code maxDamage}: the bound a placement is ranked by, before the terrain raycast.
     * Exposure is treated as 1.0, so a spot behind a target's own trap still competes.
     */
    private float boundDamage(TargetCache target, Vec3d crystalPos) {
        Box box = target.box;
        return DamageUtils.crystalDamage(target.entity, box.getCenter(), box, crystalPos, NO_OCCLUSION);
    }

    private static final DamageUtils.RaycastFactory NO_OCCLUSION = (context, blockPos) -> null;

    /**
     * Homovore's exposure tester: a blast-resistant block shadows the target (resistance >= 600),
     * blocks Speed Mine is already through are skipped, and {@code phantomBase} counts as the
     * obsidian it is about to become.
     */
    private DamageUtils.RaycastFactory hitFactory(BlockPos phantomBase) {
        if (seeThrough.isEmpty() && phantomBase == null) return DamageUtils.HIT_FACTORY;

        return (context, blockPos) -> {
            if (phantomBase != null && blockPos.equals(phantomBase)) {
                BlockState obsidian = Blocks.OBSIDIAN.getDefaultState();
                return obsidian.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
            }

            if (!seeThrough.isEmpty() && seeThrough.contains(blockPos)) return null;

            BlockState state = mc.world.getBlockState(blockPos);
            if (state.isAir()) return null;
            if (state.getBlock().getBlastResistance() < 600.0f) return null;

            return state.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
        };
    }

    private double dynamicMin(TargetCache target) {
        double min = minDamage.get();
        float hp = target.hp + target.abs;
        if (hp / 36.0f <= HEALTH_BALANCE) min /= BALANCE;
        if (target.armorBroken) min /= BALANCE;
        return min;
    }

    private boolean selfDamageOk(Vec3d crystalPos, BlockPos phantomBase, float playerHealth) {
        float real = selfDamage(crystalPos, phantomBase);
        if (real > maxSelfDamage.get()) return false;
        if (NO_SELF_POP && real + 1.5f >= playerHealth) return false;
        return true;
    }

    private float playerHealth() {
        return mc.player.getHealth() + mc.player.getAbsorptionAmount();
    }

    // ------------------------------------------------------------------ render

    private void markRender(BlockPos crystalPos) {
        if (renderMode.get() == RenderMode.None) return;
        renderPos = crystalPos;
        renderStartMs = System.currentTimeMillis();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || renderPos == null || renderMode.get() == RenderMode.None) return;

        long elapsed = System.currentTimeMillis() - renderStartMs;
        long life = renderTime.get() * 50L;
        if (elapsed > life) {
            renderPos = null;
            smoothBox = null;
            return;
        }

        double fade = 1.0 - (double) elapsed / life;

        switch (renderMode.get()) {
            case Gradient -> drawGradient(event, false, fade);
            case GradientSmooth -> drawGradient(event, true, fade);
            case BoxSmooth -> drawBoxSmooth(event, fade);
            case Pulse -> drawPulse(event, elapsed, fade);
            default -> { }
        }
    }

    /** Homovore's {@code updateBoxSmooth}: ease the drawn box towards the real one. */
    private void updateSmoothBox(Box target) {
        if (smoothBox == null) {
            smoothBox = target;
            return;
        }

        double f = 1.0 / smoothness.get();
        smoothBox = new Box(
            lerp(smoothBox.minX, target.minX, f), lerp(smoothBox.minY, target.minY, f),
            lerp(smoothBox.minZ, target.minZ, f), lerp(smoothBox.maxX, target.maxX, f),
            lerp(smoothBox.maxY, target.maxY, f), lerp(smoothBox.maxZ, target.maxZ, f));
    }

    /** Homovore's BoxSmooth: filled box on the block under the crystal, edges at {@code line-width}. */
    private void drawBoxSmooth(Render3DEvent event, double fade) {
        updateSmoothBox(new Box(renderPos.down()));
        event.renderer.boxSides(smoothBox.minX, smoothBox.minY, smoothBox.minZ,
            smoothBox.maxX, smoothBox.maxY, smoothBox.maxZ, fade(sideColor.get(), fade), 0);
        drawEdges(event, smoothBox, lineColor.get(), fade);
    }

    /**
     * Homovore's gradient: a flat quad on the crystal's block plus four walls fading downwards.
     * {@code smooth} eases the quad towards the placement instead of snapping to it.
     */
    private void drawGradient(Render3DEvent event, boolean smooth, double fade) {
        Color side = fade(sideColor.get(), fade);
        Color clear = new Color(side.r, side.g, side.b, 0);
        BlockPos base = renderPos.down();

        double x1, z1, x2, z2, top;
        if (smooth) {
            updateSmoothBox(new Box(base));
            x1 = smoothBox.minX;
            z1 = smoothBox.minZ;
            x2 = smoothBox.maxX;
            z2 = smoothBox.maxZ;
            top = smoothBox.maxY;
        } else {
            x1 = base.getX();
            z1 = base.getZ();
            x2 = x1 + 1.0;
            z2 = z1 + 1.0;
            top = base.getY() + 1.0;
        }

        double bottom = top - gradientHeight.get();

        event.renderer.quad(x1, top, z1, x2, top, z1, x2, top, z2, x1, top, z2, side);
        wall(event, x1, z1, x2, z1, top, bottom, side, clear);
        wall(event, x2, z1, x2, z2, top, bottom, side, clear);
        wall(event, x2, z2, x1, z2, top, bottom, side, clear);
        wall(event, x1, z2, x1, z1, top, bottom, side, clear);

        Color line = fade(lineColor.get(), fade);
        edge(event, x1, top, z1, x2, top, z1, line);
        edge(event, x2, top, z1, x2, top, z2, line);
        edge(event, x2, top, z2, x1, top, z2, line);
        edge(event, x1, top, z2, x1, top, z1, line);

        edge(event, x1, top, z1, x1, bottom, z1, line);
        edge(event, x2, top, z1, x2, bottom, z1, line);
        edge(event, x2, top, z2, x2, bottom, z2, line);
        edge(event, x1, top, z2, x1, bottom, z2, line);
    }

    /** One gradient wall: opaque at the rim, fully faded at the bottom edge. */
    private void wall(Render3DEvent event, double ax, double az, double bx, double bz,
                      double top, double bottom, Color topColor, Color bottomColor) {
        event.renderer.quad(ax, bottom, az, ax, top, az, bx, top, bz, bx, bottom, bz,
            topColor, topColor, bottomColor, bottomColor);
    }

    /** Homovore's Pulse: a filled box that swells with a sine and fades with the render time. */
    private void drawPulse(Render3DEvent event, long elapsed, double fade) {
        double phase = elapsed / 1000.0 * pulseSpeed.get();
        double pulse = (Math.sin(phase * Math.PI * 2.0) + 1.0) * 0.5;

        Box box = new Box(renderPos).expand(pulse * pulseExpand.get());
        Color color = pulseColor.get();

        event.renderer.boxSides(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
            fade(color, fade), 0);
        drawEdges(event, box, color, fade);
    }

    private void drawEdges(Render3DEvent event, Box box, Color color, double fade) {
        Color line = fade(color, fade);

        edge(event, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, line);
        edge(event, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, line);
        edge(event, box.maxX, box.minY, box.maxZ, box.minX, box.minY, box.maxZ, line);
        edge(event, box.minX, box.minY, box.maxZ, box.minX, box.minY, box.minZ, line);

        edge(event, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, line);
        edge(event, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, line);
        edge(event, box.maxX, box.maxY, box.maxZ, box.minX, box.maxY, box.maxZ, line);
        edge(event, box.minX, box.maxY, box.maxZ, box.minX, box.maxY, box.minZ, line);

        edge(event, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, line);
        edge(event, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, line);
        edge(event, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, line);
        edge(event, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, line);
    }

    /**
     * Homovore draws every edge with a real line width. Meteor's line renderer is hard-wired to one
     * pixel, so a non-zero {@code line-width} is drawn as a thin box around the segment instead.
     */
    private void edge(Render3DEvent event, double ax, double ay, double az,
                      double bx, double by, double bz, Color color) {
        double t = lineWidth.get();
        if (t <= 0) {
            event.renderer.line(ax, ay, az, bx, by, bz, color);
            return;
        }

        double h = t / 2.0;
        event.renderer.boxSides(
            Math.min(ax, bx) - h, Math.min(ay, by) - h, Math.min(az, bz) - h,
            Math.max(ax, bx) + h, Math.max(ay, by) + h, Math.max(az, bz) + h, color, 0);
    }

    private static Color fade(Color color, double fade) {
        return new Color(color.r, color.g, color.b, (int) MathHelper.clamp(color.a * fade, 0, 255));
    }

    private static double lerp(double a, double b, double f) {
        return a + (b - a) * f;
    }

    // ------------------------------------------------------------------ geometry

    /**
     * Homovore's reach eye: the player's real eye, or the standing, sneaking or swimming eye height
     * of the current pose, whichever sees the most of the box.
     */
    private Vec3d bestReachEye(Box box) {
        Vec3d base = mc.player.getEyePos();
        float scale = (float) mc.player.getAttributeValue(EntityAttributes.SCALE);
        double standing = 1.62 * scale;
        double sneaking = 1.27 * scale;
        double swimming = 0.4 * scale;

        Vec3d pos = mc.player.getEntityPos();
        double[] heights = switch (mc.player.getPose()) {
            case GLIDING, SPIN_ATTACK, SWIMMING -> new double[]{swimming, standing, sneaking};
            case CROUCHING -> new double[]{sneaking, standing, swimming};
            default -> new double[]{standing, sneaking, swimming};
        };

        Vec3d bestEye = base;
        double bestSq = sqDistToBox(base, box);
        for (double h : heights) {
            Vec3d eye = new Vec3d(pos.x, pos.y + h, pos.z);
            double sq = sqDistToBox(eye, box);
            if (sq < bestSq) {
                bestSq = sq;
                bestEye = eye;
            }
        }

        return bestEye;
    }

    private static double sqDistToBox(Vec3d p, Box box) {
        return sqDistToBox(p, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static double sqDistToBox(Vec3d p, double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
        double dx = p.x < minX ? minX - p.x : (p.x > maxX ? p.x - maxX : 0);
        double dy = p.y < minY ? minY - p.y : (p.y > maxY ? p.y - maxY : 0);
        double dz = p.z < minZ ? minZ - p.z : (p.z > maxZ ? p.z - maxZ : 0);
        return dx * dx + dy * dy + dz * dz;
    }

    /** The point on a box closest to the eye, nudged a 16th inside so the ray always actually crosses it. */
    private Vec3d getClosestPointToEye(Vec3d eye, Box box) {
        final double vec = 1.0 / 16.0;
        final double eps = 1e-9;

        double x = MathHelper.clamp(eye.x, box.minX, box.maxX);
        double y = MathHelper.clamp(eye.y, box.minY, box.maxY);
        double z = MathHelper.clamp(eye.z, box.minZ, box.maxZ);

        if (Math.abs(x - box.minX) < eps) x = Math.min(box.minX + vec, box.maxX - eps);
        else if (Math.abs(x - box.maxX) < eps) x = Math.max(box.maxX - vec, box.minX + eps);
        if (Math.abs(z - box.minZ) < eps) z = Math.min(box.minZ + vec, box.maxZ - eps);
        else if (Math.abs(z - box.maxZ) < eps) z = Math.max(box.maxZ - vec, box.minZ + eps);

        return new Vec3d(x, y, z);
    }

    private static Vec3d lookVector(float yaw, float pitch) {
        float f = (float) Math.cos(-yaw * 0.017453292F - Math.PI);
        float g = (float) Math.sin(-yaw * 0.017453292F - Math.PI);
        float h = -(float) Math.cos(-pitch * 0.017453292F);
        float i = (float) Math.sin(-pitch * 0.017453292F);
        return new Vec3d(g * h, i, f * h);
    }

    // ------------------------------------------------------------------ diagnostics

    private void flushDiag() {
        if (diagWindowSends > diagPeakWindow) diagPeakWindow = diagWindowSends;
        info("sends=%d peakTick=%d peakSec=%d place=%d/%d break=%d insta=%d",
            diagWindowSends, diagPeakTickSends, diagPeakWindow,
            diagPlaceAttempt, diagPlaceSent, diagBreakSent, diagInstaBreak);
        diagWindowSends = 0;
        diagPeakTickSends = 0;
        diagPlaceAttempt = diagPlaceSent = diagBreakSent = diagInstaBreak = 0;
        diagWindowStart = System.currentTimeMillis();
    }

    private void resetDiag() {
        diagWindowStart = 0L;
        diagWindowSends = 0;
        diagPeakWindow = 0;
        diagLifetimeSends = 0L;
        diagLastTickSends = 0L;
        diagPeakTickSends = 0;
        diagPlaceAttempt = diagPlaceSent = diagBreakSent = diagInstaBreak = 0;
    }

    // ------------------------------------------------------------------ records

    private record TargetCache(LivingEntity entity, Vec3d pos, Box box, float hp, float abs,
                               boolean armorBroken) {}

    private record PlaceTarget(BlockPos base, float damage) {}

    private record BasePlaceTarget(BlockPos base, float damage) {}

    private record PlaceCandidate(BlockPos base, BlockPos airPos, Vec3d crystalCenter, float bound) {}

    /** Millisecond timer, the same shape as Homovore's {@code Timer}. */
    private static final class MsTimer {
        private long last = System.currentTimeMillis();

        void reset() {
            last = System.currentTimeMillis();
        }

        boolean passed(long ms) {
            return System.currentTimeMillis() - last >= ms;
        }
    }

    /** Homovore's {@code MathUtil.calcAngle}: yaw/pitch from one point to another. */
    private static final class Angles {
        static float[] of(Vec3d from, Vec3d to) {
            double difX = to.x - from.x;
            double difY = (to.y - from.y) * -1.0;
            double difZ = to.z - from.z;
            double dist = Math.sqrt(difX * difX + difZ * difZ);
            return new float[]{
                (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0),
                (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difY, dist)))
            };
        }
    }

    public enum RenderMode {
        None,
        Gradient,
        GradientSmooth,
        BoxSmooth,
        Pulse
    }
}
