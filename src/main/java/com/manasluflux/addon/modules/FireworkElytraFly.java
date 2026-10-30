package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.player.ChestSwap;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Elytra flight you fly with fireworks: it keeps the glide going by boosting only when you are
 * slower than the speed you set, and with fake fly it does that while you are wearing a chestplate
 * - the hotbar elytra is swapped into the server's chest slot for the start-gliding packet and
 * straight back out, so the elytra is never actually worn and loses no durability.
 *
 * <p>The fake-fly swap follows the trick used by Stashhunting's {@code ElytraFlyPlusPlus}
 * (miles352, MIT) and the Grim elytra fly the same idea comes from.
 */
public class FireworkElytraFly extends Module {
    private static final int CHEST_SLOT = 6;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFakeFly = settings.createGroup("Fake Fly");

    private final Setting<Boolean> autoTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-takeoff")
        .description("Jumps for you and starts gliding, so a rocket can be used right away.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoFireworks = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-fireworks")
        .description("Uses a firework rocket whenever you are slower than the target speed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fireworkDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("Seconds between two rockets.")
        .defaultValue(1.5)
        .range(0.2, 10.0)
        .sliderRange(0.2, 5.0)
        .visible(autoFireworks::get)
        .build()
    );

    private final Setting<Double> targetSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-speed")
        .description("Blocks per second to stay at. No rocket is used while you are already going faster.")
        .defaultValue(35.0)
        .range(0.0, 250.0)
        .sliderRange(10.0, 120.0)
        .visible(autoFireworks::get)
        .build()
    );

    private final Setting<Boolean> lockPitch = sgGeneral.add(new BoolSetting.Builder()
        .name("lock-pitch")
        .description("Holds your pitch while gliding, so a rocket does not send you into a climb or a dive.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch")
        .description("The pitch to hold. 0 is level flight.")
        .defaultValue(0.0)
        .range(-90.0, 90.0)
        .sliderRange(-90.0, 90.0)
        .visible(lockPitch::get)
        .build()
    );

    private final Setting<Boolean> stopInWater = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-in-water")
        .description("Does nothing while you are in water.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fakeFly = sgFakeFly.add(new BoolSetting.Builder()
        .name("fake-fly")
        .description("Fly with a chestplate: the hotbar elytra is shown to the server only for the start-gliding packet, so the elytra takes no durability.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> toggleElytra = sgFakeFly.add(new BoolSetting.Builder()
        .name("toggle-elytra")
        .description("Puts the elytra on when the module is enabled, and the chestplate back on when it is disabled.")
        .defaultValue(false)
        .visible(() -> !fakeFly.get())
        .build()
    );

    private int cooldown;
    private int spoofCooldown;

    private boolean warnedRocket;
    private boolean warnedElytra;

    private boolean sprintBefore;

    public FireworkElytraFly() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "firework-elytra-fly",
            "Elytra flight flown with fireworks: it boosts only while you are slower than the speed you set, and fake fly lets you do it with a chestplate on.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        sprintBefore = mc.player.isSprinting();
        cooldown = 0;
        spoofCooldown = 0;
        warnedRocket = false;
        warnedElytra = false;

        if (toggleElytra.get() && !fakeFly.get() && !isElytraEquipped()) chestSwap();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        mc.player.setSprinting(sprintBefore);

        if (toggleElytra.get() && !fakeFly.get() && isElytraEquipped()) chestSwap();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;
        if (mc.player.getAbilities().flying) return;

        if (cooldown > 0) cooldown--;
        if (spoofCooldown > 0) spoofCooldown--;

        if (stopInWater.get() && mc.player.isTouchingWater()) return;

        if (toggleElytra.get() && !fakeFly.get() && !isElytraEquipped()) {
            chestSwap();
            return;
        }

        if (fakeFly.get()) {
            if (mc.player.isOnGround()) {
                if (autoTakeoff.get()) mc.player.jump();
            } else if (!mc.player.isGliding() && spoofCooldown == 0) {
                spoofStartGliding();
            }
        } else {
            if (!isElytraEquipped()) return;

            if (!mc.player.isGliding() && autoTakeoff.get() && !mc.player.isOnGround()) {
                mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }

        if (lockPitch.get() && mc.player.isGliding()) mc.player.setPitch(pitch.get().floatValue());

        if (!autoFireworks.get() || !mc.player.isGliding() || cooldown > 0) return;

        Vec3d velocity = mc.player.getVelocity();
        double blocksPerSecond = Math.hypot(velocity.x, velocity.z) * 20.0;

        if (blocksPerSecond >= targetSpeed.get()) return;
        if (useFirework()) cooldown = (int) Math.round(fireworkDelay.get() * 20.0);
    }

    private boolean useFirework() {
        FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);

        if (!rocket.found()) {
            if (!warnedRocket) {
                info("No firework rocket in the hotbar.");
                warnedRocket = true;
            }

            return false;
        }

        warnedRocket = false;

        if (!InvUtils.swap(rocket.slot(), true)) return false;

        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);

        InvUtils.swapBack();
        return true;
    }

    /**
     * Shows the server an elytra in the chest slot for exactly one start-gliding packet, then puts
     * the chestplate back. Nothing is predicted client-side, so your own inventory never changes.
     */
    private void spoofStartGliding() {
        FindItemResult elytra = InvUtils.findInHotbar(Items.ELYTRA);

        if (!elytra.found()) {
            if (!warnedElytra) {
                info("Fake fly needs an elytra in the hotbar.");
                warnedElytra = true;
            }

            return;
        }

        warnedElytra = false;

        if (!swapChestWithHotbar(elytra.slot())) return;

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));

        swapChestWithHotbar(elytra.slot());

        spoofCooldown = 5;
    }

    private boolean swapChestWithHotbar(int hotbarSlot) {
        ScreenHandler handler = mc.player.currentScreenHandler;

        if (handler != mc.player.playerScreenHandler) return false;

        // No client-side prediction: the changed-slot map is left empty like every other raw
        // click this addon sends, so the server applies the swap and our own inventory stays as is.
        mc.player.networkHandler.sendPacket(new ClickSlotC2SPacket(
            handler.syncId,
            handler.nextRevision(),
            (short) CHEST_SLOT,
            (byte) hotbarSlot,
            SlotActionType.SWAP,
            Int2ObjectMaps.emptyMap(),
            ItemStackHash.EMPTY
        ));

        return true;
    }

    private void chestSwap() {
        ChestSwap chestSwap = Modules.get().get(ChestSwap.class);
        if (chestSwap != null) chestSwap.swap();
    }

    private boolean isElytraEquipped() {
        return mc.player.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA);
    }
}
