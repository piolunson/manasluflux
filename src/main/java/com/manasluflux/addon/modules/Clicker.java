package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/**
 * Port of RyanWare's Clicker (SmilerRyan): simple left/right auto clicker with Nothing,
 * Hold and Click modes, driven by tick delays. Rewritten for MC 26.2.
 */
public class Clicker extends Module {
    public enum Mode {
        NOTHING,
        HOLD,
        CLICK
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> leftMode = sg.add(new EnumSetting.Builder<Mode>()
        .name("left")
        .description("What to do with the left (attack) mouse button.")
        .defaultValue(Mode.NOTHING)
        .build()
    );

    private final Setting<Integer> leftDelay = sg.add(new IntSetting.Builder()
        .name("left-delay-ticks")
        .description("Ticks between left clicks in CLICK mode.")
        .defaultValue(5)
        .min(1)
        .sliderMax(40)
        .visible(() -> leftMode.get() == Mode.CLICK)
        .build()
    );

    private final Setting<Mode> rightMode = sg.add(new EnumSetting.Builder<Mode>()
        .name("right")
        .description("What to do with the right (use) mouse button.")
        .defaultValue(Mode.NOTHING)
        .build()
    );

    private final Setting<Integer> rightDelay = sg.add(new IntSetting.Builder()
        .name("right-delay-ticks")
        .description("Ticks between right clicks in CLICK mode.")
        .defaultValue(5)
        .min(1)
        .sliderMax(40)
        .visible(() -> rightMode.get() == Mode.CLICK)
        .build()
    );

    private int leftTicks;
    private int rightTicks;

    public Clicker() {
        super(AddonTemplate.REWRITE_CATEGORY, "clicker", "Simple left/right auto clicker using tick delays.");
    }

    @Override
    public void onDeactivate() {
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        leftTicks = 0;
        rightTicks = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        switch (leftMode.get()) {
            case NOTHING -> mc.options.keyAttack.setDown(false);
            case HOLD -> mc.options.keyAttack.setDown(true);
            case CLICK -> {
                mc.options.keyAttack.setDown(false);
                if (leftTicks-- <= 0) {
                    mc.options.keyAttack.setDown(true);
                    leftTicks = leftDelay.get();
                }
            }
        }

        switch (rightMode.get()) {
            case NOTHING -> mc.options.keyUse.setDown(false);
            case HOLD -> mc.options.keyUse.setDown(true);
            case CLICK -> {
                mc.options.keyUse.setDown(false);
                if (rightTicks-- <= 0) {
                    mc.options.keyUse.setDown(true);
                    rightTicks = rightDelay.get();
                }
            }
        }
    }
}
