package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.commands.Msg;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.network.packet.s2c.play.DeathMessageS2CPacket;

public class DeathCoords extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> copyToClipboard = sgGeneral.add(new BoolSetting.Builder()
        .name("copy-to-clipboard")
        .description("Copies the coordinates to your clipboard when you die.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> includeDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("include-dimension")
        .description("Includes the dimension name in the message.")
        .defaultValue(true)
        .build()
    );

    public DeathCoords() {
        super(AddonTemplate.UTILITY_CATEGORY, "death-coords", "Prints (and optionally copies) your coordinates the moment you die.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof DeathMessageS2CPacket packet)) return;
        if (mc.player == null || mc.world == null) return;
        if (packet.playerId() != mc.player.getId()) return;

        BlockPos pos = BlockPos.ofFloored(mc.player.getEntityPos());
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();

        if (includeDimension.get()) {
            String dimension = mc.world.getRegistryKey().getValue().toString();
            Msg.info("You died at %s in %s.", coords, dimension);
        } else {
            Msg.info("You died at %s.", coords);
        }

        if (copyToClipboard.get()) mc.keyboard.setClipboard(coords);
    }
}
