package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.MathHelper;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Attacks the nearest target without rotating your camera - raw attack packets with a silent
 * swing, plus a silent server-side switch to your best sword/axe, so your view, hotbar and
 * held item stay exactly where they are.
 */
public class SilentAura extends Module {
    private static final Item[] SWORDS = {
        Items.NETHERITE_SWORD, Items.DIAMOND_SWORD, Items.IRON_SWORD,
        Items.GOLDEN_SWORD, Items.STONE_SWORD, Items.WOODEN_SWORD
    };

    private static final Item[] AXES = {
        Items.NETHERITE_AXE, Items.DIAMOND_AXE, Items.IRON_AXE,
        Items.GOLDEN_AXE, Items.STONE_AXE, Items.WOODEN_AXE
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoWeapon = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-weapon")
        .description("Silently switches to your best sword/axe (server-side only) before attacking and switches back after.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to the target. Vanilla servers only accept hits within ~3 blocks.")
        .defaultValue(3.0)
        .range(1.0, 6.0)
        .sliderRange(1.0, 4.5)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Ticks between attacks. 10 = 2 hits per second.")
        .defaultValue(10)
        .range(0, 40)
        .sliderRange(0, 40)
        .build()
    );

    private final Setting<Boolean> silentAim = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-aim")
        .description("Looks at the target server-side only - your camera never moves.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> aimHead = sgGeneral.add(new BoolSetting.Builder()
        .name("aim-at-head")
        .description("Aims at the target's head instead of their body.")
        .defaultValue(false)
        .visible(silentAim::get)
        .build()
    );

    private final SettingGroup sgPause = settings.createGroup("Pause");

    private final Setting<Boolean> pauseOnMine = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-mine")
        .description("Pauses while you are mining a block.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pauseOnEat = sgPause.add(new BoolSetting.Builder()
        .name("pause-on-eat")
        .description("Pauses while you are eating or drinking.")
        .defaultValue(true)
        .build()
    );

    private int timer;

    public SilentAura() {
        super(AddonTemplate.COMBAT_CATEGORY, "silent-aura", "Attacks the nearest player without rotating or swinging your visible hand - silent aim plus a silent weapon switch.");
    }

    @Override
    public void onActivate() {
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (pauseOnMine.get() && mc.interactionManager.isBreakingBlock()) return;
        if (pauseOnEat.get() && isEatingOrDrinking()) return;
        if (timer > 0) {
            timer--;
            return;
        }

        PlayerEntity target = CombatTargets.findTarget(range.get());
        if (target == null) return;

        attack(target);

        timer = delay.get();
    }

    private void attack(PlayerEntity target) {
        int realSlot = mc.player.getInventory().getSelectedSlot();
        int weaponSlot = autoWeapon.get() ? findWeaponSlot() : -1;

        boolean swap = weaponSlot != -1 && weaponSlot != realSlot;
        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(weaponSlot));

        if (silentAim.get()) lookAtTarget(target);

        mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.attack(target, false));
        mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));

        if (swap) mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(realSlot));
    }

    /**
     * Silent aim: sends a look rotation packet at the target just before the attack so the
     * server sees us facing them, without touching the client-side camera. Server-side
     * rotation resets on the next vanilla movement packet, exactly like BlackOut-style
     * "instant rotation".
     */
    private void lookAtTarget(PlayerEntity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d aim = aimHead.get()
            ? new Vec3d(target.getX(), target.getEyeY(), target.getZ())
            : new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());

        Vec3d delta = aim.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) MathHelper.wrapDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) -MathHelper.wrapDegrees(Math.atan2(delta.y, horizontal));

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), mc.player.isOnGround()));
    }

    /**
     * Best weapon in the hotbar: swords beat axes, better tiers beat worse ones.
     */
    private int findWeaponSlot() {
        int bestSlot = -1;
        int bestScore = 0;

        for (int slot = 0; slot < 9; slot++) {
            Item item = mc.player.getInventory().getStack(slot).getItem();
            int score = score(item);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }

        return bestSlot;
    }

    private static int score(Item item) {
        int type = 0;
        int tier = 0;

        for (int i = 0; i < SWORDS.length; i++) {
            if (item == SWORDS[i]) {
                type = 100;
                tier = SWORDS.length - i;
                break;
            }
        }

        if (type == 0) {
            for (int i = 0; i < AXES.length; i++) {
                if (item == AXES[i]) {
                    type = 50;
                    tier = AXES.length - i;
                    break;
                }
            }
        }

        return type + tier;
    }

    private boolean isEatingOrDrinking() {
        if (!mc.player.isUsingItem()) return false;
        UseAction anim = mc.player.getActiveItem().getUseAction();
        return anim == UseAction.EAT || anim == UseAction.DRINK;
    }
}
