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
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

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
        if (!Utils.canUpdate() || mc.player == null || mc.level == null || mc.gameMode == null) {
            toggle();
            return;
        }

        // Find a pearl to throw.
        InteractionHand hand = getHand();

        if (hand == null && switchMode.get() == SwitchMode.Silent) {
            FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) {
                error("No ender pearls in your hotbar.");
                toggle();
                return;
            }

            InvUtils.swap(pearl.slot(), true);
            swapped = true;
            hand = InteractionHand.MAIN_HAND;
        }

        if (hand == null) {
            error("Hold an ender pearl (or use Silent switch mode).");
            toggle();
            return;
        }

        // Aim at the center of our own block, rotated 180 degrees, steep pitch.
        prevYaw = mc.player.getYRot();
        prevPitch = mc.player.getXRot();

        double cx = Math.floor(mc.player.getX()) + 0.5;
        double cz = Math.floor(mc.player.getZ()) + 0.5;

        Vec3 eye = mc.player.getEyePosition();
        double dx = cx - eye.x;
        double dz = cz - eye.z;

        yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)) + 180.0f;
        pit = pitch.get().floatValue();

        mc.player.setYRot(yaw);
        mc.player.setXRot(pit);

        stage = Stage.Rotated;

        if (instant.get()) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(yaw, pit, mc.player.onGround(), false));
            throwPearl(hand);
        }
        // Otherwise the vanilla movement packet sends our rotation this tick;
        // onTick throws on the next tick.
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (stage == Stage.Rotated) {
            throwPearl(InteractionHand.MAIN_HAND);
        }
    }

    private void throwPearl(InteractionHand hand) {
        if (mc.player == null || mc.gameMode == null) {
            toggle();
            return;
        }

        ItemStack stack = mc.player.getItemInHand(hand);
        if (!(stack.getItem() instanceof EnderpearlItem)) {
            error("Pearl is no longer in hand.");
            toggle();
            return;
        }

        mc.gameMode.useItem(mc.player, hand);
        mc.player.swing(hand);

        stage = Stage.Thrown;
        cleanup();
        toggle();
        info("Pearl thrown.");
    }

    private void cleanup() {
        stage = Stage.Idle;

        if (mc.player != null && !keepRotation.get()) {
            mc.player.setYRot(prevYaw);
            mc.player.setXRot(prevPitch);
        }

        if (swapped) {
            InvUtils.swapBack();
            swapped = false;
        }
    }

    private InteractionHand getHand() {
        if (mc.player.getMainHandItem().getItem() == Items.ENDER_PEARL) return InteractionHand.MAIN_HAND;
        if (mc.player.getOffhandItem().getItem() == Items.ENDER_PEARL) return InteractionHand.OFF_HAND;
        return null;
    }
}
