package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.screen.sync.ItemStackHash;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Rocket-fed elytra flight whose rotation exists only on the wire. The rotation the flight needs -
 * the way you steer with W/A/S/D, straight up for jump, straight down for sneak - is never written
 * to the player: outbound movement packets are rewritten with it as they leave, so the server gets
 * exactly that rotation on every movement packet while your own entity is untouched for every
 * single frame. Your view is a plain, ordinary first-person camera - nothing to snap back, nothing
 * to interpolate across, no look packets of our own.
 *
 * <p>The movement itself is a velocity controller, so the keys stay relative to where you are
 * looking and the sideways keys cannot spin you, and releasing everything stops the glide and
 * holds your position. Rockets are consumed as the thrust while you are short of the speed set.
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
        .description("Blocks per second to travel horizontally.")
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

    private final Setting<Double> responsiveness = sgGeneral.add(new DoubleSetting.Builder()
        .name("responsiveness")
        .description("How quickly the speed moves toward the target each tick. Higher is snappier, lower glides into it.")
        .defaultValue(0.35)
        .range(0.05, 1.0)
        .sliderRange(0.05, 1.0)
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

    private final Setting<Boolean> pauseOnEat = sgGeneral.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Lets go while you are eating or drinking: no rotation, no rockets and no speed control, so you can eat mid-flight.")
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
            "Rocket-fed elytra flight: jump climbs straight up, sneak descends straight down, WASD steers, letting go hovers. The rotation goes to the server only, your first-person view never turns.");
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

    /**
     * The aim goes on the movement packets themselves: the packet vanilla built is dropped and an
     * identical one carrying the flight rotation is sent in its place. The player entity is never
     * written, so the client is exactly as it would be with the module off.
     */
    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (!(event.packet instanceof PlayerMoveC2SPacket packet)) return;
        if (mc.player == null || mc.world == null || !Utils.canUpdate() || mc.getNetworkHandler() == null) return;
        if (pauseOnEat.get() && isEating()) return;
        if (!mc.player.isGliding() || mc.player.getAbilities().flying) return;
        if (stopInWater.get() && mc.player.isTouchingWater()) return;

        Vec3d direction = inputDirection(mc.player.getYaw());
        if (direction.lengthSquared() < 1.0E-4) return;

        PlayerMoveC2SPacket aimed = aim(packet, direction);
        if (aimed == null) return;

        event.cancel();
        event.sendSilently(aimed);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !Utils.canUpdate()) return;
        if (mc.player.getAbilities().flying) return;

        if (cooldown > 0) cooldown--;
        if (spoofCooldown > 0) spoofCooldown--;

        // Eating takes priority: no speed control and no hover while the item is in use.
        if (pauseOnEat.get() && isEating()) return;

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
        Vec3d direction = inputDirection(mc.player.getYaw());

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

        double maxHorizontal = horizontalSpeed.get() / TICKS_PER_SECOND;
        double maxVertical = verticalSpeed.get() / TICKS_PER_SECOND;
        boolean straightVertical = Math.hypot(direction.x, direction.z) < 1.0E-4;

        Vec3d velocity = mc.player.getVelocity();

        // Rockets are the thrust: light one while the speed is still short of the setting. The
        // server's copy of your rotation is the flight aim, so the boost follows your steering.
        if (cooldown == 0) fireThrust(direction);

        velocity = mc.player.getVelocity();

        double targetX = direction.x * maxHorizontal;
        double targetY = direction.y * maxVertical;
        double targetZ = direction.z * maxHorizontal;

        double factor = responsiveness.get();

        double x = velocity.x + (targetX - velocity.x) * factor;
        double y = velocity.y + (targetY - velocity.y) * factor;
        double z = velocity.z + (targetZ - velocity.z) * factor;

        if (straightVertical) {
            // Climbing or diving is straight up or down: no sideways drift is left in.
            x = 0;
            z = 0;
        }

        // The speed you set is the ceiling, whatever a rocket added.
        double horizontal = Math.hypot(x, z);
        if (maxHorizontal <= 0) {
            x = 0;
            z = 0;
        } else if (horizontal > maxHorizontal) {
            double scale = maxHorizontal / horizontal;
            x *= scale;
            z *= scale;
        }

        if (Math.abs(y) > maxVertical) y = Math.signum(y) * maxVertical;

        mc.player.setVelocity(new Vec3d(x, y, z));
    }

    /** Rockets are the thrust: light one while the speed is still short of the setting. */
    private void fireThrust(Vec3d direction) {
        double maxHorizontal = horizontalSpeed.get() / TICKS_PER_SECOND;
        double maxVertical = verticalSpeed.get() / TICKS_PER_SECOND;
        boolean straightVertical = Math.hypot(direction.x, direction.z) < 1.0E-4;

        Vec3d velocity = mc.player.getVelocity();
        boolean horizontalSlow = Math.hypot(velocity.x, velocity.z) < maxHorizontal * 0.9;
        boolean verticalSlow = Math.abs(velocity.y) < maxVertical * 0.9;

        if ((!straightVertical && horizontalSlow) || (direction.y != 0 && verticalSlow)) {
            if (fireRocket()) cooldown = (int) Math.max(1, Math.round(fireworkDelay.get() * TICKS_PER_SECOND));
        }
    }

    /** Food or a potion actively being used - the cases where the module has to get out of the way. */
    private boolean isEating() {
        if (!mc.player.isUsingItem()) return false;

        ItemStack active = mc.player.getActiveItem();
        return active.contains(DataComponentTypes.FOOD) || active.contains(DataComponentTypes.POTION_CONTENTS);
    }

    /** WASD + jump/sneak as a world-space direction, horizontal part normalised, from a given yaw. */
    private Vec3d inputDirection(float baseYaw) {
        double forward = (mc.options.forwardKey.isPressed() ? 1 : 0) - (mc.options.backKey.isPressed() ? 1 : 0);
        double strafe = (mc.options.rightKey.isPressed() ? 1 : 0) - (mc.options.leftKey.isPressed() ? 1 : 0);
        double up = (mc.options.jumpKey.isPressed() ? 1 : 0) - (mc.options.sneakKey.isPressed() ? 1 : 0);

        double yaw = Math.toRadians(baseYaw);
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

    /**
     * The same movement packet, carrying the flight rotation: your steering direction and, when you
     * climb or dive, 90 degrees up or down. Position, on-ground and collision flags are copied as
     * they were, so nothing about where you are changes.
     *
     * @return the replacement packet, or null when the packet carries no rotation to re-aim.
     */
    private PlayerMoveC2SPacket aim(PlayerMoveC2SPacket packet, Vec3d direction) {
        double horizontal = Math.hypot(direction.x, direction.z);

        float yaw = horizontal > 1.0E-4
            ? (float) Math.toDegrees(Math.atan2(-direction.x, direction.z))
            : mc.player.getYaw();

        float pitch;
        if (direction.y != 0) {
            pitch = horizontal > 1.0E-4
                ? (float) -Math.toDegrees(Math.atan2(direction.y, horizontal))
                : (float) Math.copySign(90.0, -direction.y);
        } else {
            pitch = 0.0f;
        }

        if (packet instanceof PlayerMoveC2SPacket.LookAndOnGround look) {
            return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, look.isOnGround(), look.horizontalCollision());
        }

        if (packet instanceof PlayerMoveC2SPacket.Full full) {
            return new PlayerMoveC2SPacket.Full(
                full.getX(mc.player.getX()), full.getY(mc.player.getY()), full.getZ(mc.player.getZ()),
                yaw, pitch, full.isOnGround(), full.horizontalCollision());
        }

        if (packet instanceof PlayerMoveC2SPacket.PositionAndOnGround position) {
            return new PlayerMoveC2SPacket.Full(
                position.getX(mc.player.getX()), position.getY(mc.player.getY()), position.getZ(mc.player.getZ()),
                yaw, pitch, position.isOnGround(), position.horizontalCollision());
        }

        return null;
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
