package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Abilities;

/**
 * Keeps flight enabled every tick, with a vanilla flying speed setting.
 * No movement code - just mayfly/flying switched on, like holding the creative
 * flight switch down. Works wherever flight is allowed (creative, servers with
 * /fly, singleplayer).
 */
public class UniversalFlight extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> flyingSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("flying-speed")
        .description("Vanilla creative flight speed (vanilla default 0.05). Applies to all directions.")
        .defaultValue(0.05)
        .range(0.01, 1.0)
        .sliderRange(0.01, 0.3)
        .build()
    );

    private final Setting<Boolean> alwaysFly = sgGeneral.add(new BoolSetting.Builder()
        .name("always-fly")
        .description("ON = flight is forced on all the time. OFF = you only get the flight permission and can toggle flying with a double-jump, exactly like creative mode.")
        .defaultValue(true)
        .build()
    );

    private boolean savedMayFly;
    private boolean savedFlying;
    private float savedFlyingSpeed;

    public UniversalFlight() {
        super(AddonTemplate.CATEGORY, "universal-flight", "Enables flight - force it on all the time, or only get the permission and toggle it with a double-jump like creative.");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;
        Abilities abilities = mc.player.getAbilities();
        savedMayFly = abilities.mayfly;
        savedFlying = abilities.flying;
        savedFlyingSpeed = abilities.getFlyingSpeed();
    }

    @Override
    public void onDeactivate() {
        if (mc.player == null) return;
        Abilities abilities = mc.player.getAbilities();
        abilities.mayfly = savedMayFly;
        abilities.flying = savedFlying && savedMayFly;
        abilities.setFlyingSpeed(savedFlyingSpeed);
        mc.player.onUpdateAbilities();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        Abilities abilities = mc.player.getAbilities();
        float speed = flyingSpeed.get().floatValue();

        if (alwaysFly.get()) {
            // Force the vanilla creative flight switch on every tick.
            if (abilities.mayfly && abilities.flying && abilities.getFlyingSpeed() == speed) {
                return; // already on - don't spam packets
            }

            abilities.mayfly = true;
            abilities.flying = true;
            abilities.setFlyingSpeed(speed);
            mc.player.onUpdateAbilities();
        } else {
            // Only guarantee the flight permission and speed, and leave "flying" alone.
            // Vanilla's double-jump toggle then turns flight on and off, like in creative.
            boolean changed = false;
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                changed = true;
            }
            if (abilities.getFlyingSpeed() != speed) {
                abilities.setFlyingSpeed(speed);
                changed = true;
            }
            if (changed) mc.player.onUpdateAbilities();
        }
    }
}
