package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.util.MsScheduler;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * AC PacketFlood - a crystal aura that lives on the packet timeline instead of the game tick.
 *
 * Two things make it fast:
 * <ul>
 *     <li>every place and break is a raw packet (use-block / attack / silent look) built by the
 *     module, so nothing waits for the interaction manager;</li>
 *     <li>packets are not sent once per tick - a 1 ms loop on {@link MsScheduler} keeps firing the
 *     current place/break plan at your configured delay until the server confirms it, so
 *     <code>place-delay-ms 1</code> really is about a thousand packets per second (bounded only by
 *     <code>max-packets-per-second</code>).</li>
 * </ul>
 * The plans themselves (which base, which crystal, damage numbers) are rebuilt on the game thread
 * every tick, because that part needs the world; the loop only sends prebuilt packets.
 */
public class AcPacketFlood extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgPacket = settings.createGroup("Packet");

    // General

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range in which to target players.")
        .defaultValue(10)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> predictMovement = sgGeneral.add(new BoolSetting.Builder()
        .name("predict-movement")
        .description("Predicts the target's movement in the damage calculation.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> minDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("Minimum damage a crystal needs to deal to the target.")
        .defaultValue(6)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximum damage a crystal may deal to you.")
        .defaultValue(6)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Never places or breaks a crystal that could kill you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Sends a silent look packet at the crystal before every place/break.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> facePlace = sgGeneral.add(new BoolSetting.Builder()
        .name("face-place")
        .description("Relaxes min-damage to 1.5 when the target is weak or its armor is nearly gone.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> facePlaceHealth = sgGeneral.add(new DoubleSetting.Builder()
        .name("face-place-health")
        .description("Health the target has to be at to start face placing.")
        .defaultValue(8)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(facePlace::get)
        .build()
    );

    private final Setting<Double> facePlaceDurability = sgGeneral.add(new DoubleSetting.Builder()
        .name("face-place-durability")
        .description("Armor durability percentage the target has to be below to start face placing.")
        .defaultValue(2)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .visible(facePlace::get)
        .build()
    );

    private final Setting<Boolean> facePlaceArmor = sgGeneral.add(new BoolSetting.Builder()
        .name("face-place-missing-armor")
        .description("Face place when the target is missing a piece of armor.")
        .defaultValue(false)
        .visible(facePlace::get)
        .build()
    );

    // Place

    private final Setting<Boolean> doPlace = sgPlace.add(new BoolSetting.Builder()
        .name("place")
        .description("Places crystals on the most damaging bases.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeDelayMs = sgPlace.add(new DoubleSetting.Builder()
        .name("place-delay-ms")
        .description("Milliseconds between two place packets (1 = about a thousand per second, 0 = every loop step).")
        .defaultValue(1)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Integer> placeRetries = sgPlace.add(new IntSetting.Builder()
        .name("place-retries")
        .description("Extra place packets sent after place-retry-delay-ms while the base still has no crystal.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Double> placeRetryDelayMs = sgPlace.add(new DoubleSetting.Builder()
        .name("place-retry-delay-ms")
        .description("Milliseconds before the first retry of a place packet.")
        .defaultValue(2)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Integer> basesPerTick = sgPlace.add(new IntSetting.Builder()
        .name("bases-per-tick")
        .description("How many bases are kept as active place plans (the flood loop cycles through them).")
        .defaultValue(1)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range in which to place crystals.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-walls-range")
        .description("Range in which to place crystals through walls.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support-blocks")
        .description("Places obsidian in the air when no base was found.")
        .defaultValue(false)
        .build()
    );

    // Break

    private final Setting<Boolean> doBreak = sgBreak.add(new BoolSetting.Builder()
        .name("break")
        .description("Breaks crystals that damage the target.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> breakDelayMs = sgBreak.add(new DoubleSetting.Builder()
        .name("break-delay-ms")
        .description("Milliseconds between two break packets (1 = about a thousand per second, 0 = every loop step).")
        .defaultValue(1)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Integer> breakRetries = sgBreak.add(new IntSetting.Builder()
        .name("break-retries")
        .description("Extra attack packets sent after break-retry-delay-ms while the crystal still exists.")
        .defaultValue(0)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Double> breakRetryDelayMs = sgBreak.add(new DoubleSetting.Builder()
        .name("break-retry-delay-ms")
        .description("Milliseconds before the first retry of an attack packet.")
        .defaultValue(2)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Range in which to break crystals.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    private final Setting<Double> breakWallsRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-walls-range")
        .description("Range in which to break crystals through walls.")
        .defaultValue(4.5)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 10000.0)
        .build()
    );

    // Packet

    private final Setting<Integer> maxPacketsPerSecond = sgPacket.add(new IntSetting.Builder()
        .name("max-packets-per-second")
        .description("Hard cap on everything this module sends in one second.")
        .defaultValue(60)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> debugMode = sgPacket.add(new BoolSetting.Builder()
        .name("debug-mode")
        .description("Prints the packets sent per second in chat.")
        .defaultValue(false)
        .build()
    );

    private record PlacePlan(BlockPos base, List<Packet<?>> batch) {}

    private final Map<Long, Long> confirmedBases = new ConcurrentHashMap<>();
    private final Map<Integer, Long> destroyedIds = new ConcurrentHashMap<>();
    private final AtomicInteger windowCount = new AtomicInteger();
    private final AtomicInteger placeCursor = new AtomicInteger();

    private volatile List<PlacePlan> placePlans = List.of();
    private volatile List<Packet<?>> breakPlan = List.of();
    private volatile int breakPlanId = -1;

    private MsScheduler scheduler;
    private volatile long windowStart;
    private volatile long nextPlaceAt;
    private volatile long nextBreakAt;
    private int sequence;
    private int debugTicks;

    public AcPacketFlood() {
        super(AddonTemplate.COMBAT_CATEGORY, "ac-pcktflood",
            "Crystal aura on the packet timeline: raw place/break packets fired by a millisecond loop with a packet-per-second cap.");
    }

    @Override
    public void onActivate() {
        scheduler = new MsScheduler("manasluflux-ac-pcktflood");
        confirmedBases.clear();
        destroyedIds.clear();
        placePlans = List.of();
        breakPlan = List.of();
        breakPlanId = -1;
        windowCount.set(0);
        windowStart = System.currentTimeMillis();
        nextPlaceAt = 0;
        nextBreakAt = 0;
        sequence = 0;
        debugTicks = 0;

        // The flood loop: independent of the game tick, keeps sending the current plans.
        scheduler.scheduleRepeating(1, this::flood);
    }

    @Override
    public void onDeactivate() {
        if (scheduler != null) {
            scheduler.close();
            scheduler = null;
        }

        placePlans = List.of();
        breakPlan = List.of();
        breakPlanId = -1;
        confirmedBases.clear();
        destroyedIds.clear();
    }

    /**
     * Server confirmations. Retries and the flood loop read these without touching the world.
     */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (!isActive()) return;

        long now = System.currentTimeMillis();

        if (event.packet instanceof EntitySpawnS2CPacket spawn) {
            if (spawn.getEntityType() != EntityType.END_CRYSTAL) return;
            if (confirmedBases.size() > 512) confirmedBases.clear();
            confirmedBases.put(BlockPos.ofFloored(spawn.getX(), spawn.getY() - 1, spawn.getZ()).asLong(), now);
        } else if (event.packet instanceof EntitiesDestroyS2CPacket destroy) {
            if (destroyedIds.size() > 512) destroyedIds.clear();
            for (int id : destroy.getEntityIds()) destroyedIds.put(id, now);
        }
    }

    /**
     * Game thread: rebuild the plans (this is the only part that needs the world).
     */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (debugMode.get() && ++debugTicks >= 20) {
            debugTicks = 0;
            info("packets/s: %d", sentLastSecond());
        }

        PlayerEntity target = CombatTargets.findTarget(targetRange.get());
        if (target == null) {
            placePlans = List.of();
            breakPlan = List.of();
            breakPlanId = -1;
            return;
        }

        if (doBreak.get()) planBreak(target);
        else {
            breakPlan = List.of();
            breakPlanId = -1;
        }

        if (doPlace.get()) planPlace(target);
        else placePlans = List.of();
    }

    // Flood loop (millisecond thread - only sends prebuilt packets)

    private static final int MAX_BURST_PER_STEP = 512;

    private void flood() {
        if (!isActive()) return;

        long now = System.currentTimeMillis();

        if (now >= nextBreakAt) {
            long delay = breakDelayMs.get().longValue();
            nextBreakAt = delay <= 0 ? now : now + delay;

            // delay 0 means "as fast as the packet cap allows": burst until the cap says stop.
            if (delay <= 0) {
                for (int i = 0; i < MAX_BURST_PER_STEP && sendBreak(); i++);
            } else {
                sendBreak();
            }
        }

        if (now >= nextPlaceAt) {
            long delay = placeDelayMs.get().longValue();
            nextPlaceAt = delay <= 0 ? now : now + delay;

            if (delay <= 0) {
                for (int i = 0; i < MAX_BURST_PER_STEP && sendPlace(); i++);
            } else {
                sendPlace();
            }
        }
    }

    /**
     * @return true when a break batch was actually sent (used by the delay-0 burst loop).
     */
    private boolean sendBreak() {
        List<Packet<?>> batch = breakPlan;
        if (batch.isEmpty()) return false;

        int id = breakPlanId;
        if (id != -1 && isDestroyed(id)) return false;

        if (!sendBatch(batch)) return false;

        scheduleRetries(batch, breakRetries.get(), breakRetryDelayMs.get(), () -> id != -1 && isDestroyed(id));
        return true;
    }

    /**
     * @return true when a place batch was actually sent (used by the delay-0 burst loop).
     */
    private boolean sendPlace() {
        List<PlacePlan> plans = placePlans;
        if (plans.isEmpty()) return false;

        PlacePlan plan = plans.get(Math.floorMod(placeCursor.getAndIncrement(), plans.size()));
        if (isBaseConfirmed(plan.base())) return false;

        if (!sendBatch(plan.batch())) return false;

        scheduleRetries(plan.batch(), placeRetries.get(), placeRetryDelayMs.get(), () -> isBaseConfirmed(plan.base()));
        return true;
    }

    // Planning (game thread)

    private void planBreak(PlayerEntity target) {
        EndCrystalEntity best = null;
        float bestDamage = 0;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;

            float damage = breakDamage(crystal, target);
            if (damage > bestDamage) {
                bestDamage = damage;
                best = crystal;
            }
        }

        if (best == null) {
            breakPlan = List.of();
            breakPlanId = -1;
            return;
        }

        breakPlan = buildAttack(best);
        breakPlanId = best.getId();
    }

    private void planPlace(PlayerEntity target) {
        FindItemResult crystals = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystals.found()) {
            placePlans = List.of();
            return;
        }

        int limit = Math.min(basesPerTick.get(), 64);
        if (limit <= 0) {
            placePlans = List.of();
            return;
        }

        List<BaseCandidate> bases = findBases(target, limit);

        if (bases.isEmpty()) {
            placePlans = List.of();
            if (support.get()) placeSupport(target);
            return;
        }

        List<PlacePlan> plans = new ArrayList<>(bases.size());
        for (BaseCandidate candidate : bases) {
            List<Packet<?>> batch = buildPlace(candidate.pos());
            if (!batch.isEmpty()) plans.add(new PlacePlan(candidate.pos(), batch));
        }

        placePlans = plans;
    }

    private List<BaseCandidate> findBases(PlayerEntity target, int limit) {
        List<BaseCandidate> found = new ArrayList<>();

        // The setting can be dialled up to 10000; a bigger scan cube than this would hang the tick.
        int r = Math.min((int) Math.ceil(placeRange.get()), 8);
        BlockPos origin = mc.player.getBlockPos();

        for (BlockPos bp : BlockPos.iterate(origin.add(-r, -r, -r), origin.add(r, r, r))) {
            BlockState state = mc.world.getBlockState(bp);
            if (!state.isOf(Blocks.OBSIDIAN) && !state.isOf(Blocks.BEDROCK)) continue;

            BlockPos above = bp.up();
            if (!mc.world.getBlockState(above).isAir()) continue;
            if (hasCrystalAt(above)) continue;

            Vec3d crystalPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5);
            if (!inRange(crystalPos, above, placeRange.get(), placeWallsRange.get())) continue;

            float selfDamage = DamageUtils.crystalDamage(mc.player, crystalPos, predictMovement.get(), bp.toImmutable());
            if (selfDamage > maxSelfDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) continue;

            float damage = DamageUtils.crystalDamage(target, crystalPos, predictMovement.get(), bp.toImmutable());
            double minimum = shouldFacePlace(target) ? Math.min(minDamage.get(), 1.5) : minDamage.get();
            if (damage < minimum) continue;

            Box crystalBox = new Box(bp.getX(), bp.getY() + 1, bp.getZ(), bp.getX() + 1, bp.getY() + 3, bp.getZ() + 1);
            if (EntityUtils.intersectsWithEntity(crystalBox, entity -> !entity.isSpectator())) continue;

            found.add(new BaseCandidate(bp.toImmutable(), damage));
        }

        found.sort(Comparator.comparingDouble((BaseCandidate candidate) -> candidate.damage).reversed());

        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    // Packet building (game thread - they read the player and the world once)

    private List<Packet<?>> buildAttack(EndCrystalEntity crystal) {
        List<Packet<?>> batch = new ArrayList<>(3);

        if (rotate.get()) {
            Vec3d look = lookAngles(crystal.getEntityPos());
            batch.add(new PlayerMoveC2SPacket.LookAndOnGround((float) look.x, (float) look.y, mc.player.isOnGround(), mc.player.isOnGround()));
        }

        batch.add(PlayerInteractEntityC2SPacket.attack(crystal, false));
        batch.add(new HandSwingC2SPacket(Hand.MAIN_HAND));

        return batch;
    }

    private List<Packet<?>> buildPlace(BlockPos base) {
        FindItemResult crystals = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystals.found()) return List.of();

        BlockHitResult hit = placeHit(base);
        Hand hand = Hand.MAIN_HAND;
        int realSlot = -1;

        List<Packet<?>> batch = new ArrayList<>(5);

        if (crystals.isOffhand()) {
            hand = Hand.OFF_HAND;
        } else if (crystals.isHotbar() && crystals.slot() != mc.player.getInventory().getSelectedSlot()) {
            realSlot = mc.player.getInventory().getSelectedSlot();
            batch.add(new UpdateSelectedSlotC2SPacket(crystals.slot()));
        }

        if (rotate.get()) {
            Vec3d look = lookAngles(hit.getPos());
            batch.add(new PlayerMoveC2SPacket.LookAndOnGround((float) look.x, (float) look.y, mc.player.isOnGround(), mc.player.isOnGround()));
        }

        batch.add(new PlayerInteractBlockC2SPacket(hand, hit, sequence++));
        batch.add(new HandSwingC2SPacket(hand));

        if (realSlot != -1) batch.add(new UpdateSelectedSlotC2SPacket(realSlot));

        return batch;
    }

    private void placeSupport(PlayerEntity target) {
        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        int r = Math.min((int) Math.ceil(placeRange.get()), 8);
        BlockPos origin = mc.player.getBlockPos();

        BlockPos best = null;
        double bestDamage = 0;

        for (BlockPos bp : BlockPos.iterate(origin.add(-r, -r, -r), origin.add(r, r, r))) {
            BlockState state = mc.world.getBlockState(bp);
            if (!state.isReplaceable() || !mc.world.getBlockState(bp.up()).isAir()) continue;

            Vec3d crystalPos = new Vec3d(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5);
            if (!inRange(crystalPos, bp.up(), placeRange.get(), placeWallsRange.get())) continue;

            float selfDamage = DamageUtils.crystalDamage(mc.player, crystalPos, predictMovement.get(), bp.toImmutable());
            if (selfDamage > maxSelfDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) continue;

            float damage = DamageUtils.crystalDamage(target, crystalPos, predictMovement.get(), bp.toImmutable());
            if (damage < minDamage.get()) continue;

            Box crystalBox = new Box(bp.getX(), bp.getY() + 1, bp.getZ(), bp.getX() + 1, bp.getY() + 3, bp.getZ() + 1);
            if (EntityUtils.intersectsWithEntity(crystalBox, entity -> !entity.isSpectator())) continue;

            if (damage > bestDamage) {
                bestDamage = damage;
                best = bp.toImmutable();
            }
        }

        if (best == null) return;

        BlockHitResult hit = supportHit(best);
        if (hit == null) return;

        Hand hand = Hand.MAIN_HAND;
        int realSlot = -1;
        List<Packet<?>> batch = new ArrayList<>(5);

        if (obsidian.isOffhand()) {
            hand = Hand.OFF_HAND;
        } else if (obsidian.isHotbar() && obsidian.slot() != mc.player.getInventory().getSelectedSlot()) {
            realSlot = mc.player.getInventory().getSelectedSlot();
            batch.add(new UpdateSelectedSlotC2SPacket(obsidian.slot()));
        }

        if (rotate.get()) {
            Vec3d look = lookAngles(hit.getPos());
            batch.add(new PlayerMoveC2SPacket.LookAndOnGround((float) look.x, (float) look.y, mc.player.isOnGround(), mc.player.isOnGround()));
        }

        batch.add(new PlayerInteractBlockC2SPacket(hand, hit, sequence++));
        batch.add(new HandSwingC2SPacket(hand));

        if (realSlot != -1) batch.add(new UpdateSelectedSlotC2SPacket(realSlot));

        sendBatch(batch);
    }

    private float breakDamage(EndCrystalEntity crystal, PlayerEntity target) {
        BlockPos base = crystal.getBlockPos().down();

        if (!inRange(crystal.getEntityPos(), crystal.getBlockPos(), breakRange.get(), breakWallsRange.get())) return 0;

        float selfDamage = DamageUtils.crystalDamage(mc.player, crystal.getEntityPos(), predictMovement.get(), base);
        if (selfDamage > maxSelfDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) return 0;

        float damage = DamageUtils.crystalDamage(target, crystal.getEntityPos(), predictMovement.get(), base);
        double minimum = shouldFacePlace(target) ? Math.min(minDamage.get(), 1.5) : minDamage.get();
        return damage < minimum ? 0 : damage;
    }

    // Sending

    private void scheduleRetries(List<Packet<?>> batch, int retries, double retryDelayMs, BooleanSupplier done) {
        if (retries <= 0) return;
        scheduleRetry(batch, retries - 1, Math.max(1, (long) retryDelayMs), done);
    }

    private void scheduleRetry(List<Packet<?>> batch, int retriesLeft, long delay, BooleanSupplier done) {
        scheduler.schedule(delay, () -> {
            if (!isActive() || done.getAsBoolean()) return;

            sendBatch(batch);

            if (retriesLeft > 0) scheduleRetry(batch, retriesLeft - 1, delay, done);
        });
    }

    /**
     * Sends a whole batch or nothing: a batch cut in half (look packet without the attack) would
     * be useless, so it is skipped for this loop step instead.
     */
    private boolean sendBatch(List<Packet<?>> batch) {
        if (batch.isEmpty() || !isActive()) return false;

        var handler = mc.getNetworkHandler();
        if (handler == null) return false;
        if (!allowBatch(batch.size())) return false;

        for (Packet<?> packet : batch) handler.sendPacket(packet);
        return true;
    }

    private boolean allowBatch(int size) {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            windowStart = now;
            windowCount.set(0);
        }

        if (windowCount.get() + size > maxPacketsPerSecond.get()) return false;

        windowCount.addAndGet(size);
        return true;
    }

    private int sentLastSecond() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= 1000) {
            windowStart = now;
            windowCount.set(0);
        }

        return windowCount.get();
    }

    // Helpers

    private boolean isBaseConfirmed(BlockPos base) {
        Long at = confirmedBases.get(base.asLong());
        return at != null && System.currentTimeMillis() - at < 1000;
    }

    private boolean isDestroyed(int entityId) {
        Long at = destroyedIds.get(entityId);
        return at != null && System.currentTimeMillis() - at < 1000;
    }

    private boolean hasCrystalAt(BlockPos above) {
        return !mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(above), e -> e.getBlockPos().equals(above)).isEmpty();
    }

    private BlockHitResult placeHit(BlockPos base) {
        Vec3d eye = mc.player.getEyePos();

        for (Direction side : Direction.values()) {
            Vec3d faceCenter = new Vec3d(
                base.getX() + 0.5 + side.getOffsetX() * 0.5,
                base.getY() + 0.5 + side.getOffsetY() * 0.5,
                base.getZ() + 0.5 + side.getOffsetZ() * 0.5
            );

            BlockHitResult result = mc.world.raycast(new RaycastContext(eye, faceCenter, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(base)) return result;
        }

        Direction side = base.getY() > eye.y ? Direction.DOWN : Direction.UP;
        Vec3d hitVec = new Vec3d(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5)
            .add(side.getOffsetX() * 0.5, side.getOffsetY() * 0.5, side.getOffsetZ() * 0.5);

        return new BlockHitResult(hitVec, side, base, false);
    }

    private BlockHitResult supportHit(BlockPos supportPos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = supportPos.offset(dir);
            BlockState state = mc.world.getBlockState(neighbor);
            if (state.isAir() || state.isReplaceable()) continue;

            Direction face = dir.getOpposite();
            Vec3d hitVec = Vec3d.of(neighbor).add(0.5, 0.5, 0.5)
                .add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);

            return new BlockHitResult(hitVec, face, neighbor, false);
        }

        return null;
    }

    private boolean inRange(Vec3d pos, BlockPos expectedClipPos, double range, double wallsRange) {
        BlockHitResult result = mc.world.raycast(new RaycastContext(mc.player.getEyePos(), pos, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        boolean behindWall = result.getType() == HitResult.Type.BLOCK && !result.getBlockPos().equals(expectedClipPos);

        return PlayerUtils.isWithin(pos, behindWall ? wallsRange : range);
    }

    private boolean shouldFacePlace(PlayerEntity target) {
        if (!facePlace.get()) return false;

        if (EntityUtils.getTotalHealth(target) <= facePlaceHealth.get()) return true;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue;

            ItemStack stack = target.getEquippedStack(slot);
            if (stack.isEmpty()) {
                if (facePlaceArmor.get()) return true;
            } else if ((double) (stack.getMaxDamage() - stack.getDamage()) / stack.getMaxDamage() * 100 <= facePlaceDurability.get()) {
                return true;
            }
        }

        return false;
    }

    /**
     * @return (yaw, pitch) pointing at the position.
     */
    private Vec3d lookAngles(Vec3d pos) {
        Vec3d delta = pos.subtract(mc.player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) MathHelper.wrapDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -MathHelper.wrapDegrees(Math.atan2(delta.y, horizontal));

        return new Vec3d(yaw, pitch, 0);
    }

    private record BaseCandidate(BlockPos pos, float damage) {}
}
