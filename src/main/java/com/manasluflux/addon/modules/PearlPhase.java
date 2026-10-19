package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Port of BlackOut's Auto Pearl (KassuK1/BlackOut, author OLEPOSSU):
 * "Easily clip inside walls with pearls."
 *
 * Aims at the center of your own block (yaw + 180, steep pitch) and throws a pearl so it
 * clips you into/past the block. Runs on the tick thread: rotates on one tick so the server
 * receives the rotation in the vanilla movement packet, throws on the next tick, restores
 * your rotation and hotbar, then toggles off. Uses a plain client-side swap - no raw
 * carried-item packets, so nothing desyncs and no blocks get eaten.
 */
public class PearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> pitch = sgGeneral.add(new IntSetting.Builder()
        .name("pitch")
        .description("How deep down to look.")
        .defaultValue(85)
        .range(-90, 90)
        .sliderRange(0, 90)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .description("Normal: only uses a pearl already in your hand. Silent: hotbar-swaps to a pearl and swaps back after throwing.")
        .defaultValue(SwitchMode.Silent)
        .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-rotation")
        .description("Throws on the same tick via a direct rotation packet instead of waiting one tick.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-rotation")
        .description("Keeps looking at the phase spot after throwing instead of restoring your view.")
        .defaultValue(false)
        .build()
    );

    public enum SwitchMode {
        Normal,
        Silent;

        @Override
        public String toString() {
            return switch (this) {
                case Normal -> "Normal";
                case Silent -> "Silent";
            };
        }
    }

    private enum Stage {
        Idle,
        Rotated,
        Thrown
    }

    private Stage stage = Stage.Idle;
    private float prevYaw, prevPitch;
    private float yaw, pit;
    private boolean swapped;

    public PearlPhase() {
        super(AddonTemplate.CATEGORY, "pearl-phase", "Easily clip inside walls with pearls (ported from BlackOut's Auto Pearl).");
    }

    @Override
    public void onActivate() {
        start();
    }

    @Override
    public void onDeactivate() {
        cleanup();
    }

    private void start() {
        if (!Utils.canUpdate() || mc.player == null || mc.world == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        // Find a pearl to throw.
        Hand hand = getHand();

        if (hand == null && switchMode.get() == SwitchMode.Silent) {
            FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) {
                error("No ender pearls in your hotbar.");
                toggle();
                return;
            }

            InvUtils.swap(pearl.slot(), true);
            swapped = true;
            hand = Hand.MAIN_HAND;
        }

        if (hand == null) {
            error("Hold an ender pearl (or use Silent switch mode).");
            toggle();
            return;
        }

        // Aim at the center of our own block, rotated 180 degrees, steep pitch.
        prevYaw = mc.player.getYaw();
        prevPitch = mc.player.getPitch();

        double cx = Math.floor(mc.player.getX()) + 0.5;
        double cz = Math.floor(mc.player.getZ()) + 0.5;

        Vec3d eye = mc.player.getEyePos();
        double dx = cx - eye.x;
        double dz = cz - eye.z;

        yaw = (float) MathHelper.wrapDegrees(Math.atan2(-dx, dz)) + 180.0f;
        pit = pitch.get().floatValue();

        mc.player.setYaw(yaw);
        mc.player.setPitch(pit);

        stage = Stage.Rotated;

        if (instant.get()) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pit, mc.player.isOnGround(), false));
            throwPearl(hand);
        }
        // Otherwise the vanilla movement packet sends our rotation this tick;
        // onTick throws on the next tick.
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (stage == Stage.Rotated) {
            throwPearl(Hand.MAIN_HAND);
        }
    }

    private void throwPearl(Hand hand) {
        if (mc.player == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        ItemStack stack = mc.player.getStackInHand(hand);
        if (!(stack.getItem() instanceof EnderPearlItem)) {
            error("Pearl is no longer in hand.");
            toggle();
            return;
        }

        mc.interactionManager.interactItem(mc.player, hand);
        mc.player.swingHand(hand);

        stage = Stage.Thrown;
        cleanup();
        toggle();
        info("Pearl thrown.");
    }

    private void cleanup() {
        stage = Stage.Idle;

        if (mc.player != null && !keepRotation.get()) {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
        }

        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }
    }

    private Hand getHand() {
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) return Hand.OFF_HAND;
        return null;
    }
}
