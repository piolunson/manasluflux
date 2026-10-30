package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;

import java.util.Set;

/**
 * Stops the server from turning you.
 *
 * <p>Rotations arrive inside {@code PlayerPositionLookS2CPacket} - the same packet anticheats use
 * for setbacks, and the one servers use to snap your view after a teleport, a web escape or a
 * region change. The client applies that rotation and then <b>acknowledges it</b>, so simply having
 * your camera put back is not enough: the server already believes you are facing the way it told you.
 *
 * <p>This module replaces the packet before the client ever handles it: the position and velocity
 * are kept exactly as sent, the yaw and pitch are swapped for the ones you are actually looking at,
 * and the rebuilt packet goes to the normal handler - so the acknowledgement that comes back out
 * carries <b>your</b> rotation, not the forced one. Relative rotation flags are handled too (they
 * carry a delta, so the substituted value is 0 rather than your absolute angle).
 *
 * <p>{@code ignore-teleports} (default 1 block, 0 = never) leaves rotations alone when the packet
 * also moved you further than that. A big move is a real teleport - a lagback, a pearl, a region
 * change - and arguing about the rotation there usually earns a second setback.
 *
 * <p>Yaw and pitch are separate toggles. Meteor's built-in {@code no-rotate} does the same job for
 * both axes with no options, so this is the version with per-axis control and the teleport escape.
 */
public class NoForceRotation extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> yaw = sgGeneral.add(new BoolSetting.Builder()
        .name("yaw")
        .description("Keep your yaw when the server tries to turn you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> pitch = sgGeneral.add(new BoolSetting.Builder()
        .name("pitch")
        .description("Keep your pitch when the server tries to tilt you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> ignoreTeleports = sgGeneral.add(new DoubleSetting.Builder()
        .name("ignore-teleports")
        .description("Leave the rotation alone when the packet also moved you further than this, in blocks - a real teleport, not a forced turn. 0 = never ignore.")
        .defaultValue(1.0)
        .range(0.0, 64.0)
        .sliderRange(0.0, 8.0)
        .build()
    );

    private int blocked;
    private int skipped;

    public NoForceRotation() {
        super(AddonTemplate.PLAYER_CATEGORY, "no-force-rotation",
            "Prevents the server from forcing your view: the rotation is rewritten before the client applies it, so the acknowledgement carries your angle.");
    }

    @Override
    public void onActivate() {
        blocked = 0;
        skipped = 0;
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null) return;
        if (!(event.packet instanceof PlayerPositionLookS2CPacket packet)) return;

        double moved = packet.change().position().distanceTo(mc.player.getEntityPos());
        double limit = ignoreTeleports.get();

        if (limit > 0 && moved > limit) {
            skipped++;
            return;
        }

        Set<PositionFlag> flags = packet.relatives();

        // A relative rotation carries a delta, so the value that keeps our angle is 0, not our absolute.
        float newYaw = yaw.get()
            ? (flags.contains(PositionFlag.Y_ROT) ? 0.0f : mc.player.getYaw())
            : packet.change().yaw();
        float newPitch = pitch.get()
            ? (flags.contains(PositionFlag.X_ROT) ? 0.0f : mc.player.getPitch())
            : packet.change().pitch();

        if (newYaw == packet.change().yaw() && newPitch == packet.change().pitch()) return;

        event.cancel();

        PlayerPositionLookS2CPacket replaced = new PlayerPositionLookS2CPacket(
            packet.teleportId(), packet.change().withRotation(newYaw, newPitch), flags);

        if (mc.getNetworkHandler() != null) mc.getNetworkHandler().onPlayerPositionLook(replaced);

        blocked++;
    }

    @Override
    public String getInfoString() {
        return blocked + (skipped > 0 ? " / " + skipped + " tp" : "");
    }
}
