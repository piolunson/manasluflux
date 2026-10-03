package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public class ClientSideNightVision extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> hideIcon = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-icon")
        .description("Hides the effect icon from your screen, just like a real invisible effect.")
        .defaultValue(true)
        .build()
    );

    // Refreshed well above 400 ticks so the vanilla night-vision fade blink never triggers.
    private static final int DURATION_TICKS = 500;
    private static final int REFRESH_TICKS = 50;

    private int timer;

    public ClientSideNightVision() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "client-side-night-vision", "Gives you the night vision potion effect client-side only - the server never sees the effect.");
    }

    private void apply() {
        if (mc.player == null) return;
        mc.player.addEffect(new MobEffectInstance(
            MobEffects.NIGHT_VISION,
            DURATION_TICKS,
            0,          // amplifier
            true,      // ambient
            !hideIcon.get(), // visible particles
            !hideIcon.get()  // show icon in HUD
        ));
        timer = 0;
    }

    @Override
    public void onActivate() {
        apply();
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) mc.player.removeEffect(MobEffects.NIGHT_VISION);
        timer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Re-apply periodically and after world changes (also covers joining a world while enabled).
        if (++timer >= REFRESH_TICKS || mc.player.getEffect(MobEffects.NIGHT_VISION) == null) {
            apply();
        }
    }
}
