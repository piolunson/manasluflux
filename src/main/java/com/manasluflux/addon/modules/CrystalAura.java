package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of BlackOut's <b>Auto Crystal+</b> ({@code kassuk.addon.blackout.modules.AutoCrystalPlus},
 * KassuK1/BlackOut, GPL-3.0, author OLEPOSSU) - "Breaks and places crystals automatically (but
 * better)". The 1597-line original is vendored unmodified in
 * {@code blackout/modules/AutoCrystalPlus.java}.
 *
 * <p>What the port keeps, because it is what makes the original different:
 * <ul>
 *   <li>their damage model: enemy / friend / self damage plus health, with the min-max-ratio
 *       checks, force pop, anti friend pop and anti self pop;</li>
 *   <li>their place and explode speed model (place speed, slow place under a damage threshold,
 *       instant place, explode speed, instant attack, sequential ticks);</li>
 *   <li>their explode modes and filters: only own, inhibit, existed time, set dead;</li>
 *   <li>switch modes: Disabled, Simple, Gapple, Silent, InvSilent, PickSilent;</li>
 *   <li>extrapolation of hitboxes and ranges for the damage checks;</li>
 *   <li>a placement box render.</li>
 * </ul>
 *
 * <p>Dropped on purpose, because they need BlackOut's own modules or framework: <b>ID Predict</b>
 * (it rewrites the entity id of the attack packet through a mixin), the <b>Compatibility</b> group
 * (AutoMine / PistonCrystal integration), and the three animated render styles (BlackOut / Future /
 * Earthhack boxes); this port draws one plain box with the same colours.
 *
 * <p>Timers run on the tick (delta 0.05 s) instead of BlackOut's render delta, so speeds are
 * second-based in the same way but deterministic at 20 TPS.
 */
public class CrystalAura extends Module {
    private static final double TICK_DELTA = 0.05;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgExplode = settings.createGroup("Explode");
    private final SettingGroup sgSwitch = settings.createGroup("Switch");
    private final SettingGroup sgDamage = settings.createGroup("Damage");
    private final SettingGroup sgExtrapolation = settings.createGroup("Extrapolation");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General

    private final Setting<Boolean> place = sgGeneral.add(new BoolSetting.Builder()
        .name("place")
        .description("Places crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> explode = sgGeneral.add(new BoolSetting.Builder()
        .name("explode")
        .description("Explodes crystals.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-eat")
        .description("Pauses while eating or drinking.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> performance = sgGeneral.add(new BoolSetting.Builder()
        .name("performance-mode")
        .description("Recalculates placements every other tick instead of every tick.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> ignoreTerrain = sgGeneral.add(new BoolSetting.Builder()
        .name("ignore-terrain")
        .description("Ignores blocks when calculating explosion damage, so terrain cannot soak the hit.")
        .defaultValue(true)
        .build()
    );

    // Place

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range in which crystals are placed.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-walls-range")
        .description("Range in which crystals are placed when a wall is in the way.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> instantPlace = sgPlace.add(new BoolSetting.Builder()
        .name("instant-place")
        .description("Ignores the delay after a crystal has disappeared.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> speedLimit = sgPlace.add(new DoubleSetting.Builder()
        .name("speed-limit")
        .description("Maximum place packets per second. 0 = no limit.")
        .defaultValue(0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(instantPlace::get)
        .build()
    );

    private final Setting<Double> placeSpeed = sgPlace.add(new DoubleSetting.Builder()
        .name("place-speed")
        .description("How many times per second the module places.")
        .defaultValue(10)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<DelayMode> placeDelayMode = sgPlace.add(new EnumSetting.Builder<DelayMode>()
        .name("place-delay-mode")
        .description("Counts the place delay in seconds or in ticks.")
        .defaultValue(DelayMode.Seconds)
        .build()
    );

    private final Setting<Double> placeDelay = sgPlace.add(new DoubleSetting.Builder()
        .name("place-delay")
        .description("Seconds to wait after an attack before placing.")
        .defaultValue(0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(() -> placeDelayMode.get() == DelayMode.Seconds)
        .build()
    );

    private final Setting<Integer> placeDelayTicks = sgPlace.add(new IntSetting.Builder()
        .name("place-delay-ticks")
        .description("Ticks to wait after an attack before placing.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .visible(() -> placeDelayMode.get() == DelayMode.Ticks)
        .build()
    );

    private final Setting<Double> slowDamage = sgPlace.add(new DoubleSetting.Builder()
        .name("slow-damage")
        .description("Switches to the slow speed when the target would take less than this.")
        .defaultValue(3)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> slowSpeed = sgPlace.add(new DoubleSetting.Builder()
        .name("slow-speed")
        .description("Places per second when the damage is under slow-damage.")
        .defaultValue(2)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    // Explode

    private final Setting<Double> breakRange = sgExplode.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Range in which crystals are attacked.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> breakWallsRange = sgExplode.add(new DoubleSetting.Builder()
        .name("break-walls-range")
        .description("Range in which crystals are attacked when a wall is in the way.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> onlyOwn = sgExplode.add(new BoolSetting.Builder()
        .name("only-own")
        .description("Only attacks crystals this module placed.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> inhibit = sgExplode.add(new BoolSetting.Builder()
        .name("inhibit")
        .description("Stops retargeting crystals that were just attacked.")
        .defaultValue(false)
        .build()
    );

    private final Setting<DelayMode> existedMode = sgExplode.add(new EnumSetting.Builder<DelayMode>()
        .name("existed-mode")
        .description("Counts the existed delay in seconds or in ticks.")
        .defaultValue(DelayMode.Seconds)
        .build()
    );

    private final Setting<Double> existed = sgExplode.add(new DoubleSetting.Builder()
        .name("existed")
        .description("Seconds a crystal has to exist before it is attacked.")
        .defaultValue(0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(() -> existedMode.get() == DelayMode.Seconds)
        .build()
    );

    private final Setting<Integer> existedTicks = sgExplode.add(new IntSetting.Builder()
        .name("existed-ticks")
        .description("Ticks a crystal has to exist before it is attacked.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .visible(() -> existedMode.get() == DelayMode.Ticks)
        .build()
    );

    private final Setting<SequentialMode> sequential = sgExplode.add(new EnumSetting.Builder<SequentialMode>()
        .name("sequential")
        .description("Doesn't place and attack during the same ticks.")
        .defaultValue(SequentialMode.Disabled)
        .build()
    );

    private final Setting<Boolean> instantAttack = sgExplode.add(new BoolSetting.Builder()
        .name("instant-attack")
        .description("Skips the delay on the first attack.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> explodeSpeedLimit = sgExplode.add(new DoubleSetting.Builder()
        .name("explode-speed-limit")
        .description("Maximum attacks per second. 0 = no limit.")
        .defaultValue(0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(instantAttack::get)
        .build()
    );

    private final Setting<Double> explodeSpeed = sgExplode.add(new DoubleSetting.Builder()
        .name("explode-speed")
        .description("How many times per second the module attacks.")
        .defaultValue(4)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> setDead = sgExplode.add(new BoolSetting.Builder()
        .name("set-dead")
        .description("Hides a crystal client-side after attacking it.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> setDeadDelay = sgExplode.add(new DoubleSetting.Builder()
        .name("set-dead-delay")
        .description("Seconds between the attack and the crystal disappearing.")
        .defaultValue(0.05)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(setDead::get)
        .build()
    );

    // Switch

    private final Setting<SwitchMode> switchMode = sgSwitch.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .description("How the crystal gets into your hand. Disabled: only a crystal you already hold.")
        .defaultValue(SwitchMode.Disabled)
        .build()
    );

    private final Setting<Double> switchPenalty = sgSwitch.add(new DoubleSetting.Builder()
        .name("switch-penalty")
        .description("Seconds to wait after a slot switch before attacking.")
        .defaultValue(0.25)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    // Damage

    private final Setting<DmgCheckMode> dmgCheckMode = sgDamage.add(new EnumSetting.Builder<DmgCheckMode>()
        .name("dmg-check-mode")
        .description("Normal: best enemy damage wins. Safe: best enemy/self ratio wins.")
        .defaultValue(DmgCheckMode.Normal)
        .build()
    );

    private final Setting<Double> minPlace = sgDamage.add(new DoubleSetting.Builder()
        .name("min-place")
        .description("Minimum damage to the enemy required to place.")
        .defaultValue(4)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> maxPlace = sgDamage.add(new DoubleSetting.Builder()
        .name("max-place")
        .description("Maximum self damage allowed when placing.")
        .defaultValue(8)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> minPlaceRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("min-place-ratio")
        .description("Minimum enemy/self damage ratio when placing.")
        .defaultValue(1.4)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> maxFriendPlace = sgDamage.add(new DoubleSetting.Builder()
        .name("max-friend-place")
        .description("Maximum damage allowed to a friend when placing.")
        .defaultValue(8)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> minFriendPlaceRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("min-friend-place-ratio")
        .description("Minimum enemy/friend damage ratio when placing.")
        .defaultValue(2)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<ExplodeMode> expMode = sgDamage.add(new EnumSetting.Builder<ExplodeMode>()
        .name("explode-damage-mode")
        .description("Which checks are applied before attacking a crystal.")
        .defaultValue(ExplodeMode.FullCheck)
        .build()
    );

    private final Setting<Double> minExplode = sgDamage.add(new DoubleSetting.Builder()
        .name("min-explode")
        .description("Minimum enemy damage required to attack.")
        .defaultValue(2.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> maxExp = sgDamage.add(new DoubleSetting.Builder()
        .name("max-explode")
        .description("Maximum self damage allowed when attacking.")
        .defaultValue(9)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> minExpRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("min-explode-ratio")
        .description("Minimum enemy/self damage ratio when attacking.")
        .defaultValue(1.1)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> maxFriendExp = sgDamage.add(new DoubleSetting.Builder()
        .name("max-friend-explode")
        .description("Maximum damage allowed to a friend when attacking.")
        .defaultValue(12)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> minFriendExpRatio = sgDamage.add(new DoubleSetting.Builder()
        .name("min-friend-explode-ratio")
        .description("Minimum enemy/friend damage ratio when attacking.")
        .defaultValue(2)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> forcePop = sgDamage.add(new DoubleSetting.Builder()
        .name("force-pop")
        .description("Ignores the damage checks when an enemy pops in this many hits. 0 = off.")
        .defaultValue(1)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> antiFriendPop = sgDamage.add(new DoubleSetting.Builder()
        .name("anti-friend-pop")
        .description("Cancels the action when a friend pops in this many hits. 0 = off.")
        .defaultValue(1)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> antiSelfPop = sgDamage.add(new DoubleSetting.Builder()
        .name("anti-self-pop")
        .description("Cancels the action when you pop in this many hits. 0 = off.")
        .defaultValue(1)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    // Extrapolation

    private final Setting<Integer> selfExt = sgExtrapolation.add(new IntSetting.Builder()
        .name("self-extrapolation")
        .description("Ticks of your own movement to predict for the self damage check.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> extrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("extrapolation")
        .description("Ticks of enemy movement to predict for the damage checks.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> rangeExtrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("range-extrapolation")
        .description("Ticks of your own movement to predict for the range checks.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> hitboxExtrapolation = sgExtrapolation.add(new IntSetting.Builder()
        .name("hitbox-extrapolation")
        .description("Ticks of enemy movement to predict for the placement hitbox checks.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Integer> extSmoothness = sgExtrapolation.add(new IntSetting.Builder()
        .name("extrapolation-smoothness")
        .description("How many ticks of movement are averaged for the extrapolation.")
        .defaultValue(2)
        .range(1, 10000)
        .sliderRange(1, 10000)
        .build()
    );

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draws a box on the placement position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("Which parts of the box to draw.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour of the box.")
        .defaultValue(new SettingColor(255, 0, 0, 50))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Line colour of the box.")
        .defaultValue(new SettingColor(255, 0, 0, 255))
        .build()
    );

    // State

    private long ticksEnabled;
    private int placed;
    private double placeTimer;
    private double placeLimitTimer;
    private double delayTimer;
    private int delayTicks;
    private double switchTimer;
    private long lastAttack;
    private double cps;

    private BlockPos placePos;
    private Direction placeDir;
    private Entity expEntity;

    private final Map<Integer, Long> attacked = new HashMap<>();
    private final Map<Integer, Long> inhibited = new HashMap<>();
    private final Map<BlockPos, Long> existedMillis = new HashMap<>();
    private final Map<BlockPos, Long> existedTicksMap = new HashMap<>();
    private final Map<BlockPos, Long> own = new HashMap<>();
    private final List<Box> blocked = new ArrayList<>();
    private final Map<PlayerEntity, Deque<Vec3d>> motion = new HashMap<>();
    private final List<long[]> explosions = new ArrayList<>();
    private final List<long[]> pendingDead = new ArrayList<>();

    public CrystalAura() {
        super(AddonTemplate.COMBAT_CATEGORY, "crystal-aura",
            "Breaks and places crystals automatically (but better) - full port of BlackOut's Auto Crystal+ with its damage model, speed model, switch modes and extrapolation.");
    }

    @Override
    public void onActivate() {
        ticksEnabled = 0;
        placed = 0;
        placeTimer = 0;
        placeLimitTimer = 0;
        delayTimer = 0;
        delayTicks = 0;
        switchTimer = 0;
        lastAttack = 0;
        cps = 0;
        placePos = null;
        placeDir = null;
        expEntity = null;
        attacked.clear();
        inhibited.clear();
        existedMillis.clear();
        existedTicksMap.clear();
        own.clear();
        blocked.clear();
        motion.clear();
        explosions.clear();
        pendingDead.clear();
    }

    @Override
    public String getInfoString() {
        return String.format("%.1f", cps);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        delayTicks++;
        ticksEnabled++;
        placed++;
        placeLimitTimer += TICK_DELTA;
        delayTimer += TICK_DELTA;
        switchTimer = Math.max(0, switchTimer - TICK_DELTA);
        placeTimer = Math.max(0, placeTimer - TICK_DELTA * getSpeed());

        recordMotion();
        pruneTimers();

        // Placement is only recalculated every other tick in performance mode; the action loop
        // still runs every tick so delays and attacks stay responsive.
        if (!performance.get() || ticksEnabled % 2 == 0) updatePlacement();

        if (isPaused()) return;

        update();
        checkDelayed();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || placePos == null || isPaused()) return;

        Box box = new Box(placePos.getX(), placePos.getY(), placePos.getZ(),
            placePos.getX() + 1, placePos.getY() + 1, placePos.getZ() + 1);

        event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    // ---- main loop -------------------------------------------------------------

    private void update() {
        expEntity = null;
        updateCps();

        Hand hand = heldCrystalHand();

        if (place.get() && placePos != null && placeDir != null && speedCheck() && delayCheck()) {
            placeCrystal(placePos.down(), placeDir, hand);
        }

        if (!explode.get()) return;

        double[] bestValue = null;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            if (inhibited.containsKey(crystal.getId())) continue;
            if (switchTimer > 0) continue;
            if (!inBreakRange(crystal.getEntityPos(), crystal.getBlockPos())) continue;
            if (!canExplode(crystal.getEntityPos(), crystal.getBlockPos(), isOwn(crystal.getBlockPos()))) continue;

            double[] dmg = getDmg(crystal.getEntityPos(), crystal.getBlockPos(), true)[0];

            if (expEntity == null || bestValue == null
                || (dmgCheckMode.get() == DmgCheckMode.Normal && dmg[0] > bestValue[0])
                || (dmgCheckMode.get() == DmgCheckMode.Safe && dmg[2] / Math.max(dmg[0], 0.01) < bestValue[2] / Math.max(bestValue[0], 0.01))) {
                expEntity = crystal;
                bestValue = dmg;
            }
        }

        if (expEntity != null && multiTaskCheck() && !attacked.containsKey(expEntity.getId())
            && attackDelayCheck() && existedCheck(expEntity.getBlockPos())) {
            explode(expEntity.getId(), expEntity.getEntityPos());
            return;
        }

    }

    private void explode(int id, Vec3d pos) {
        if (mc.player == null) return;

        lastAttack = System.currentTimeMillis();
        attacked.put(id, lastAttack + (long) (1000 / Math.max(explodeSpeed.get(), 0.01)));
        if (inhibit.get()) inhibited.put(id, lastAttack + 500);

        delayTimer = 0;
        delayTicks = 0;
        explosions.add(new long[] {lastAttack, 0});

        removeExisted(BlockPos.ofFloored(pos));

        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(mc.player, mc.player.isSneaking()));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        blocked.clear();

        if (setDead.get()) {
            long when = System.currentTimeMillis() + (long) (setDeadDelay.get() * 1000);
            pendingDead.add(new long[] {id, when});
        }
    }

    private void placeCrystal(BlockPos base, Direction dir, Hand hand) {
        if (mc.player == null) return;

        boolean swapped = false;
        boolean picked = false;
        int hotbar = mc.player.getInventory().getSelectedSlot();

        Hand useHand = hand;

        if (useHand == null) {
            switch (switchMode.get()) {
                case Simple, Gapple -> {
                    FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
                    if (!crystal.found()) return;
                    InvUtils.swap(crystal.slot(), false);
                    useHand = Hand.MAIN_HAND;
                }
                case Silent -> {
                    FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
                    if (!crystal.found()) return;
                    InvUtils.swap(crystal.slot(), true);
                    swapped = true;
                    useHand = Hand.MAIN_HAND;
                }
                case PickSilent, InvSilent -> {
                    FindItemResult crystal = InvUtils.find(Items.END_CRYSTAL);
                    if (!crystal.found()) return;

                    if (crystal.slot() > 8) {
                        InvUtils.quickSwap().fromHotbar(hotbar).to(crystal.slot());
                        picked = true;
                    } else {
                        InvUtils.swap(crystal.slot(), true);
                        swapped = true;
                    }
                    useHand = Hand.MAIN_HAND;
                }
                case Disabled -> {
                    return;
                }
            }
        }

        blocked.add(new Box(base.getX(), base.getY() + 1, base.getZ(),
            base.getX() + 1, base.getY() + 3, base.getZ() + 1));

        addExisted(base.up());
        own.put(base.up(), System.currentTimeMillis());

        placeLimitTimer = 0;
        placeTimer = 1;
        placed = 0;

        BlockHitResult hit = getPlaceInfo(base);
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(useHand, hit, 0));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(useHand));

        if (swapped) InvUtils.swapBack();
        if (picked) {
            FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (crystal.found()) InvUtils.quickSwap().fromHotbar(hotbar).to(crystal.slot());
        }

        explosions.add(new long[] {System.currentTimeMillis(), 0});
    }

    private void checkDelayed() {
        long now = System.currentTimeMillis();

        pendingDead.removeIf(entry -> {
            if (now < entry[1]) return false;
            if (mc.world != null) mc.world.removeEntity((int) entry[0], Entity.RemovalReason.KILLED);
            return true;
        });
    }

    // ---- checks ----------------------------------------------------------------

    private boolean speedCheck() {
        if (speedLimit.get() > 0 && placeLimitTimer < 1 / speedLimit.get()) return false;
        if (instantPlace.get() && !shouldSlow() && !isBlocked(placePos)) return true;
        return placeTimer <= 0;
    }

    private boolean delayCheck() {
        if (placeDelayMode.get() == DelayMode.Seconds) return delayTimer >= placeDelay.get();
        return delayTicks >= placeDelayTicks.get();
    }

    private boolean multiTaskCheck() {
        return placed >= sequential.get().ticks;
    }

    private boolean attackDelayCheck() {
        if (instantAttack.get()) return explodeSpeedLimit.get() <= 0 || System.currentTimeMillis() > lastAttack + 1000 / explodeSpeedLimit.get();
        return System.currentTimeMillis() > lastAttack + 1000 / Math.max(explodeSpeed.get(), 0.01);
    }

    private boolean isPaused() {
        if (!pauseEat.get()) return false;
        return isEatingOrDrinking();
    }

    private boolean isEatingOrDrinking() {
        if (mc.player == null || !mc.player.isUsingItem()) return false;
        UseAction action = mc.player.getActiveItem().getUseAction();
        return action == UseAction.EAT || action == UseAction.DRINK;
    }

    private boolean isBlocked(BlockPos pos) {
        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
        for (Box bb : blocked) if (bb.intersects(box)) return true;
        return false;
    }

    private double getSpeed() {
        return shouldSlow() ? slowSpeed.get() : placeSpeed.get();
    }

    private boolean shouldSlow() {
        if (placePos == null) return false;
        return getDmg(new Vec3d(placePos.getX() + 0.5, placePos.getY(), placePos.getZ() + 0.5), placePos.down(), false)[0][0] <= slowDamage.get();
    }

    // ---- placement -------------------------------------------------------------

    private void updatePlacement() {
        if (!place.get()) {
            placePos = null;
            placeDir = null;
            return;
        }

        double range = Math.max(placeRange.get(), placeWallsRange.get());
        int r = Math.min((int) Math.ceil(range), 8);
        BlockPos origin = BlockPos.ofFloored(mc.player.getEyePos());

        BlockPos bestPos = null;
        Direction bestDir = null;
        double[] highest = null;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.add(x, y, z);

                    if (!air(pos) || !air(pos.up())) continue;
                    if (!crystalBlock(pos.down())) continue;
                    if (!inPlaceRange(pos.down())) continue;
                    if (!inBreakRange(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), pos)) continue;

                    double[][] result = getDmg(new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), pos.down(), false);
                    if (!placeDamageCheck(result[0], result[1], highest)) continue;

                    Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1);
                    if (EntityUtils.intersectsWithEntity(box, entity -> !entity.isSpectator() && !(entity instanceof EndCrystalEntity))) continue;

                    Direction dir = getPlaceDirection(pos.down());
                    if (dir == null) continue;

                    bestPos = pos;
                    bestDir = dir;
                    highest = result[0];
                }
            }
        }

        placeDir = bestDir;
        placePos = bestPos;
    }

    private boolean placeDamageCheck(double[] dmg, double[] health, double[] highest) {
        // 0 = enemy, 1 = friend, 2 = self
        if (highest != null) {
            if (dmgCheckMode.get() == DmgCheckMode.Normal && dmg[0] < highest[0]) return false;
            if (dmgCheckMode.get() == DmgCheckMode.Safe && dmg[2] / Math.max(dmg[0], 0.01) > highest[2] / Math.max(highest[0], 0.01)) return false;
        }

        double playerHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (antiSelfPop.get() > 0 && dmg[2] * antiSelfPop.get() >= playerHp) return false;
        if (health[1] >= 0 && antiFriendPop.get() > 0 && dmg[1] * antiFriendPop.get() >= health[1]) return false;
        if (health[0] >= 0 && forcePop.get() > 0 && dmg[0] * forcePop.get() >= health[0]) return true;

        if (dmg[0] < minPlace.get()) return false;

        if (dmg[1] > maxFriendPlace.get()) return false;
        if (dmg[1] > 0 && dmg[0] / dmg[1] < minFriendPlaceRatio.get()) return false;
        if (dmg[2] > maxPlace.get()) return false;

        return dmg[2] <= 0 || dmg[0] / dmg[2] >= minPlaceRatio.get();
    }

    private boolean canExplode(Vec3d vec, BlockPos pos, boolean isOwn) {
        if (onlyOwn.get() && !isOwn) return false;
        if (!inBreakRange(vec, pos)) return false;

        double[][] result = getDmg(vec, pos, true);
        return explodeDamageCheck(result[0], result[1], isOwn);
    }

    private boolean explodeDamageCheck(double[] dmg, double[] health, boolean own) {
        ExplodeMode mode = expMode.get();

        boolean checkOwn = mode == ExplodeMode.FullCheck || mode == ExplodeMode.SelfDmgCheck
            || mode == ExplodeMode.SelfDmgOwn || mode == ExplodeMode.AlwaysOwn;
        boolean checkDmg = mode == ExplodeMode.FullCheck
            || (mode == ExplodeMode.SelfDmgOwn && !own)
            || (mode == ExplodeMode.AlwaysOwn && !own);

        double playerHp = mc.player.getHealth() + mc.player.getAbsorptionAmount();

        if (checkOwn) {
            if (antiSelfPop.get() > 0 && dmg[2] * antiSelfPop.get() >= playerHp) return false;
            if (health[1] >= 0 && antiFriendPop.get() > 0 && dmg[1] * antiFriendPop.get() >= health[1]) return false;
        }

        if (checkDmg) {
            if (health[0] >= 0 && forcePop.get() > 0 && dmg[0] * forcePop.get() >= health[0]) return true;
            if (dmg[0] < minExplode.get()) return false;

            if (dmg[1] > 0 && dmg[0] / dmg[1] < minFriendExpRatio.get()) return false;
            if (dmg[2] > 0 && dmg[0] / dmg[2] < minExpRatio.get()) return false;
        }

        if (checkOwn) {
            if (dmg[1] > maxFriendExp.get()) return false;
            return dmg[2] <= maxExp.get();
        }

        return true;
    }

    // ---- damage ----------------------------------------------------------------

    /** @return {@code {{enemy, friend, self}, {enemyHp, friendHp}}} - like BlackOut's getDmg. */
    private double[][] getDmg(Vec3d crystalPos, BlockPos base, boolean attack) {
        double self = crystalDamage(mc.player, crystalPos, base, predictedBox(mc.player, selfExt.get()));

        double highestEnemy = -1;
        double highestFriend = -1;
        double enemyHp = -1;
        double friendHp = -1;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || player.getHealth() <= 0) continue;

            double dmg = crystalDamage(player, crystalPos, base, predictedBox(player, extrapolation.get()));
            double hp = player.getHealth() + player.getAbsorptionAmount();

            if (Friends.get().isFriend(player)) {
                if (dmg > highestFriend) {
                    highestFriend = dmg;
                    friendHp = hp;
                }
            } else if (dmg > highestEnemy) {
                highestEnemy = dmg;
                enemyHp = hp;
            }
        }

        return new double[][] {{highestEnemy, highestFriend, self}, {enemyHp, friendHp}};
    }

    private double crystalDamage(LivingEntity target, Vec3d crystalPos, BlockPos base, Box box) {
        if (target == null) return 0;

        Box hitbox = box == null ? target.getBoundingBox() : box;
        Vec3d center = hitbox.getCenter();

        return DamageUtils.crystalDamage(target, center, hitbox, crystalPos, hitFactory(base));
    }

    /** BlackOut's "ignore terrain": everything but the base the crystal sits on is see-through. */
    private DamageUtils.RaycastFactory hitFactory(BlockPos base) {
        if (!ignoreTerrain.get()) return DamageUtils.HIT_FACTORY;

        return (context, blockPos) -> {
            if (!blockPos.equals(base)) return null;
            BlockState state = Blocks.OBSIDIAN.getDefaultState();
            return state.getCollisionShape(mc.world, blockPos).raycast(context.start(), context.end(), blockPos);
        };
    }

    // ---- extrapolation ---------------------------------------------------------

    private void recordMotion() {
        motion.keySet().removeIf(player -> player.getHealth() <= 0 || player.isRemoved());

        for (PlayerEntity player : mc.world.getPlayers()) {
            Deque<Vec3d> history = motion.computeIfAbsent(player, p -> new ArrayDeque<>());
            history.addLast(player.getEntityPos());
            while (history.size() > Math.max(2, extSmoothness.get() + 1)) history.removeFirst();
        }
    }

    /** Extrapolates a player's box by the average of their last few ticks of movement. */
    private Box predictedBox(PlayerEntity player, int ticks) {
        if (player == null || ticks <= 0) return player == null ? null : player.getBoundingBox();

        Deque<Vec3d> history = motion.get(player);
        if (history == null || history.size() < 2) return player.getBoundingBox();

        Vec3d first = history.peekFirst();
        Vec3d last = history.peekLast();
        if (first == null || last == null) return player.getBoundingBox();

        Vec3d velocity = last.subtract(first).multiply(1.0 / (history.size() - 1));
        return player.getBoundingBox().offset(velocity.multiply(ticks));
    }

    // ---- helpers ---------------------------------------------------------------

    private void pruneTimers() {
        long now = System.currentTimeMillis();

        attacked.entrySet().removeIf(e -> e.getValue() < now);
        inhibited.entrySet().removeIf(e -> e.getValue() < now);

        existedMillis.entrySet().removeIf(e -> now - e.getValue() >= 5000 + (long) (existed.get() * 1000));
        existedTicksMap.entrySet().removeIf(e -> ticksEnabled - e.getValue() >= 100 + existedTicks.get());
        own.entrySet().removeIf(e -> now - e.getValue() >= 5000);
    }

    private void updateCps() {
        long now = System.currentTimeMillis();

        explosions.removeIf(entry -> now - entry[0] >= 5000);

        double total = 0;
        for (long[] entry : explosions) {
            double age = (now - entry[0]) / 1000.0;
            total += age <= 4 ? 1 : Math.max(0, 1 - (age - 4));
        }
        cps = total / 4.5;
    }

    private boolean existedCheck(BlockPos pos) {
        if (existedMode.get() == DelayMode.Seconds) {
            Long time = existedMillis.get(pos);
            return time == null || System.currentTimeMillis() > time + (long) (existed.get() * 1000);
        }

        Long ticks = existedTicksMap.get(pos);
        return ticks == null || ticksEnabled >= ticks + existedTicks.get();
    }

    private void addExisted(BlockPos pos) {
        if (existedMode.get() == DelayMode.Seconds) existedMillis.putIfAbsent(pos, System.currentTimeMillis());
        else existedTicksMap.putIfAbsent(pos, ticksEnabled);
    }

    private void removeExisted(BlockPos pos) {
        existedMillis.remove(pos);
        existedTicksMap.remove(pos);
    }

    private boolean isOwn(BlockPos pos) {
        return own.containsKey(pos);
    }

    private boolean air(BlockPos pos) {
        return mc.world.getBlockState(pos).isAir();
    }

    private boolean crystalBlock(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        return state.isOf(Blocks.OBSIDIAN) || state.isOf(Blocks.BEDROCK);
    }

    private boolean inPlaceRange(BlockPos pos) {
        return PlayerUtils.isWithin(pos.toCenterPos(), isBehindWall(pos.toCenterPos(), pos) ? placeWallsRange.get() : placeRange.get());
    }

    private boolean inBreakRange(Vec3d pos, BlockPos expected) {
        return PlayerUtils.isWithin(pos, isBehindWall(pos, expected) ? breakWallsRange.get() : breakRange.get());
    }

    private boolean isBehindWall(Vec3d pos, BlockPos expected) {
        BlockHitResult result = mc.world.raycast(new RaycastContext(
            mc.player.getEyePos(), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

        return result.getType() == HitResult.Type.BLOCK && !result.getBlockPos().equals(expected);
    }

    private Hand heldCrystalHand() {
        if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == Items.END_CRYSTAL) return Hand.OFF_HAND;
        return null;
    }

    /** Which face of the base to click: a visible face if there is one, otherwise the closest. */
    private BlockHitResult getPlaceInfo(BlockPos base) {
        Vec3d eye = mc.player.getEyePos();

        for (Direction side : Direction.values()) {
            Vec3d faceCenter = new Vec3d(
                base.getX() + 0.5 + side.getOffsetX() * 0.5,
                base.getY() + 0.5 + side.getOffsetY() * 0.5,
                base.getZ() + 0.5 + side.getOffsetZ() * 0.5);

            BlockHitResult result = mc.world.raycast(new RaycastContext(eye, faceCenter,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));

            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(base)) return result;
        }

        Direction side = base.getY() > eye.y ? Direction.DOWN : Direction.UP;
        Vec3d hitVec = new Vec3d(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5)
            .add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

        return new BlockHitResult(hitVec, side, base, false);
    }

    private Direction getPlaceDirection(BlockPos base) {
        return getPlaceInfo(base).getSide();
    }

    // ---- enums ----------------------------------------------------------------

    public enum DmgCheckMode {
        Normal,
        Safe
    }

    public enum SwitchMode {
        Disabled,
        Simple,
        Gapple,
        Silent,
        InvSilent,
        PickSilent
    }

    public enum SequentialMode {
        Disabled(0),
        Weak(1),
        Strong(2),
        Strict(3);

        public final int ticks;

        SequentialMode(int ticks) {
            this.ticks = ticks;
        }
    }

    public enum ExplodeMode {
        FullCheck,
        SelfDmgCheck,
        SelfDmgOwn,
        AlwaysOwn,
        Always
    }

    public enum DelayMode {
        Seconds,
        Ticks
    }
}
