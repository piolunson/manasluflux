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
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.entity.EntityUtils;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.CardinalDirection;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BedBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BedItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Explodes beds and respawn anchors next to a target, choosing whichever one works in the current
 * dimension instead of being a Nether-only module.
 *
 * <ul>
 *   <li><b>Beds</b> detonate in the <b>Nether</b> and the <b>End</b>, and cannot be used in the
 *       Overworld - there they just lie down as a spawn point.</li>
 *   <li><b>Respawn anchors</b> detonate in the <b>Overworld</b> and the <b>End</b>, once they are
 *       charged with glowstone. In the Nether a charged anchor sets your spawn and eats a charge
 *       instead, so it does no damage there.</li>
 * </ul>
 *
 * <p>Between them that covers every dimension. The module picks the item the dimension allows,
 * falls back to the other one if you are not carrying it, places, then detonates: a bed with a
 * plain use on the block, an anchor by charging it with glowstone and using it once more without
 * glowstone in hand.
 *
 * <p>Placement copies the trick Meteor's own bed aura uses: the candidate is the block next to
 * you, the client is rotated to face the target block, and the bed's head half then lands on it.
 */
public class BedAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgTargeting = settings.createGroup("Targeting");
    private final SettingGroup sgItems = settings.createGroup("Items");
    private final SettingGroup sgPause = settings.createGroup("Pause");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // ---- General ----------------------------------------------------------------

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
        .name("place-delay")
        .description("Ticks between placements.")
        .defaultValue(5)
        .range(0, 10000)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> strictDirection = sgGeneral.add(new BoolSetting.Builder()
        .name("strict-direction")
        .description("Only place beds along the direction you are already facing.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swing your hand client-side when placing.")
        .defaultValue(true)
        .build()
    );

    // ---- Targeting --------------------------------------------------------------

    private final Setting<Double> targetRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("How far away a player can be to be targeted, in blocks.")
        .defaultValue(4.5)
        .range(0, 10000)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<SortPriority> priority = sgTargeting.add(new EnumSetting.Builder<SortPriority>()
        .name("target-priority")
        .description("Which player to pick when several are in range.")
        .defaultValue(SortPriority.LowestHealth)
        .build()
    );

    private final Setting<Double> minDamage = sgTargeting.add(new DoubleSetting.Builder()
        .name("min-damage")
        .description("The least damage a placement has to do to the target.")
        .defaultValue(7.0)
        .range(0, 10000)
        .sliderRange(0, 36)
        .build()
    );

    private final Setting<Double> maxSelfDamage = sgTargeting.add(new DoubleSetting.Builder()
        .name("max-self-damage")
        .description("The most damage a placement may do to you.")
        .defaultValue(7.0)
        .range(0, 10000)
        .sliderRange(0, 36)
        .build()
    );

    private final Setting<Boolean> antiSuicide = sgTargeting.add(new BoolSetting.Builder()
        .name("anti-suicide")
        .description("Never place or pop something that would kill you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> placeRange = sgTargeting.add(new DoubleSetting.Builder()
        .name("place-range")
        .description("How far away you may place, in blocks.")
        .defaultValue(4.5)
        .range(0, 10000)
        .sliderRange(0, 6)
        .build()
    );

    // ---- Items ------------------------------------------------------------------

    private final Setting<Boolean> autoSwitch = sgItems.add(new BoolSetting.Builder()
        .name("auto-switch")
        .description("Silently switch to the bed / anchor / glowstone when needed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> anchorCharges = sgItems.add(new IntSetting.Builder()
        .name("anchor-charges")
        .description("How many glowstone charges to put into a respawn anchor before popping it (more charges = more damage).")
        .defaultValue(2)
        .range(1, 4)
        .sliderRange(1, 4)
        .build()
    );

    private final Setting<Integer> chargeDelay = sgItems.add(new IntSetting.Builder()
        .name("charge-delay")
        .description("Ticks between anchor charges (an anchor cannot take two in the same tick).")
        .defaultValue(3)
        .range(0, 10000)
        .sliderRange(0, 10)
        .build()
    );

    // ---- Pause ------------------------------------------------------------------

    private final Setting<Boolean> pauseOnEat = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Stops while you are eating.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnDrink = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-drink")
        .description("Stops while you are drinking.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Stops while you are mining.")
        .defaultValue(true)
        .build()
    );

    // ---- Render -----------------------------------------------------------------

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Draws the block it is aiming at.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the box is drawn.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill colour of the box.")
        .defaultValue(new SettingColor(196, 116, 240, 45))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline colour of the box.")
        .defaultValue(new SettingColor(196, 116, 240, 220))
        .build()
    );

    /** What the module is currently working with. */
    private enum Mode { Bed, Anchor }

    private PlayerEntity target;
    private BlockPos placePos;
    private BlockPos livePos;
    private Mode mode = Mode.Bed;
    private CardinalDirection direction = CardinalDirection.North;
    private int placeTimer;
    private int chargeTimer;
    private int chargesLeft;

    public BedAura() {
        super(AddonTemplate.COMBAT_CATEGORY, "mf-bed-aura",
            "Places and pops beds in the Nether/End and charged respawn anchors in the Overworld/End - whichever the dimension allows.");
    }

    @Override
    public void onActivate() {
        placeTimer = 0;
        chargeTimer = 0;
        chargesLeft = 0;
        placePos = null;
        livePos = null;
    }

    @Override
    public void onDeactivate() {
        placePos = null;
        livePos = null;
        chargesLeft = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (PlayerUtils.shouldPause(pauseOnMine.get(), pauseOnEat.get(), pauseOnDrink.get())) return;

        // Something is already down: finish it before starting another one, target or not - a bed
        // left lying around is a bed the server can make you sleep in.
        if (livePos != null) {
            if (!alive(livePos)) {
                livePos = null;
                chargesLeft = 0;
            } else if (mode == Mode.Anchor && chargesLeft > 0) {
                chargeAnchor(livePos);
                return;
            } else {
                detonate(livePos);
                livePos = null;
                chargesLeft = 0;
                placeTimer = placeDelay.get();
                return;
            }
        }

        target = TargetUtils.getPlayerTarget(targetRange.get(), priority.get());
        if (target == null) {
            placePos = null;
            return;
        }

        if (placeTimer > 0) {
            placeTimer--;
            return;
        }

        pickMode();
        placePos = findPlace();
        if (placePos == null) return;

        if (place()) {
            livePos = placePos;
            placePos = null;
            chargesLeft = mode == Mode.Anchor ? Math.max(1, anchorCharges.get()) : 0;
            placeTimer = placeDelay.get();
            chargeTimer = 0;
        }
    }

    /**
     * Beds explode in the Nether and the End, anchors everywhere but the Nether. Pick the one that
     * can actually go off here, and fall back to the other if the first is not in the inventory.
     */
    private void pickMode() {
        String dimension = mc.world.getRegistryKey().getValue().getPath();
        boolean bedsWork = !dimension.equals("overworld");
        boolean anchorsWork = !dimension.equals("the_nether");

        boolean hasBed = findBed().found();
        boolean hasAnchor = InvUtils.find(Items.RESPAWN_ANCHOR).found();

        if (bedsWork && hasBed) mode = Mode.Bed;
        else if (anchorsWork && hasAnchor) mode = Mode.Anchor;
        else if (bedsWork) mode = Mode.Bed;
        else if (anchorsWork) mode = Mode.Anchor;
    }

    private FindItemResult findBed() {
        return InvUtils.find(itemStack -> itemStack.getItem() instanceof BedItem);
    }

    /** The block a target can actually be hit from, or {@code null} if the best spot does no damage. */
    private BlockPos findPlace() {
        if (target == null || mc.player == null) return null;

        if (mode == Mode.Bed && !findBed().found()) return null;
        if (mode == Mode.Anchor && !InvUtils.find(Items.RESPAWN_ANCHOR).found()) return null;

        // Above the target first, then their feet block, then two up - the same order Meteor uses,
        // because the bed has to sit on something and the target's own block usually does not.
        int[] heights = {1, 0, 2};

        for (int up : heights) {
            for (CardinalDirection dir : CardinalDirection.values()) {
                if (strictDirection.get()
                    && dir.toDirection() != mc.player.getHorizontalFacing()
                    && dir.toDirection() != mc.player.getHorizontalFacing().getOpposite()) continue;

                BlockPos center = target.getBlockPos().up(up);
                BlockPos clicked = mode == Mode.Bed ? center.offset(dir.toDirection()) : center;

                if (!canReach(clicked)) continue;
                if (!mc.world.getBlockState(clicked).isReplaceable()) continue;

                // The bed needs both halves free; the anchor only needs its own block.
                if (mode == Mode.Bed && !mc.world.getBlockState(center).isReplaceable()) continue;
                if (!BlockUtils.canPlace(clicked)) continue;

                Vec3d blast = Vec3d.ofCenter(center);
                double enemy = damage(blast);
                double self = selfDamage(blast);

                if (enemy < minDamage.get()) continue;
                if (self >= maxSelfDamage.get()) continue;
                if (antiSuicide.get() && PlayerUtils.getTotalHealth() - self <= 0) continue;

                direction = dir;
                return clicked;
            }
        }

        return null;
    }

    private double damage(Vec3d blast) {
        if (target == null) return 0;
        return mode == Mode.Bed ? DamageUtils.bedDamage(target, blast) : DamageUtils.anchorDamage(target, blast);
    }

    private double selfDamage(Vec3d blast) {
        if (mc.player == null) return 0;
        return mode == Mode.Bed ? DamageUtils.bedDamage(mc.player, blast) : DamageUtils.anchorDamage(mc.player, blast);
    }

    private boolean canReach(BlockPos pos) {
        if (mc.player == null) return false;
        return mc.player.canInteractWithBlockAt(pos, placeRange.get());
    }

    /** Places the bed (rotated so its head lands on the target block) or the anchor. */
    private boolean place() {
        if (placePos == null) return false;

        FindItemResult item = mode == Mode.Bed ? findBed() : InvUtils.find(Items.RESPAWN_ANCHOR);
        if (!item.found()) return false;

        // BlockUtils can only place from the hotbar, so pull the item in first and place next tick.
        if (!item.isHotbar() && !item.isOffhand()) {
            if (!autoSwitch.get()) return false;

            FindItemResult empty = InvUtils.findEmpty();
            if (!empty.found() || !empty.isHotbar()) return false;

            InvUtils.move().from(item.slot()).toHotbar(empty.slot());
            return false;
        }

        if (mode == Mode.Bed) {
            double yaw = switch (direction) {
                case East -> 90;
                case South -> 180;
                case West -> -90;
                default -> 0;
            };

            Rotations.rotate(yaw, Rotations.getPitch(placePos), () ->
                BlockUtils.place(placePos, item, false, 50, swing.get(), true, true));
        } else {
            BlockUtils.place(placePos, item, true, 50, swing.get(), true, true);
        }

        return true;
    }

    /** Puts one glowstone charge into the anchor, then hands the hotbar back. */
    private void chargeAnchor(BlockPos pos) {
        if (chargeTimer > 0) {
            chargeTimer--;
            return;
        }

        FindItemResult glowstone = InvUtils.findInHotbar(Items.GLOWSTONE);
        if (!glowstone.found()) {
            chargesLeft = 0; // nothing to charge with: pop what is there instead of waiting forever
            return;
        }

        useOn(pos, glowstone.slot(), false);
        chargesLeft--;
        chargeTimer = chargeDelay.get();
    }

    /**
     * Pops the block. A bed explodes on a plain use; an anchor explodes when used <b>without</b>
     * glowstone in the hand doing the using, which is why the off hand does it - the glowstone
     * used for charging is in the main hand.
     */
    private void detonate(BlockPos pos) {
        boolean offhandGlow = mc.player != null && mc.player.getOffHandStack().isOf(Items.GLOWSTONE);

        if (!offhandGlow) {
            useOn(mode == Mode.Bed ? bedPos(pos) : pos, -1, true);
            return;
        }

        // Off hand would just charge it again: pick a clean hotbar slot for one packet instead.
        FindItemResult clean = InvUtils.findEmpty();
        if (!clean.found() || !clean.isHotbar()) {
            clean = InvUtils.findInHotbar(itemStack -> !itemStack.isOf(Items.GLOWSTONE) && !itemStack.isEmpty());
        }

        if (clean.found() && clean.isHotbar()) useOn(mode == Mode.Bed ? bedPos(pos) : pos, clean.slot(), false);
    }

    /**
     * Where the bed actually ended up. The client decides the bed's second half from your facing,
     * so the placed block can be one block off the spot that was aimed at - pop whatever is there.
     */
    private BlockPos bedPos(BlockPos near) {
        if (mc.world == null) return near;

        double best = 9.0;
        BlockPos found = null;

        for (BlockEntity blockEntity : Utils.blockEntities()) {
            if (!(blockEntity instanceof BedBlockEntity)) continue;

            BlockPos pos = blockEntity.getPos();
            if (!(mc.world.getBlockState(pos).getBlock() instanceof BedBlock)) continue;

            double distance = pos.getSquaredDistance(near);
            if (distance <= best) {
                best = distance;
                found = pos;
            }
        }

        return found != null ? found : near;
    }

    /**
     * Right-clicks {@code pos} with a chosen hotbar slot selected for that one packet.
     *
     * @param slot     hotbar slot to select, or {@code -1} to leave the selection alone
     * @param offHand  use the off hand (beds detonate fine that way, and it keeps glowstone out of it)
     */
    private void useOn(BlockPos pos, int slot, boolean offHand) {
        if (mc.player == null || mc.interactionManager == null) return;

        // Sneaking swallows the use packet, so stand up for it and sit back down after.
        boolean sneaking = mc.player.isSneaking();
        if (sneaking) mc.player.setSneaking(false);

        int original = mc.player.getInventory().getSelectedSlot();
        boolean swap = autoSwitch.get() && slot >= 0 && slot != original;
        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);
        ActionResult result = mc.interactionManager.interactBlock(mc.player,
            offHand ? Hand.OFF_HAND : Hand.MAIN_HAND, hit);

        if (result.isAccepted()) mc.player.swingHand(offHand ? Hand.OFF_HAND : Hand.MAIN_HAND);
        else if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);

        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(original));
        if (sneaking) mc.player.setSneaking(true);
    }

    /** Is the block we planted still there? Beds are checked loosely, since their second half shifts. */
    private boolean alive(BlockPos pos) {
        if (mc.world == null) return false;
        if (mode == Mode.Bed) return !bedPos(pos).equals(pos) || mc.world.getBlockState(pos).getBlock() instanceof BedBlock;
        return mc.world.getBlockState(pos).isOf(Blocks.RESPAWN_ANCHOR);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        BlockPos pos = livePos != null ? livePos : placePos;
        if (!render.get() || pos == null) return;

        Box box = new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
        event.renderer.box(box, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
    }

    @Override
    public String getInfoString() {
        String who = EntityUtils.getName(target);
        if (who == null) who = "";
        String what = mode == Mode.Bed ? "bed" : "anchor";
        return who.isEmpty() ? what : who + " " + what;
    }
}
