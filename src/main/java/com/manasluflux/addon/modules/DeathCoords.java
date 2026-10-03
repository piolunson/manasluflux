package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.commands.Msg;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;

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
        super(AddonTemplate.CATEGORY, "death-coords", "Prints (and optionally copies) your coordinates the moment you die.");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (!(event.packet instanceof ClientboundPlayerCombatKillPacket packet)) return;
        if (mc.player == null || mc.level == null) return;
        if (packet.playerId() != mc.player.getId()) return;

        BlockPos pos = BlockPos.containing(mc.player.position());
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();

        if (includeDimension.get()) {
            String dimension = mc.level.dimension().identifier().toString();
            Msg.info("You died at %s in %s.", coords, dimension);
        } else {
            Msg.info("You died at %s.", coords);
        }

        if (copyToClipboard.get()) mc.keyboardHandler.setClipboard(coords);
    }
}
