package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Port of Meteor Client's CrystalAura (skid), adapted to ManasluFlux's silent packet style.
 *
 * Instead of blindly placing on any base next to the target, it simulates the crystal
 * explosion for every obsidian/bedrock base in range (Meteor's DamageUtils.crystalDamage)
 * and only places/pops when the explosion deals enough damage to the target while staying
 * under your max self damage. Supports face placing (low health / bad armor), optional
 * obsidian support blocks in air, and separate place/break ranges, walls ranges and delays.
 * Everything is silent: server-side hotbar swaps and server-side-only look packets - your
 * camera, hand and hotbar never move.
 */
public class CrystalAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Place");
    private final SettingGroup sgFacePlace = settings.createGroup("Face Place");
    private final SettingGroup sgBreak = settings.createGroup("Break");
    private final SettingGroup sgPause = settings.createGroup("Pause");

    // General

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("Range in which to target players.")
        .defaultValue(10)
        .range(0, 16)
        .sliderRange(0, 16)
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
        .description("Minimum damage the crystal needs to deal to the target.")
        .defaultValue(6)
        .range(0, 36)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgGeneral.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("Maximum damage crystals can deal to yourself.")
        .defaultValue(6)
        .range(0, 36)
        .sliderRange(0, 36)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgGeneral.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Will not place or break crystals that could kill you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Rotates server-side (silent) towards the crystals being placed/broken - your camera never moves.")
        .defaultValue(true)
        .build()
    );

    // Place

    private final Setting<Boolean> doPlace = sgPlace.add(new BoolSetting.Builder()
        .name("place")
        .description("Places crystals on the best base for maximum damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> placeDelay = sgPlace.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks to wait between placing crystals.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> placeRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("Range in which to place crystals.")
        .defaultValue(4.5)
        .range(0, 6)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> placeWallsRange = sgPlace.add(new DoubleSetting.Builder()
        .name("place-walls-range")
        .description("Range in which to place crystals when behind blocks.")
        .defaultValue(4.5)
        .range(0, 6)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Boolean> support = sgPlace.add(new BoolSetting.Builder()
        .name("support-blocks")
        .description("Places obsidian in the air when no base position was found.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> supportDelay = sgPlace.add(new IntSetting.Builder()
        .name("support-delay")
        .description("Ticks to wait after placing a support block before placing the crystal on it.")
        .defaultValue(1)
        .range(0, 10)
        .sliderRange(0, 10)
        .visible(support::get)
        .build()
    );

    // Face place

    private final Setting<Boolean> facePlace = sgFacePlace.add(new BoolSetting.Builder()
        .name("face-place")
        .description("Relaxes the min-damage requirement (to 1.5) when the target is weak.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> facePlaceHealth = sgFacePlace.add(new DoubleSetting.Builder()
        .name("face-place-health")
        .description("The health the target has to be at to start face placing.")
        .defaultValue(8)
        .range(1, 36)
        .sliderRange(1, 36)
        .visible(facePlace::get)
        .build()
    );

    private final Setting<Double> facePlaceDurability = sgFacePlace.add(new DoubleSetting.Builder()
        .name("face-place-durability")
        .description("The armor durability percentage the target has to be below to start face placing.")
        .defaultValue(2)
        .range(1, 100)
        .sliderRange(1, 100)
        .visible(facePlace::get)
        .build()
    );

    private final Setting<Boolean> facePlaceArmor = sgFacePlace.add(new BoolSetting.Builder()
        .name("face-place-missing-armor")
        .description("Automatically starts face placing when the target misses a piece of armor.")
        .defaultValue(false)
        .visible(facePlace::get)
        .build()
    );

    // Break

    private final Setting<Boolean> doBreak = sgBreak.add(new BoolSetting.Builder()
        .name("break")
        .description("Breaks the crystal that deals the most damage.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> breakDelay = sgBreak.add(new IntSetting.Builder()
        .name("break-delay")
        .description("Ticks to wait between breaking crystals.")
        .defaultValue(0)
        .range(0, 20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Double> breakRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-range")
        .description("Range in which to break crystals.")
        .defaultValue(4.5)
        .range(0, 6)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Double> breakWallsRange = sgBreak.add(new DoubleSetting.Builder()
        .name("break-walls-range")
        .description("Range in which to break crystals when behind blocks.")
        .defaultValue(4.5)
        .range(0, 6)
        .sliderRange(0, 6)
        .build()
    );

    private final Setting<Integer> attackFrequency = sgBreak.add(new IntSetting.Builder()
        .name("attack-frequency")
        .description("Maximum hits to do per second.")
        .defaultValue(25)
        .range(1, 30)
        .sliderRange(1, 30)
        .build()
    );

    // Pause

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Pauses while you are mining a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Pauses while you are eating or drinking.")
        .defaultValue(true)
        .build()
    );

    private int breakTimer;
    private int placeTimer;
    private int ticksPassed;
    private int attacks;

    public CrystalAura() {
        super(AddonTemplate.COMBAT_CATEGORY, "crystal-aura", "Places and breaks end crystals at the most damaging positions - damage simulation, face place and support blocks, all silent (no visible rotations or hotbar movement).");
    }

    @Override
    public void onActivate() {
        breakTimer = 0;
        placeTimer = 0;
        ticksPassed = 0;
        attacks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (pauseOnMine.get() && mc.gameMode.isDestroying()) return;
        if (pauseOnEat.get() && isEatingOrDrinking()) return;

        // Attack frequency counter (resets every second)
        if (ticksPassed < 20) ticksPassed++;
        else {
            ticksPassed = 0;
            attacks = 0;
        }

        if (breakTimer > 0) breakTimer--;
        if (placeTimer > 0) placeTimer--;

        Player target = CombatTargets.findTarget(targetRange.get());
        if (target == null) return;

        if (doBreak.get()) doBreak(target);
        if (doPlace.get()) doPlace(target);
    }

    // Break

    private void doBreak(Player target) {
        if (breakTimer > 0 || attacks >= attackFrequency.get()) return;

        EndCrystal best = null;
        float bestDamage = 0;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;

            float damage = getBreakDamage(crystal, target);
            if (damage > bestDamage) {
                bestDamage = damage;
                best = crystal;
            }
        }

        if (best == null) return;

        if (rotate.get()) lookAt(best.position());

        mc.player.connection.send(new ServerboundAttackPacket(best.getId()));
        mc.player.connection.send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

        attacks++;
        breakTimer = breakDelay.get();
    }

    /**
     * Returns the damage breaking this crystal deals to the target, or 0 when the crystal
     * is out of range, would deal too much self damage or not enough damage to the target.
     */
    private float getBreakDamage(EndCrystal crystal, Player target) {
        BlockPos base = crystal.blockPosition().below();

        if (!inRange(crystal.position(), crystal.blockPosition(), breakRange.get(), breakWallsRange.get())) return 0;

        float selfDamage = DamageUtils.crystalDamage(mc.player, crystal.position(), predictMovement.get(), base);
        if (selfDamage > maxSelfDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) return 0;

        float damage = DamageUtils.crystalDamage(target, crystal.position(), predictMovement.get(), base);
        double minimumDamage = shouldFacePlace(target) ? Math.min(minDamage.get(), 1.5) : minDamage.get();
        if (damage < minimumDamage) return 0;

        return damage;
    }

    // Place

    private void doPlace(Player target) {
        if (placeTimer > 0) return;

        FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystal.found()) return;

        // Don't multiplace: if a crystal is already placed that we could break, break it first.
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal existing && getBreakDamage(existing, target) > 0) return;
        }

        int r = (int) Math.ceil(placeRange.get());
        BlockPos origin = mc.player.blockPosition();

        BlockPos bestBase = null;
        double bestBaseDamage = 0;

        BlockPos bestSupportPos = null;
        double bestSupportDamage = 0;

        for (BlockPos bp : BlockPos.betweenClosed(origin.offset(-r, -r, -r), origin.offset(r, r, r))) {
            BlockState state = mc.level.getBlockState(bp);

            boolean hasBlock = state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK);
            boolean supportCandidate = !hasBlock && support.get() && state.canBeReplaced();
            if (!hasBlock && !supportCandidate) continue;

            BlockPos above = bp.above();
            if (!mc.level.getBlockState(above).isAir()) continue;

            Vec3 crystalPos = new Vec3(bp.getX() + 0.5, bp.getY() + 1, bp.getZ() + 0.5);
            if (!inRange(crystalPos, above, placeRange.get(), placeWallsRange.get())) continue;

            float selfDamage = DamageUtils.crystalDamage(mc.player, crystalPos, predictMovement.get(), bp.immutable());
            if (selfDamage > maxSelfDamage.get() || (antiSuicide.get() && selfDamage >= EntityUtils.getTotalHealth(mc.player))) continue;

            float damage = DamageUtils.crystalDamage(target, crystalPos, predictMovement.get(), bp.immutable());
            double minimumDamage = shouldFacePlace(target) ? Math.min(minDamage.get(), 1.5) : minDamage.get();
            if (damage < minimumDamage) continue;

            // The crystal's bounding box (2 blocks above the base) must be free of entities.
            AABB crystalBox = new AABB(bp.getX(), bp.getY() + 1, bp.getZ(), bp.getX() + 1, bp.getY() + 3, bp.getZ() + 1);
            if (EntityUtils.intersectsWithEntity(crystalBox, entity -> !entity.isSpectator())) continue;

            if (hasBlock) {
                if (damage > bestBaseDamage) {
                    bestBaseDamage = damage;
                    bestBase = bp.immutable();
                }
            } else if (damage > bestSupportDamage) {
                bestSupportDamage = damage;
                bestSupportPos = bp.immutable();
            }
        }

        if (bestBase != null) {
            if (placeOnBase(bestBase)) placeTimer = placeDelay.get();
        } else if (bestSupportPos != null) {
            placeSupportBlock(bestSupportPos);
        }
    }

    /**
     * Places a crystal on top of the given base block (silent hotbar swap + silent rotation).
     */
    private boolean placeOnBase(BlockPos base) {
        FindItemResult crystal = InvUtils.findInHotbar(Items.END_CRYSTAL);
        if (!crystal.found()) return false;

        BlockHitResult hit = getPlaceInfo(base);
        InteractionHand hand = InteractionHand.MAIN_HAND;

        int realSlot = -1;
        if (crystal.isOffhand()) {
            hand = InteractionHand.OFF_HAND;
        } else if (crystal.isHotbar() && crystal.slot() != mc.player.getInventory().getSelectedSlot()) {
            realSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(crystal.slot()));
        }

        if (rotate.get()) lookAt(hit.getLocation());

        mc.gameMode.useItemOn(mc.player, hand, hit);
        mc.player.connection.send(new ServerboundSwingPacket(hand));

        if (realSlot != -1) mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));

        return true;
    }

    /**
     * Places obsidian into a replaceable block in the air (support block), then either places
     * the crystal immediately (support-delay 0) or lets the normal place loop pick it up
     * after the support delay.
     */
    private void placeSupportBlock(BlockPos supportPos) {
        BlockHitResult hit = getSupportInfo(supportPos);
        if (hit == null) return;

        FindItemResult obsidian = InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        InteractionHand hand = InteractionHand.MAIN_HAND;

        int realSlot = -1;
        if (obsidian.isOffhand()) {
            hand = InteractionHand.OFF_HAND;
        } else if (obsidian.isHotbar() && obsidian.slot() != mc.player.getInventory().getSelectedSlot()) {
            realSlot = mc.player.getInventory().getSelectedSlot();
            mc.player.connection.send(new ServerboundSetCarriedItemPacket(obsidian.slot()));
        }

        if (rotate.get()) lookAt(hit.getLocation());

        mc.gameMode.useItemOn(mc.player, hand, hit);
        mc.player.connection.send(new ServerboundSwingPacket(hand));

        if (realSlot != -1) mc.player.connection.send(new ServerboundSetCarriedItemPacket(realSlot));

        if (supportDelay.get() == 0) placeOnBase(supportPos);
        else placeTimer = supportDelay.get();
    }

    // Helpers

    /**
     * Finds the side of the base block to click on for placing a crystal: raycasts from your
     * eyes to all 6 face centers (like Meteor's CrystalAura) and falls back to the closest
     * face (up/down) when no face is visible.
     */
    private BlockHitResult getPlaceInfo(BlockPos base) {
        Vec3 eye = mc.player.getEyePosition();

        for (Direction side : Direction.values()) {
            Vec3 faceCenter = new Vec3(
                base.getX() + 0.5 + side.getStepX() * 0.5,
                base.getY() + 0.5 + side.getStepY() * 0.5,
                base.getZ() + 0.5 + side.getStepZ() * 0.5
            );

            BlockHitResult result = mc.level.clip(new ClipContext(eye, faceCenter, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            if (result.getType() == HitResult.Type.BLOCK && result.getBlockPos().equals(base)) return result;
        }

        Direction side = base.getY() > eye.y ? Direction.DOWN : Direction.UP;
        Vec3 hitVec = new Vec3(base.getX() + 0.5, base.getY() + 0.5, base.getZ() + 0.5)
            .add(side.getStepX() * 0.5, side.getStepY() * 0.5, side.getStepZ() * 0.5);

        return new BlockHitResult(hitVec, side, base, false);
    }

    /**
     * Finds a neighboring solid block to click on in order to place obsidian into the given
     * air/replaceable position.
     */
    private BlockHitResult getSupportInfo(BlockPos supportPos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = supportPos.relative(dir);
            BlockState state = mc.level.getBlockState(neighbor);
            if (state.isAir() || state.canBeReplaced()) continue;

            Direction face = dir.getOpposite();
            Vec3 hitVec = Vec3.atLowerCornerOf(neighbor).add(0.5, 0.5, 0.5)
                .add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);

            return new BlockHitResult(hitVec, face, neighbor, false);
        }

        return null;
    }

    /**
     * Range check with wall handling: raycasts from your eyes to the position - if a wall is
     * in the way the walls range is used, otherwise the normal range.
     */
    private boolean inRange(Vec3 pos, BlockPos expectedClipPos, double range, double wallsRange) {
        BlockHitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), pos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        boolean behindWall = result.getType() == HitResult.Type.BLOCK && !result.getBlockPos().equals(expectedClipPos);

        return PlayerUtils.isWithin(pos, behindWall ? wallsRange : range);
    }

    /**
     * Face place: relaxes the min damage requirement when the target has low health,
     * nearly broken armor or (optionally) is missing armor pieces.
     */
    private boolean shouldFacePlace(Player target) {
        if (!facePlace.get()) return false;

        if (EntityUtils.getTotalHealth(target) <= facePlaceHealth.get()) return true;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;

            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty()) {
                if (facePlaceArmor.get()) return true;
            } else if ((double) (stack.getMaxDamage() - stack.getDamageValue()) / stack.getMaxDamage() * 100 <= facePlaceDurability.get()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Silent aim: sends a look rotation packet so the server sees us looking at the position,
     * without touching the client-side camera.
     */
    private void lookAt(Vec3 pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 delta = pos.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));

        mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), mc.player.onGround()));
    }

    private boolean isEatingOrDrinking() {
        if (!mc.player.isUsingItem()) return false;
        ItemUseAnimation anim = mc.player.getUseItem().getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }
}
