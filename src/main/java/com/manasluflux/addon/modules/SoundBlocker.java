package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.SoundEventListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

/**
 * Port of RyanWare's SoundBlocker (SmilerRyan): mutes the sounds you pick from the sound
 * list client-side, so spammy farms or annoying effects stay silent. Rewritten for MC 26.2.
 */
public class SoundBlocker extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<SoundEvent>> sounds = sgGeneral.add(new SoundEventListSetting.Builder()
        .name("sounds")
        .description("Sounds to block.")
        .build()
    );

    public SoundBlocker() {
        super(AddonTemplate.REWRITE_CATEGORY, "sound-muter", "Blocks specific sounds from playing on your client.");
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        for (SoundEvent sound : sounds.get()) {
            if (event.sound.getIdentifier().equals(sound.location())) {
                event.cancel();
                return;
            }
        }
    }
}
