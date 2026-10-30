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
 * Rocket-powered elytra flight. The rockets are the thrust: aim with the keys, and a firework is
 * lit in that direction whenever you are slower than the speed you set, so you settle at that
 * speed instead of accelerating forever. Jump climbs, sneak descends, W/A/S/D move, and with no
 * key held the glide is stopped dead and you hover where you are.
 *
 * <p>Fake fly does the same with a chestplate: the hotbar elytra is shown to the server for one
 * start-gliding packet and swapped straight back, so the elytra is never worn and costs no
 * durability (the trick from Stashhunting's {@code ElytraFlyPlusPlus}).
 */
public class FireworkElytraFly extends Module {
    private static final int CHEST_SLOT = 6;
    private static final double TICKS_PER_SECOND = 20.0;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFakeFly = settings.createGroup("Fake Fly");

    private final Setting<Double> horizontalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("horizontal-speed")
        .description("Blocks per second to travel horizontally. The rockets push you; this is the speed they are cut off at.")
        .defaultValue(20.0)
        .range(0.0, 100.0)
        .sliderRange(1.0, 60.0)
        .build()
    );

    private final Setting<Double> verticalSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("vertical-speed")
        .description("Blocks per second to climb or descend with jump / sneak.")
        .defaultValue(8.0)
        .range(0.0, 50.0)
        .sliderRange(1.0, 25.0)
        .build()
    );

    private final Setting<Boolean> hover = sgGeneral.add(new BoolSetting.Builder()
        .name("hover")
        .description("With no movement key held, stop the glide and stay exactly where you are.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> autoTakeoff = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-takeoff")
        .description("Jumps and starts gliding for you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> fireworkDelay = sgGeneral.add(new DoubleSetting.Builder()
        .name("firework-delay")
        .description("Seconds between rockets while you still need speed.")
        .defaultValue(0.8)
        .range(0.2, 10.0)
        .sliderRange(0.2, 5.0)
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
    private Vec3d hoverPos;

    public FireworkElytraFly() {
        super(AddonTemplate.MOVEMENT_CATEGORY, "firework-elytra-fly",
            "Rocket-powered elytra flight: jump climbs, sneak descends, WASD moves, and letting go hovers in place. Fireworks are the thrust.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        sprintBefore = mc.player.isSprinting();
        hoverPos = null;
        cooldown = 0;
        spoofCooldown = 0;
        warnedRocket = false;
        warnedElytra = false;

        if (toggleElytra.get() && !fakeFly.get() && !isElytraEquipped()) chestSwap();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;

        hoverPos = null;
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

        if (!mc.player.isGliding()) {
            if (!autoTakeoff.get()) return;

            // On the ground, only leave it when you ask with jump - no bunny-hopping while idle.
            if (mc.player.isOnGround()) {
                if (mc.options.jumpKey.isPressed()) mc.player.jump();
            } else if (fakeFly.get()) spoofStartGliding();
            else startGliding();

            return;
        }

        fly();
    }

    private void fly() {
        Vec3d direction = inputDirection();

        // No key held: park in the air. Zeroing the velocity alone lets gravity sink you a little
        // every tick, so the position is held as well.
        if (direction.lengthSquared() < 1.0E-4) {
            if (hover.get()) {
                if (hoverPos == null) hoverPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());

                mc.player.setVelocity(Vec3d.ZERO);
                mc.player.setPosition(hoverPos.x, hoverPos.y, hoverPos.z);
            }

            return;
        }

        hoverPos = null;
        aim(direction);

        double maxHorizontal = horizontalSpeed.get() / TICKS_PER_SECOND;
        double maxVertical = verticalSpeed.get() / TICKS_PER_SECOND;

        Vec3d velocity = mc.player.getVelocity();
        double horizontal = Math.hypot(velocity.x, velocity.z);

        // Rockets are the accelerator: light one only while the speed is still short of the target.
        if (cooldown == 0) {
            boolean horizontalSlow = horizontal < maxHorizontal * 0.9;
            boolean verticalSlow = Math.abs(velocity.y) < maxVertical * 0.9;
            boolean wantsHorizontal = direction.x != 0 || direction.z != 0;
            boolean wantsVertical = direction.y != 0;

            if ((wantsHorizontal && horizontalSlow) || (wantsVertical && verticalSlow)) {
                if (fireRocket()) cooldown = (int) Math.max(1, Math.round(fireworkDelay.get() * TICKS_PER_SECOND));
            }
        }

        // ...and the speed you set is the ceiling, so a rocket cannot run away with you.
        velocity = mc.player.getVelocity();
        double newHorizontal = Math.hypot(velocity.x, velocity.z);
        double x = velocity.x;
        double z = velocity.z;
        double y = velocity.y;

        if (maxHorizontal <= 0) {
            x = 0;
            z = 0;
        } else if (newHorizontal > maxHorizontal) {
            double scale = maxHorizontal / newHorizontal;
            x *= scale;
            z *= scale;
        }

        if (direction.y == 0) {
            // Level flight: let the glide keep its own sink, but never faster than the vertical setting.
            if (Math.abs(y) > maxVertical) y = Math.signum(y) * maxVertical;
        } else if (Math.abs(y) > maxVertical) {
            y = Math.signum(y) * maxVertical;
        }

        mc.player.setVelocity(new Vec3d(x, y, z));
    }

    /** WASD + jump/sneak as a world-space direction, horizontal part normalised. */
    private Vec3d inputDirection() {
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double strafe = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
        double up = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

        double yaw = Math.toRadians(mc.player.getYaw());
        Vec3d look = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));

        double x = look.x * forward + right.x * strafe;
        double z = look.z * forward + right.z * strafe;

        double length = Math.hypot(x, z);
        if (length > 1.0E-4) {
            x /= length;
            z /= length;
        } else {
            x = 0;
            z = 0;
        }

        if (x == 0 && z == 0 && up == 0) return Vec3d.ZERO;

        return new Vec3d(x, up, z);
    }

    private void aim(Vec3d direction) {
        double horizontal = Math.hypot(direction.x, direction.z);

        if (horizontal > 1.0E-4) mc.player.setYaw((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));

        if (direction.y != 0) {
            double pitch = horizontal > 1.0E-4
                ? -Math.toDegrees(Math.atan2(direction.y, horizontal))
                : Math.copySign(80.0, -direction.y);
            mc.player.setPitch((float) pitch);
        } else if (horizontal > 1.0E-4) {
            mc.player.setPitch(0.0f);
        }
    }

    private boolean fireRocket() {
        FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);

        if (!rocket.found()) {
            if (!warnedRocket) {
                info("No firework rocket in the hotbar - out of thrust.");
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

    private void startGliding() {
        if (!isElytraEquipped()) return;

        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
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
