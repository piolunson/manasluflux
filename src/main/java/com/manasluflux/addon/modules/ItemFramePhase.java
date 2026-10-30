package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * A <b>pearl phase</b> that works when an item frame is in the way.
 *
 * <p>The trick this replaces was a pearl thrown into your own block. It fails when a frame hangs on
 * that block, and the reason is geometric, not a patch: an item frame's box is <b>12/16 wide, 12/16
 * high and 1/16 thick</b>, sitting <b>just in front of the block face</b> (a map frame is a full
 * 1x1 face, a painting is as wide as its variant and still 1/16 thick). Vanilla gives frames no
 * projectile immunity, so a pearl whose path crosses that box hits it, follows its entity-hit path
 * and ends its flight at your own position - which is why nothing happens.
 *
 * <p>Hiding the frame client-side cannot fix it either: the server owns the pearl, so it still hits
 * the frame. The only lever is the throw itself, and there is a real gap to use - the frame covers
 * the middle 12/16 of the face, leaving a band around it. This module aims the throw at a point in
 * that band (a corner or edge of the face) instead of its centre, keeps BlackOut's steep pitch that
 * makes the pearl clip you into the block, and <b>measures</b> whether the throw moved you. If it
 * did not, it walks through the other points on the face and tries again, so a server/frame
 * combination that only works from one corner is found rather than guessed.
 *
 * <p>If every point on the face is covered - a map frame is exactly that - it says so and does not
 * throw, because there is no honest way to pearl through a full-face hitbox.
 *
 * <p>Rotation is silent (a look packet; your camera is put back), and the pearl can be brought in
 * from a hotbar slot with a silent switch. This is an attempt that reports what happened, not a
 * guarantee: whether a given server lets the pearl put you inside the block is measured, never
 * assumed.
 */
public abstract class ItemFramePhase extends Module {
    public enum Aim { Corner, OwnBlock }

    public enum SwitchMode { Normal, Silent }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Aim> aim = sgGeneral.add(new EnumSetting.Builder<Aim>()
        .name("aim")
        .description("Corner: aim at the clear band around an item frame on the target face. OwnBlock: BlackOut's plain pearl phase, thrown straight down into your own block - no frame handling at all.")
        .defaultValue(Aim.Corner)
        .build()
    );

    private final Setting<Double> offset = sgGeneral.add(new DoubleSetting.Builder()
        .name("offset")
        .description("Corner aim: how far from the centre of the face to aim. A frame covers the middle 0.375, the face ends at 0.5 - a pearl is 0.25 wide, so stay near the edge.")
        .defaultValue(0.46)
        .range(0.0, 0.499)
        .sliderRange(0.375, 0.499)
        .visible(() -> aim.get() == Aim.Corner)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("How steeply down to throw. This is what makes the pearl put you inside the block rather than just hit the wall; 85 is BlackOut's Auto Pearl default.")
        .defaultValue(85.0)
        .range(0.0, 90.0)
        .sliderRange(40.0, 90.0)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How far away a frame may be for the module to use its face. Reach is about 3 blocks, so going much past that only finds frames you cannot throw at.")
        .defaultValue(4.5)
        .range(1.0, 16.0)
        .sliderRange(1.0, 8.0)
        .build()
    );

    private final Setting<Integer> tries = sgGeneral.add(new IntSetting.Builder()
        .name("tries")
        .description("How many points on the face to try. Each one is a real throw, so the pearl is spent each time.")
        .defaultValue(4)
        .range(1, 8)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> settleTicks = sgGeneral.add(new IntSetting.Builder()
        .name("settle-ticks")
        .description("How long to wait after a throw before deciding whether it moved you.")
        .defaultValue(8)
        .range(1, 200)
        .sliderRange(1, 40)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .description("Normal: only a pearl you are already holding. Silent: selects a hotbar slot with the pearl and switches back afterwards.")
        .defaultValue(SwitchMode.Silent)
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
        .description("Report each throw and what it did.")
        .defaultValue(true)
        .build()
    );

    private enum Stage { Idle, Prepare, Throw, Wait }

    /** Ticks of real rotation before the throw - NCP wants the server to see the look first. */
    private final int prepareTicks;

    private Stage stage = Stage.Idle;
    private int ticks;
    private int attempt;
    private int attemptsLeft;

    private final List<Vec3d> points = new ArrayList<>();
    private final List<String> skipped = new ArrayList<>();

    private BlockPos target;
    private Vec3d aimPoint;

    private float yawBefore;
    private float pitchBefore;
    private boolean corrected;
    private boolean switched;
    /** True when there was no frame to work around and the throw is BlackOut's own-block one. */
    private boolean ownBlockAim;

    protected ItemFramePhase(String name, String description, int prepareTicks, int settleTicks) {
        super(AddonTemplate.BYPASS_CATEGORY, name, description);

        this.prepareTicks = prepareTicks;
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            error("Join a world first.");
            toggle();
            return;
        }

        if (switchMode.get() == SwitchMode.Normal && heldPearlHand() == null) {
            error("Hold an ender pearl (or set switch-mode to Silent).");
            toggle();
            return;
        }

        if (switchMode.get() == SwitchMode.Silent && !InvUtils.findInHotbar(Items.ENDER_PEARL).found() && heldPearlHand() == null) {
            error("No ender pearls in your hotbar.");
            toggle();
            return;
        }

        stage = Stage.Idle;
        ticks = 0;
        attempt = 0;
        attemptsLeft = tries.get();
        corrected = false;
        switched = false;
        skipped.clear();

        yawBefore = mc.player.getYaw();
        pitchBefore = mc.player.getPitch();

        buildPoints();

        if (points.isEmpty()) {
            if (chatFeedback.get()) warning("%s", skipped.isEmpty()
                ? "No face to aim at nearby."
                : "Nothing to throw at: " + String.join("; ", skipped) + ".");
            toggle();
            return;
        }

        beginAttempt();
    }

    @Override
    public void onDeactivate() {
        stage = Stage.Idle;

        if (mc.player != null) {
            mc.player.setYaw(yawBefore);
            mc.player.setPitch(pitchBefore);
        }

        if (switched) {
            InvUtils.swapBack();
            switched = false;
        }
    }

    /** Finds the face to throw at and the points on it that a frame does not cover. */
    private void buildPoints() {
        points.clear();
        target = null;
        ownBlockAim = false;

        if (aim.get() == Aim.Corner && findDecoratedBlock()) return;

        // OwnBlock (or nothing to avoid): BlackOut's pearl phase - your own block, thrown backwards.
        target = mc.player.getBlockPos().toImmutable();
        ownBlockAim = true;

        points.add(Vec3d.ofCenter(target));

        if (aim.get() == Aim.Corner) {
            skipped.add("no frame in range, using your own block");
        }
    }

    /**
     * Looks for the nearest frame or painting and fills {@link #points} with the parts of the face a
     * frame's box does not cover, nearest-to-your-aim first.
     */
    private boolean findDecoratedBlock() {
        Entity found = null;
        BlockPos foundBlock = null;
        Direction foundFacing = null;
        double best = range.get() * range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity) && !(entity instanceof PaintingEntity)) continue;

            Direction facing;
            BlockPos attached;

            if (entity instanceof ItemFrameEntity frame) {
                facing = frame.getFacing();
                attached = frame.getAttachedBlockPos();
            } else {
                PaintingEntity painting = (PaintingEntity) entity;
                facing = painting.getHorizontalFacing();
                attached = painting.getAttachedBlockPos();
            }

            // Floor and ceiling decorations hang off a different axis; their box is not on a wall
            // face, so the corner trick does not apply to them.
            if (facing == null || facing.getAxis().isVertical()) continue;

            double distanceSq = mc.player.squaredDistanceTo(entity);
            if (distanceSq >= best) continue;

            best = distanceSq;
            found = entity;
            foundBlock = attached;
            foundFacing = facing;
        }

        if (found == null) return false;

        target = foundBlock.toImmutable();

        Vec3d normal = Vec3d.of(foundFacing.getVector());
        Vec3d faceCentre = Vec3d.ofCenter(target).add(normal.multiply(0.5));

        // Two axes along the face: horizontal tangent and "up".
        Vec3d along = normal.crossProduct(new Vec3d(0, 1, 0));
        if (along.lengthSquared() < 1.0E-6) return false;
        along = along.normalize();
        Vec3d up = normal.crossProduct(along).normalize();

        double o = offset.get();

        double[][] corners = {
            {o, o}, {o, -o}, {-o, o}, {-o, -o},
            {0, o}, {0, -o}, {o, 0}, {-o, 0}
        };

        for (double[] c : corners) {
            Vec3d point = faceCentre.add(along.multiply(c[0])).add(up.multiply(c[1]));

            if (!inReach(point)) {
                skipped.add(String.format("a point on %s is out of reach", target.toShortString()));
                continue;
            }

            if (coveredByDecoration(point)) {
                skipped.add("a frame covers that point");
                continue;
            }

            points.add(point);
        }

        return !points.isEmpty();
    }

    /** Whether the ray from your eye to this point passes through a frame or painting. */
    private boolean coveredByDecoration(Vec3d point) {
        Vec3d eye = mc.player.getEyePos();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemFrameEntity) && !(entity instanceof PaintingEntity)) continue;

            // The pearl is 0.25 wide, so its centre has to keep that much clear of the frame box.
            Box box = entity.getBoundingBox().expand(0.125);

            // Eye already inside the frame's box (you are pressed into it): a ray starting inside
            // always reports an exit hit, so it says nothing useful. Spawned projectiles only test
            // collisions along their movement, so the throw itself is what decides.
            if (box.contains(eye)) continue;

            if (box.contains(point)) return true;
            if (box.raycast(eye, point).isPresent()) return true;
        }

        return false;
    }

    private boolean inReach(Vec3d point) {
        return mc.player.getEyePos().distanceTo(point) <= 4.0;
    }

    /** Starts the next throw: rotate to the next point, then throw after the prepare ticks. */
    private void beginAttempt() {
        if (attempt >= points.size() || attemptsLeft <= 0) {
            finish();
            return;
        }

        aimPoint = points.get(attempt);
        corrected = false;

        rotate();

        stage = Stage.Prepare;
        ticks = 0;
    }

    /** Silent rotation: your camera is flicked only for the throw itself. */
    private void rotate() {
        if (mc.player == null) return;

        // Aiming at the block you stand in has no meaningful yaw (it is straight down), so the
        // own-block throw keeps the yaw you already have and only the steep pitch matters.
        float yaw = ownBlockAim
            ? mc.player.getYaw()
            : (float) MathHelper.wrapDegrees(Rotations.getYaw(aimPoint));
        float p = (float) MathHelper.clamp(pitch.get(), 0.0, 90.0);

        if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, p, mc.player.isOnGround(), false));
        }

        mc.player.setYaw(yaw);
        mc.player.setPitch(p);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        switch (stage) {
            case Prepare -> {
                if (++ticks >= prepareTicks) {
                    stage = Stage.Throw;
                    ticks = 0;
                }
            }

            case Throw -> throwPearl();

            case Wait -> {
                if (++ticks < settleTicks.get()) return;

                if (corrected) {
                    if (chatFeedback.get()) {
                        info("Server moved you after the throw (point %d of %d on %s) - check whether you are inside the block.",
                            attempt + 1, points.size(), target == null ? "the block" : target.toShortString());
                    }

                    toggle();
                    return;
                }

                attempt++;
                attemptsLeft--;

                if (attemptsLeft <= 0 || attempt >= points.size()) {
                    finish();
                    return;
                }

                if (chatFeedback.get()) warning("That point did not move you - trying the next one.");
                beginAttempt();
            }

            default -> { }
        }
    }

    private void throwPearl() {
        Hand hand = handFor();

        if (hand == null) {
            error("Could not get a pearl in hand.");
            toggle();
            return;
        }

        // The client rotation decides the pearl's velocity, so it has to be right at the throw.
        rotate();

        mc.interactionManager.interactItem(mc.player, hand);
        if (swing.get()) mc.player.swingHand(hand);

        // Camera back immediately: the server already has the look from the rotate() packet.
        mc.player.setYaw(yawBefore);
        mc.player.setPitch(pitchBefore);

        stage = Stage.Wait;
        ticks = 0;
    }

    /** A server-side correction, or a real teleport, is how a pearl phase shows up. */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (stage != Stage.Wait) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket)) return;

        // Either the pearl hop itself or a correction. Both mean the server moved you, and telling
        // them apart from a packet alone is guesswork, so the module reports what it saw.
        corrected = true;
    }

    private Hand handFor() {
        Hand held = heldPearlHand();
        if (held != null) return held;
        if (switchMode.get() == SwitchMode.Normal) return null;

        FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
        if (!pearl.found()) return null;

        if (InvUtils.swap(pearl.slot(), false)) {
            switched = true;

            if (mc.player.getMainHandStack().getItem() instanceof EnderPearlItem) return Hand.MAIN_HAND;
        }

        return null;
    }

    private Hand heldPearlHand() {
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) return Hand.OFF_HAND;
        return null;
    }

    /** Says what happened when every point failed. */
    private void finish() {
        stage = Stage.Idle;

        if (chatFeedback.get()) {
            String where = target == null ? "the block" : target.toShortString();

            if (corrected) {
                info("Server moved you near %s.", where);
            } else if (skipped.isEmpty()) {
                warning("No point on %s moved you - the server did not accept the pearl there.", where);
            } else {
                warning("No point on %s moved you (%s).", where, String.join("; ", skipped));
            }
        }

        toggle();
    }

    @Override
    public String getInfoString() {
        if (aim.get() == Aim.OwnBlock) return "own block";

        int covered = 0;
        for (String note : skipped) if (note.contains("covers")) covered++;

        return covered == 0 ? "corner" : "corner, " + covered + " covered";
    }
}
