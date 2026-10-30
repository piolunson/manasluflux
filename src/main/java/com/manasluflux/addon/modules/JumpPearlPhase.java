package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
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
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Sneak, jump, and throw the pearl partway up the jump.
 *
 * <p>That is the whole module. The jump is not decoration: a pearl thrown while your feet are off
 * the ground starts its flight from a spot inside the block you are standing next to, and the
 * teleport that follows lands you in it. When in the jump matters, because throwing at the top of
 * the arc starts too high to end up inside the block below and throwing from the ground never gets
 * the pearl into it at all - hence {@code jump-percent}.
 *
 * <p>"Percent of the jump" is measured against the real vanilla jump arc - 0.42 blocks/tick upward
 * with 0.08 gravity and 0.98 drag, which peaks at <b>1.2522 blocks</b> - so 55% means the throw goes
 * out when your feet are 0.69 blocks up, not at some arbitrary tick. If the jump never gets that
 * high (a ceiling, a slab, low gravity), the module says so and spends no pearl.
 *
 * <p>{@code stable-cam} keeps your view exactly where it was: the phase rotation is sent to the
 * server as a look packet and the camera is put back in the same tick, so the screen never snaps to
 * the throw. With it off, the camera stays pointed at the throw after the pearl leaves.
 */
public class JumpPearlPhase extends Module {
    /** Peak height of a vanilla jump in blocks. */
    private static final double VANILLA_JUMP_HEIGHT = 1.2522;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> jumpPercent = sgGeneral.add(new IntSetting.Builder()
        .name("jump-percent")
        .description("Throw when the jump has reached this much of its height. The middle of the arc puts the pearl inside the block; 0 throws on the takeoff tick.")
        .defaultValue(55)
        .range(0, 100)
        .sliderRange(0, 100)
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

    private final Setting<Boolean> sneak = sgGeneral.add(new BoolSetting.Builder()
        .name("sneak")
        .description("Hold sneak from the jump until the pearl is thrown.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> stableCam = sgGeneral.add(new BoolSetting.Builder()
        .name("stable-cam")
        .description("Keep your camera where it is: the phase rotation goes out as a look packet and your view is put straight back, so the screen never jumps. Off: the camera stays turned at the throw.")
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
        .description("Report the throw, or why there wasn't one.")
        .defaultValue(true)
        .build()
    );

    private enum Stage { Idle, WaitingForGround, Jumping }

    private Stage stage = Stage.Idle;
    private int ticks;
    private double startY;
    private double reached;
    private boolean swapped;
    private boolean sneakingBefore;
    private float yawBefore;
    private float pitchBefore;

    public JumpPearlPhase() {
        super(AddonTemplate.BYPASS_CATEGORY, "universal-jump-pearl",
            "Sneak, jump, and throw the pearl at a set percent of the jump - the phase timing, without the rest.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            error("Join a world first.");
            toggle();
            return;
        }

        if (!hasPearl()) {
            error("No ender pearls in your hand or hotbar.");
            toggle();
            return;
        }

        stage = Stage.Idle;
        ticks = 0;
        reached = 0;
        swapped = false;

        sneakingBefore = mc.player.isSneaking();
        yawBefore = mc.player.getYaw();
        pitchBefore = mc.player.getPitch();

        if (mc.player.isOnGround()) {
            beginJump();
        } else {
            // Mid-air already: wait for the landing rather than throwing from nothing.
            stage = Stage.WaitingForGround;
        }
    }

    @Override
    public void onDeactivate() {
        stage = Stage.Idle;

        if (mc.player != null) mc.player.setSneaking(sneakingBefore);

        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            toggle();
            return;
        }

        switch (stage) {
            case WaitingForGround -> {
                if (mc.player.isOnGround()) beginJump();
            }

            case Jumping -> tickJump();

            default -> { }
        }
    }

    private void beginJump() {
        if (sneak.get()) mc.player.setSneaking(true);

        mc.player.jump();

        startY = mc.player.getY();
        ticks = 0;
        stage = Stage.Jumping;
    }

    private void tickJump() {
        ticks++;

        double height = mc.player.getY() - startY;
        if (height > reached) reached = height;

        double percent = Math.min(100.0, height / VANILLA_JUMP_HEIGHT * 100.0);

        if (percent >= jumpPercent.get()) {
            throwPearl(percent);
            return;
        }

        // Back on the ground without ever getting high enough: no pearl is spent, because a throw
        // from the floor is not a phase, it is just a pearl.
        boolean landed = mc.player.isOnGround() && ticks > 2;

        if (landed || ticks > 30) {
            if (chatFeedback.get()) {
                warning("The jump only reached %.0f%% of its height (wanted %d%%) - nothing thrown.",
                    reached / VANILLA_JUMP_HEIGHT * 100.0, jumpPercent.get());
            }

            toggle();
        }
    }

    private void throwPearl(double percent) {
        Hand hand = handFor();

        if (hand == null) {
            error("No pearl in hand.");
            toggle();
            return;
        }

        // BlackOut's Auto Pearl aim: your own block's centre, turned around, thrown steeply down.
        float yaw = (float) MathHelper.wrapDegrees(
            Rotations.getYaw(Vec3d.ofCenter(mc.player.getBlockPos())) + 180.0);
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

        if (chatFeedback.get()) {
            info("Pearl thrown at %.0f%% of the jump (%.2f blocks up).", percent, mc.player.getY() - startY);
        }

        if (sneak.get()) mc.player.setSneaking(sneakingBefore);

        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }

        stage = Stage.Idle;
        toggle();
    }

    /** A pearl you hold, or the first one in the hotbar selected for the throw. */
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

    private boolean hasPearl() {
        return mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL
            || mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL
            || InvUtils.findInHotbar(Items.ENDER_PEARL).found();
    }

    @Override
    public String getInfoString() {
        return jumpPercent.get() + "%";
    }
}
