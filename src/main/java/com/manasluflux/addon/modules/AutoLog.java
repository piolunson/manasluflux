package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.DisconnectionInfo;
import net.minecraft.text.Text;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;

public class AutoLog extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> threshold = sgGeneral.add(new IntSetting.Builder()
        .name("health-threshold")
        .description("Disconnect when health drops below this percentage.")
        .defaultValue(10)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    public AutoLog() {
        super(AddonTemplate.PLAYER_CATEGORY, "auto-log", "Automatically disconnects when your health drops below a threshold.");
    }

    public void setThreshold(int percent) {
        this.threshold.set(percent);
    }

    public int getThreshold() {
        return threshold.get();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        float percent = mc.player.getHealth() / mc.player.getMaxHealth() * 100f;
        if (percent < threshold.get()) {
            warning("Health dropped below %d%% - disconnecting!", threshold.get());
            toggle();
            mc.getNetworkHandler().onDisconnect(new DisconnectS2CPacket(
                Text.literal("[ManasluFlux] AutoLog: health below " + threshold.get() + "%")
            ));
        }
    }
}
