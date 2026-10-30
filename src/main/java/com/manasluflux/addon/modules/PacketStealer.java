package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPositionSyncS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityTrackerUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Records everything the server tells your client about one player, with timestamps, into
 * {@code .minecraft/manasluflux/packetstealer/<their name>.json}.
 *
 * <p><b>What this is, and what it is not:</b> a vanilla client never receives another player's
 * packets - the server sends you your own connection's packets, and other players only show up as
 * <i>effects</i> of what they did: their movement and velocity, their swings, what they are holding,
 * when their totem pops, when they take damage. That traffic is what gets logged. It is everything
 * obtainable client-side about that player, and nothing more: you cannot read their chat, their
 * inventory or their private packets, because those never reach your machine.
 *
 * <p>Each line is one JSON object, so the file stays readable even if the game crashes mid-session:
 * <pre>{"time":"2026-10-30T09:12:33.123Z","event":"animation","detail":"swing main hand"}</pre>
 *
 * <p>Movement is the bulk of a player's traffic (about 20 lines a second), so it is off by default;
 * animations, totem pops, hits and equipment changes are on.
 */
public class PacketStealer extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLog = settings.createGroup("Log");

    private final Setting<String> target = sgGeneral.add(new StringSetting.Builder()
        .name("target")
        .description("Player name to log. Leave empty to pick the nearest player when the module is switched on.")
        .defaultValue("")
        .wide()
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("How close the nearest player has to be when target is empty.")
        .defaultValue(32.0)
        .range(0.0, 10000.0)
        .sliderRange(0.0, 64.0)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Say which player and file it is logging to.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> movement = sgLog.add(new BoolSetting.Builder()
        .name("movement")
        .description("Log movement and velocity. Very noisy - about 20 lines per second per player.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> equipment = sgLog.add(new BoolSetting.Builder()
        .name("equipment")
        .description("Log equipment changes - what they are holding, and when it changes.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> dataTracker = sgLog.add(new BoolSetting.Builder()
        .name("data-tracker")
        .description("Log entity data-tracker updates (health, pose, item in use). Noisy and raw.")
        .defaultValue(false)
        .build()
    );

    private String targetName;
    private File file;
    private final SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT);

    public PacketStealer() {
        super(AddonTemplate.UTILITY_CATEGORY, "packet-stealer",
            "Records everything the server sends your client about one player - movement, swings, totem pops, hits, held items - to <name>.json with timestamps.");
    }

    @Override
    public void onActivate() {
        stamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        targetName = null;
        file = null;

        if (mc.world == null || mc.player == null) {
            error("Join a world first.");
            toggle();
            return;
        }

        resolveTarget();
    }

    @Override
    public void onDeactivate() {
        targetName = null;
        file = null;
    }

    private void resolveTarget() {
        String configured = target.get().trim();

        if (!configured.isEmpty()) {
            targetName = configured;
        } else {
            PlayerEntity nearest = null;
            double best = range.get() * range.get();

            for (PlayerEntity player : mc.world.getPlayers()) {
                if (player == mc.player) continue;

                double distance = mc.player.squaredDistanceTo(player);
                if (distance <= best) {
                    best = distance;
                    nearest = player;
                }
            }

            if (nearest == null) {
                error("No player within %.0f blocks - set target in the module settings.", range.get());
                toggle();
                return;
            }

            targetName = nearest.getName().getString();
        }

        file = new File(mc.runDirectory,
            "manasluflux/packetstealer/" + sanitize(targetName) + ".json");

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        if (chatFeedback.get()) info("Logging %s to %s", targetName, file.getPath());
    }

    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (targetName == null || file == null || mc.world == null) return;

        Object packet = event.packet;
        Integer entityId = entityIdOf(packet);
        if (entityId == null) return;

        Entity entity = mc.world.getEntityById(entityId);
        if (!(entity instanceof PlayerEntity player)) return;
        if (!player.getName().getString().equalsIgnoreCase(targetName)) return;

        String detail = describe(packet, player);
        if (detail == null) return;

        log(eventName(packet), detail);
    }

    /** The entity id a tracked packet refers to, or null when the type is not logged at all. */
    private Integer entityIdOf(Object packet) {
        if (packet instanceof EntityS2CPacket p) {
            if (!movement.get()) return null;
            Entity entity = p.getEntity(mc.world);
            return entity == null ? null : entity.getId();
        }

        if (packet instanceof EntityPositionS2CPacket p) {
            return movement.get() ? p.entityId() : null;
        }

        if (packet instanceof EntityPositionSyncS2CPacket p) {
            return movement.get() ? p.id() : null;
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket p) {
            return movement.get() ? p.getEntityId() : null;
        }

        if (packet instanceof EntityAnimationS2CPacket p) return p.getEntityId();

        if (packet instanceof EntityStatusS2CPacket p) {
            Entity entity = p.getEntity(mc.world);
            return entity == null ? null : entity.getId();
        }

        if (packet instanceof EntityEquipmentUpdateS2CPacket p) {
            return equipment.get() ? p.getEntityId() : null;
        }

        if (packet instanceof EntityDamageS2CPacket p) return p.entityId();

        if (packet instanceof EntityTrackerUpdateS2CPacket p) {
            return dataTracker.get() ? p.id() : null;
        }

        return null;
    }

    private String eventName(Object packet) {
        if (packet instanceof EntityS2CPacket) return "move";
        if (packet instanceof EntityPositionS2CPacket || packet instanceof EntityPositionSyncS2CPacket) return "teleport";
        if (packet instanceof EntityVelocityUpdateS2CPacket) return "velocity";
        if (packet instanceof EntityAnimationS2CPacket) return "animation";
        if (packet instanceof EntityStatusS2CPacket) return "status";
        if (packet instanceof EntityEquipmentUpdateS2CPacket) return "equipment";
        if (packet instanceof EntityDamageS2CPacket) return "damage";
        if (packet instanceof EntityTrackerUpdateS2CPacket) return "data-tracker";
        return "packet";
    }

    private String describe(Object packet, PlayerEntity player) {
        if (packet instanceof EntityS2CPacket p) {
            return String.format(Locale.ROOT, "d=(%d, %d, %d) yaw=%.1f pitch=%.1f ground=%s",
                p.getDeltaX(), p.getDeltaY(), p.getDeltaZ(), p.getYaw(), p.getPitch(), p.isOnGround());
        }

        if (packet instanceof EntityPositionS2CPacket p) {
            return position(p.change().position()) + " ground=" + p.onGround();
        }

        if (packet instanceof EntityPositionSyncS2CPacket p) {
            return position(p.values().position()) + " ground=" + p.onGround();
        }

        if (packet instanceof EntityVelocityUpdateS2CPacket p) {
            Vec3d v = p.getVelocity();
            return String.format(Locale.ROOT, "v=(%.3f, %.3f, %.3f)", v.x, v.y, v.z);
        }

        if (packet instanceof EntityAnimationS2CPacket p) {
            return animation(p.getAnimationId());
        }

        if (packet instanceof EntityStatusS2CPacket p) {
            return status(p.getStatus());
        }

        if (packet instanceof EntityEquipmentUpdateS2CPacket p) {
            List<com.mojang.datafixers.util.Pair<EquipmentSlot, ItemStack>> list = p.getEquipmentList();

            StringBuilder out = new StringBuilder();
            for (var pair : list) {
                if (!out.isEmpty()) out.append("; ");
                out.append(pair.getFirst().name().toLowerCase(Locale.ROOT))
                    .append('=')
                    .append(stack(pair.getSecond()));
            }

            return out.toString();
        }

        if (packet instanceof EntityDamageS2CPacket p) {
            return "source=" + p.sourceType().getIdAsString()
                + " attacker=" + p.sourceCauseId()
                + " direct=" + p.sourceDirectId();
        }

        if (packet instanceof EntityTrackerUpdateS2CPacket p) {
            StringBuilder out = new StringBuilder("entries=");
            out.append(p.trackedValues().size());
            return p.trackedValues().isEmpty() ? out + " (empty)" : out.toString();
        }

        return null;
    }

    private static String position(Vec3d pos) {
        return String.format(Locale.ROOT, "pos=(%.2f, %.2f, %.2f)", pos.x, pos.y, pos.z);
    }

    private static String stack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "empty";

        String name = stack.getItem().toString().replace("minecraft:", "");
        return stack.getCount() > 1 ? name + " x" + stack.getCount() : name;
    }

    private static String animation(int id) {
        return switch (id) {
            case 0 -> "swing main hand";
            case 1 -> "hurt";
            case 2 -> "wake up";
            case 3 -> "swing off hand";
            case 4 -> "critical hit";
            case 5 -> "magic critical";
            case 35 -> "totem of undying popped";
            default -> "animation " + id;
        };
    }

    private static String status(byte id) {
        return switch (id) {
            case 1 -> "hurt";
            case 2 -> "dead";
            case 3 -> "use totem (legacy)";
            case 35 -> "totem of undying popped";
            case 36 -> "drowned turn";
            case 37 -> "burning";
            default -> "status " + (id & 0xFF);
        };
    }

    private void log(String event, String detail) {
        try {
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write("{\"time\":\"" + stamp.format(new Date()) + "\",\"event\":\"" + event
                    + "\",\"detail\":\"" + escape(detail) + "\"}\n");
            }
        } catch (IOException e) {
            error("Could not write %s: %s", file.getName(), e.getMessage());
            file = null;
        }
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    @Override
    public String getInfoString() {
        return targetName == null ? "" : targetName;
    }
}
