package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.EnderPearlItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Port of BlackOut's <b>Auto Pearl</b> ({@code kassuk.addon.blackout.modules.AutoPearl},
 * KassuK1/BlackOut, GPL-3.0, author OLEPOSSU) — "Easily clip inside walls with pearls."
 *
 * <p>The original file is vendored unmodified in {@code blackout/modules/AutoPearl.java}. This is
 * the 1.21.11/Meteor adaptation of it: same aim (yaw of your own block centre + 180, steep pitch),
 * same <b>CC Bypass</b> (place a block under yourself before throwing, for Constantiam-style
 * anti-delay), same switch modes, plus the original's instant-rotation and swing toggles.
 *
 * <p>BlackOut drives this from its render event with its own rotation manager; there is no such
 * manager here, so the sequence runs on the tick: rotate (optionally with a look packet), then
 * throw on the following tick, then restore the view and hotbar and switch itself off.
 */
public class PearlPhase extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> ccBypass = sgGeneral.add(new BoolSetting.Builder()
        .name("cc-bypass")
        .description("BlackOut's CC bypass: places a block under you before throwing, so the server does not see you fall into the wall.")
        .defaultValue(false)
        .build()
    );

    private final Setting<SwitchMode> ccSwitchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("cc-switch-mode")
        .description("How the CC bypass block gets into your hand.")
        .defaultValue(SwitchMode.Silent)
        .build()
    );

    private final Setting<SwitchMode> switchMode = sgGeneral.add(new EnumSetting.Builder<SwitchMode>()
        .name("switch-mode")
        .description("How the pearl gets into your hand. Normal: only a pearl you already hold. Silent: selects the hotbar slot and swaps back. PickSilent / InvSwitch: swaps one out of the inventory into your slot and swaps it back after the throw.")
        .defaultValue(SwitchMode.Silent)
        .build()
    );

    private final Setting<Integer> pitch = sgGeneral.add(new IntSetting.Builder()
        .name("pitch")
        .description("How deep down to look. 0-90 is the useful range, 85 is BlackOut's default.")
        .defaultValue(85)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> instant = sgGeneral.add(new BoolSetting.Builder()
        .name("instant-rotation")
        .description("Sends the rotation as a look packet and throws on the same tick instead of waiting for the vanilla movement packet.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> keepRotation = sgGeneral.add(new BoolSetting.Builder()
        .name("keep-rotation")
        .description("Keeps looking at the phase spot after throwing instead of restoring your view.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> swing = sgGeneral.add(new BoolSetting.Builder()
        .name("swing")
        .description("Animates your hand when the pearl is thrown.")
        .defaultValue(true)
        .build()
    );

    public enum SwitchMode {
        Normal,
        Silent,
        PickSilent,
        InvSwitch
    }

    private enum Stage {
        Idle,
        PlaceBlock,
        Throw
    }

    private Stage stage = Stage.Idle;
    private float prevYaw;
    private float prevPitch;
    private float throwYaw;
    private float throwPitch;
    private boolean swappedHotbar;
    private boolean pickedFromInventory;
    private int pickedSlot = -1;
    private int pickedHotbarSlot = -1;

    public PearlPhase() {
        super(AddonTemplate.BYPASS_CATEGORY, "pearl-phase",
            "Easily clip inside walls with pearls - full port of BlackOut's Auto Pearl, with CC bypass, switch modes and instant rotation.");
    }

    @Override
    public void onActivate() {
        stage = Stage.Idle;
        swappedHotbar = false;
        pickedFromInventory = false;
        pickedSlot = -1;
        pickedHotbarSlot = -1;

        if (!Utils.canUpdate() || mc.player == null || mc.world == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        if (!canGetPearl()) {
            error(switchMode.get() == SwitchMode.Normal
                ? "Hold an ender pearl (or set switch-mode to Silent)."
                : "No ender pearls in your inventory.");
            toggle();
            return;
        }

        // BlackOut aims at the centre of the block you are standing in, then turns 180 degrees,
        // so the pearl is thrown into the block behind/below you.
        throwYaw = (float) MathHelper.wrapDegrees(
            Rotations.getYaw(new Vec3d(Math.floor(mc.player.getX()) + 0.5, 0, Math.floor(mc.player.getZ()) + 0.5)) + 180.0);
        throwPitch = MathHelper.clamp(pitch.get(), 0, 90);

        if (ccBypass.get()) {
            stage = Stage.PlaceBlock;
            return;
        }

        rotate();
        stage = Stage.Throw;
    }

    @Override
    public void onDeactivate() {
        restore();
        stage = Stage.Idle;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (stage == Stage.Idle) return;

        if (mc.player == null || mc.world == null || mc.interactionManager == null) {
            toggle();
            return;
        }

        if (stage == Stage.PlaceBlock) {
            if (!placeCcBlock()) {
                warning("No block for the CC bypass - throwing without it.");
            }

            rotate();
            stage = Stage.Throw;
            return;
        }

        throwPearl();
    }

    /** Sets the view, and with instant-rotation also tells the server about it right away. */
    private void rotate() {
        prevYaw = mc.player.getYaw();
        prevPitch = mc.player.getPitch();

        mc.player.setYaw(throwYaw);
        mc.player.setPitch(throwPitch);

        if (instant.get()) {
            mc.player.networkHandler.sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(throwYaw, throwPitch, mc.player.isOnGround(), false));
        }
    }

    private void throwPearl() {
        Hand hand = handFor(switchMode.get());
        if (hand == null) {
            error("Could not get a pearl in hand.");
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
        if (swing.get()) mc.player.swingHand(hand);

        restore();
        toggle();
        info("Pearl thrown.");
    }

    /**
     * Puts a pearl in the player's hand (or finds the one already held) according to the switch
     * mode, remembering how to undo it in {@link #restore()}.
     */
    private Hand handFor(SwitchMode mode) {
        Hand held = heldPearlHand();
        if (held != null) return held;
        if (mode == SwitchMode.Normal) return null;

        if (mode == SwitchMode.Silent) {
            FindItemResult pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) return null;

            if (InvUtils.swap(pearl.slot(), true)) {
                swappedHotbar = true;
                return Hand.MAIN_HAND;
            }
            return null;
        }

        // PickSilent / InvSwitch: bring one out of the inventory into the selected slot.
        FindItemResult pearl = InvUtils.find(Items.ENDER_PEARL);
        if (!pearl.found()) {
            pearl = InvUtils.findInHotbar(Items.ENDER_PEARL);
            if (!pearl.found()) return null;

            // Already in the hotbar: selecting it is the same thing for this mode.
            if (!InvUtils.swap(pearl.slot(), true)) return null;
            swappedHotbar = true;
            return Hand.MAIN_HAND;
        }

        int hotbar = mc.player.getInventory().getSelectedSlot();
        InvUtils.quickSwap().fromHotbar(hotbar).to(pearl.slot());

        pickedFromInventory = true;
        pickedSlot = pearl.slot();
        pickedHotbarSlot = hotbar;

        return Hand.MAIN_HAND;
    }

    /** BlackOut's {@code cc()}: one block placed straight down before the throw. */
    private boolean placeCcBlock() {
        boolean hotbarOnly = ccSwitchMode.get() == SwitchMode.Normal || ccSwitchMode.get() == SwitchMode.Silent;

        FindItemResult block = hotbarOnly
            ? InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem)
            : InvUtils.find(stack -> stack.getItem() instanceof BlockItem);

        if (!block.found()) return false;

        int hotbar = mc.player.getInventory().getSelectedSlot();
        boolean picked = block.slot() > 8;

        if (picked) {
            InvUtils.quickSwap().fromHotbar(hotbar).to(block.slot());

            block = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem);
            if (!block.found()) return false;
        }

        BlockPos below = mc.player.getBlockPos().down();
        boolean placed = BlockUtils.place(below, block, true, 50);

        if (picked) {
            FindItemResult inHotbar = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem);
            if (inHotbar.found()) InvUtils.quickSwap().fromHotbar(hotbar).to(block.slot());
        }

        return placed;
    }

    private Hand heldPearlHand() {
        if (mc.player.getMainHandStack().getItem() == Items.ENDER_PEARL) return Hand.MAIN_HAND;
        if (mc.player.getOffHandStack().getItem() == Items.ENDER_PEARL) return Hand.OFF_HAND;
        return null;
    }

    private boolean canGetPearl() {
        if (heldPearlHand() != null) return true;
        if (switchMode.get() == SwitchMode.Normal) return false;
        return InvUtils.findInHotbar(Items.ENDER_PEARL).found() || InvUtils.find(Items.ENDER_PEARL).found();
    }

    private void restore() {
        if (mc.player == null) return;

        if (!keepRotation.get()) {
            mc.player.setYaw(prevYaw);
            mc.player.setPitch(prevPitch);
        }

        if (pickedFromInventory && pickedSlot >= 0 && pickedHotbarSlot >= 0) {
            InvUtils.quickSwap().fromHotbar(pickedHotbarSlot).to(pickedSlot);
        }
        pickedFromInventory = false;

        if (swappedHotbar) {
            InvUtils.swapBack();
            swappedHotbar = false;
        }
    }
}
