package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pearls you straight back into the fight after your own totem pops.
 *
 * <p>The pop is read from the server: {@code USE_TOTEM_OF_UNDYING} for your own entity, which is the
 * same signal the client uses to play the animation. Nothing is guessed from health changes, so it
 * fires on the tick the totem actually saved you, and {@code delay} decides how long after that the
 * pearl goes out (2 ticks by default - the pop's own knockback moves you first).
 *
 * <p>Where to throw is decided at that moment:
 *
 * <ul>
 *   <li><b>A frame or painting is inside you</b> (its box intersects yours - the case a normal pearl
 *       phase cannot handle, because the frame's box sits in front of the block face and the
 *       server's pearl hits it instead of the wall): the throw is aimed at the <b>clear band around
 *       that frame</b> - a corner or edge of the face it hangs on, outside its 12/16-wide box -
 *       with the same steep pitch, so the pearl passes beside the frame and still clips you in. The
 *       point is checked before throwing: it has to be outside every decoration box (expanded by the
 *       pearl's 0.125 radius) and within reach, otherwise it is not used.</li>
 *   <li><b>Nothing in you:</b> the plain throw - BlackOut's own-block aim (your block's centre, 180
 *       degrees) with the same pitch.</li>
 * </ul>
 *
 * <p>Rotation is sent as a look packet, and {@code stable-cam} puts your view back in the same tick
 * so the pop does not also throw your camera. The pearl is taken from your hand, or silently from
 * your hotbar and switched back. The module stays enabled and does this again on the next pop.
 */
public class TotemPopPearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between the totem popping and the pearl leaving your hand. 0 throws on the same tick.")
        .defaultValue(2)
        .range(0, 40)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> frameBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("frame-bypass")
        .description("When a frame or painting is inside you, aim at the clear band around it instead of throwing at the block centre.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
        .name("offset")
        .description("Frame aim: how far from the centre of the face to aim. A frame covers the middle 0.375, the face ends at 0.5, so stay near the edge.")
        .defaultValue(0.46)
        .range(0.0, 0.499)
        .sliderRange(0.375, 0.499)
        .visible(frameBypass::get)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("How steeply down to throw. This is what puts the pearl inside the block instead of against its face.")
        .defaultValue(85.0)
        .range(0.0, 90.0)
        .sliderRange(40.0, 90.0)
        .build()
    );

    private final Setting<Boolean> stableCam = sgGeneral.add(new BoolSetting.Builder()
        .name("stable-cam")
        .description("Keep your camera where it is: the phase rotation goes out as a look packet and your view is put straight back, so the pop does not also spin the screen.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Animate your hand when the pearl is thrown.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Report each pop and where the pearl was thrown.")
        .defaultValue(true)
        .build()
    );

    /** Ticks left before the throw; -1 when nothing is queued. */
    private int countdown = -1;
    private boolean swapped;

    public TotemPopPearlPhase() {
        super(AddonTemplate.BYPASS_CATEGORY, "totempoppearlphase",
            "After your own totem pops, pearls you back in - aiming around an item frame when one is inside you, plain pearl phase when not.");
    }

    @Override
    public void onActivate() {
        countdown = -1;
        swapped = false;
    }

    @Override
    public void onDeactivate() {
        countdown = -1;

        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }
    }

    /** The server tells us the totem was used; that is the pop. */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null) return;
        if (!(event.packet instanceof EntityStatusS2CPacket status)) return;
        if (status.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;
        if (!mc.player.equals(status.getEntity(mc.world))) return;

        countdown = delay.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (countdown < 0) return;

        if (countdown > 0) {
            countdown--;
            return;
        }

        countdown = -1;
        phase();
    }

    /** One pearl, aimed around a frame if there is one inside you. */
    private void phase() {
        Hand hand = handFor();

        if (hand == null) {
            if (chatFeedback.get()) warning("Popped, but there is no pearl to throw.");
            return;
        }

        float yawBefore = mc.player.getYaw();
        float pitchBefore = mc.player.getPitch();

        Vec3d framePoint = frameBypass.get() ? frameAimPoint() : null;

        float yaw = framePoint != null
            ? (float) MathHelper.wrapDegrees(Rotations.getYaw(framePoint))
            : (float) MathHelper.wrapDegrees(Rotations.getYaw(Vec3d.ofCenter(mc.player.getBlockPos())) + 180.0);
        float throwPitch = (float) MathHelper.clamp(pitch.get(), 0.0, 90.0);

        mc.player.setYaw(yaw);
        mc.player.setPitch(throwPitch);

        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, throwPitch, mc.player.isOnGround(), false));
        }

        mc.interactionManager.interactItem(mc.player, hand);
        if (swing.get()) mc.player.swingHand(hand);

        if (stableCam.get()) {
            mc.player.setYaw(yawBefore);
            mc.player.setPitch(pitchBefore);
        }

        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }

        if (chatFeedback.get()) {
            info("Totem popped - pearl thrown %s.", framePoint != null
                ? "around the item frame"
                : "normally (nothing inside you)");
        }
    }

    /**
     * A point on the face of the block a frame/painting is attached to, in the band the decoration
     * does not cover, reachable from your eye. Null when no decoration is inside you or none of its
     * face is usable.
     */
    private Vec3d frameAimPoint() {
        Box self = mc.player.getBoundingBox().expand(0.3);

        for (Entity entity : mc.world.getEntities()) {
            Direction facing;
            BlockPos attached;

            if (entity instanceof ItemFrameEntity frame) {
                facing = frame.getFacing();
                attached = frame.getAttachedBlockPos();
            } else if (entity instanceof PaintingEntity painting) {
                facing = painting.getHorizontalFacing();
                attached = painting.getAttachedBlockPos();
            } else {
                continue;
            }

            if (!entity.getBoundingBox().intersects(self)) continue;
            if (facing == null || facing.getAxis().isVertical()) continue;

            Vec3d point = bandPoint(attached.toImmutable(), facing);
            if (point != null) return point;
        }

        return null;
    }

    /** Picks a usable point in the uncovered border of one face. */
    private Vec3d bandPoint(BlockPos block, Direction facing) {
        Vec3d normal = Vec3d.of(facing.getVector());
        Vec3d faceCentre = Vec3d.ofCenter(block).add(normal.multiply(0.5));

        Vec3d along = normal.crossProduct(new Vec3d(0, 1, 0));
        if (along.lengthSquared() < 1.0E-6) return null;
        along = along.normalize();

        Vec3d up = normal.crossProduct(along).normalize();

        double o = offset.get();
        double[][] candidates = {
            {o, o}, {o, -o}, {-o, o}, {-o, -o},
            {0, o}, {0, -o}, {o, 0}, {-o, 0}
        };

        Vec3d eye = mc.player.getEyePos();

        for (double[] c : candidates) {
            Vec3d point = faceCentre.add(along.multiply(c[0])).add(up.multiply(c[1]));

            if (eye.distanceTo(point) > 4.0) continue;
            if (coveredByDecoration(point)) continue;

            return point;
        }

        return null;
    }

    /** Whether the pearl's path to this point would run into a frame or painting. */
    private boolean coveredByDecoration(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity) && !(entity instanceof PaintingEntity)) continue;

            Box box = entity.getBoundingBox().expand(0.125);

            // Eye inside the decoration's box: a ray from inside always reports an exit hit, which
            // says nothing about whether the pearl gets through, so it is not used as a veto.
            if (box.contains(eye)) continue;

            if (box.contains(point)) return true;
            if (box.raycast(eye, point).isPresent()) return true;
        }

        return false;
    }

    private Hand handFor() {
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) return Hand.OFF_HAND;

        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) return null;

        if (InvUtils.swap(pearl.slot(), false)) {
            swapped = true;

            if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) return Hand.MAIN_HAND;
        }

        return null;
    }

    @Override
    public String getInfoString() {
        return countdown >= 0 ? "in " + countdown : null;
    }
}
