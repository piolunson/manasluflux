package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.BlockPos;

public class DiscordRpc extends Module {
    // Discord's default (Meteor Client) application. For a custom NAME shown by Discord
    // ("Playing <your name>"), create an app at discord.com/developers, paste its
    // Application ID here, and upload your images in the app's Art Assets tab.
    private static final long DEFAULT_APP_ID = 835240968533049424L;

    public enum Preset {
        Crystals, PvP, Mining, Exploring, Building, AFK, Custom
    }

    private static boolean loading = true;

    private final SettingGroup sgPreset = settings.createGroup("Preset");
    private final SettingGroup sgLine1 = settings.createGroup("Line 1");
    private final SettingGroup sgLine2 = settings.createGroup("Line 2");
    private final SettingGroup sgImages = settings.createGroup("Images");
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Preset> preset = sgPreset.add(new EnumSetting.Builder<Preset>()
        .name("preset")
        .description("One-click preset that fills every field below. Pick Custom to edit everything yourself.")
        .defaultValue(Preset.Crystals)
        .onChanged(v -> { if (v != Preset.Custom && !loading) applyPreset(v); })
        .build()
    );

    private final Setting<String> line1 = sgLine1.add(new StringSetting.Builder()
        .name("line-1")
        .description("First line. Placeholders: {player} {server} {ping} {pos} {dimension} {cps} {version} {health}")
        .defaultValue("Crasting on {server}")
        .onChanged(v -> forceUpdate = true)
        .build()
    );

    private final Setting<String> line2 = sgLine2.add(new StringSetting.Builder()
        .name("line-2")
        .description("Second line. Same placeholders as line 1.")
        .defaultValue("{cps} CPS | {ping} ms")
        .onChanged(v -> forceUpdate = true)
        .build()
    );

    private final Setting<String> largeImageName = sgImages.add(new StringSetting.Builder()
        .name("image-name")
        .description("Key of the large image inside your Discord application's Art Assets tab.")
        .defaultValue("manasluflux")
        .onChanged(v -> forceUpdate = true)
        .build()
    );

    private final Setting<String> largeImageText = sgImages.add(new StringSetting.Builder()
        .name("image-text")
        .description("Text shown when hovering the large image (this doubles as an easy custom name). Placeholders work.")
        .defaultValue("ManasluFlux {version}")
        .onChanged(v -> forceUpdate = true)
        .build()
    );

    private final Setting<Boolean> smallImage = sgImages.add(new BoolSetting.Builder()
        .name("small-image")
        .description("Also draw a small image in the corner of the big one.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> smallImageName = sgImages.add(new StringSetting.Builder()
        .name("small-image-name")
        .description("Key of the small image inside your Discord application's Art Assets tab.")
        .defaultValue("snail")
        .onChanged(v -> forceUpdate = true)
        .build()
    );

    private final Setting<String> smallImageText = sgImages.add(new StringSetting.Builder()
        .name("small-image-text")
        .description("Text shown when hovering the small image.")
        .defaultValue("ManasluFlux")
        .onChanged(v -> forceUpdate = true)
        .build()
    );

    private final Setting<Boolean> showCps = sgGeneral.add(new BoolSetting.Builder()
        .name("crystals-per-second")
        .description("Show a crystals-per-second counter (counts crystals blown up) - appends \" | <n> CPS\" to line 1 and feeds the {cps} placeholder.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> timer = sgGeneral.add(new BoolSetting.Builder()
        .name("show-timer")
        .description("Show elapsed time (mm:ss) next to your presence.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> updateInterval = sgGeneral.add(new IntSetting.Builder()
        .name("update-interval")
        .description("Ticks between Discord updates. Lower = fresher numbers, but sends more packets to Discord.")
        .defaultValue(40)
        .min(20)
        .sliderRange(20, 200)
        .build()
    );

    private final Setting<String> appId = sgGeneral.add(new StringSetting.Builder()
        .name("application-id")
        .description("Discord Application ID (number only). This app's name & icon are what Discord shows as your presence name!")
        .defaultValue("1563969889968988291")
        .onChanged(v -> appIdChanged = true)
        .build()
    );

    private RichPresence rpc;
    private long lastAppId = -1;
    private boolean forceUpdate;
    private boolean appIdChanged;
    private int ticks;
    private int explosionCount;
    private int cps;

    public DiscordRpc() {
        super(AddonTemplate.CATEGORY, "discord-rpc", "Fully customizable Discord Rich Presence: presets, custom line 1 / line 2, image name & text, and a crystals-per-second counter.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        loading = false;
        forceUpdate = true;
        explosionCount = 0;
        cps = 0;
        ticks = 0;
        rpc = new RichPresence();
        rpc.setStart(System.currentTimeMillis() / 1000L);
        lastAppId = parseAppId();
        DiscordIPC.start(lastAppId, null);
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
        rpc = null;
    }

    private long parseAppId() {
        try {
            return Long.parseLong(appId.get().trim());
        } catch (NumberFormatException e) {
            return DEFAULT_APP_ID;
        }
    }

    private void applyPreset(Preset p) {
        switch (p) {
            case Crystals -> {
                line1.set("Crystaling on {server}");
                line2.set("{cps} CPS | {ping} ms");
            }
            case PvP -> {
                line1.set("Fighting on {server}");
                line2.set("{ping} ms | {health} HP");
            }
            case Mining -> {
                line1.set("Mining in {dimension}");
                line2.set("XYZ {pos}");
            }
            case Exploring -> {
                line1.set("Exploring {dimension}");
                line2.set("XYZ {pos}");
            }
            case Building -> {
                line1.set("Building on {server}");
                line2.set("XYZ {pos}");
            }
            case AFK -> {
                line1.set("AFK on {server}");
                line2.set("Doing nothing useful");
            }
            default -> {}
        }
    }

    private String fill(String template) {
        String[] raw = template.split("[{}]", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length; i++) {
            if (i % 2 == 0) {
                out.append(raw[i]);
            } else {
                out.append(switch (raw[i]) {
                    case "player" -> mc.player != null ? mc.player.getGameProfile().name() : "player";
                    case "server" -> getServerName();
                    case "ping" -> String.valueOf(getPing());
                    case "pos" -> pos();
                    case "dimension" -> mc.world != null ? mc.world.getRegistryKey().getValue().getPath() : "menu";
                    case "cps" -> String.valueOf(cps);
                    case "version" -> modVersion();
                    case "health" -> mc.player != null ? String.valueOf((int) Math.ceil(mc.player.getHealth())) : "0";
                    default -> "";
                });
            }
        }
        return out.toString();
    }

    private String pos() {
        if (mc.player == null) return "0 0 0";
        BlockPos pos = mc.player.getBlockPos();
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private String getServerName() {
        if (mc.isInSingleplayer()) return "Singleplayer";
        var server = mc.getCurrentServerEntry();
        return server != null ? server.address : "the menu";
    }

    private int getPing() {
        if (mc.player == null || mc.getNetworkHandler() == null) return 0;
        var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 0 : entry.getLatency();
    }

    private String modVersion() {
        return FabricLoader.getInstance().getModContainer("manasluflux")
            .map(c -> c.getMetadata().getVersion().getFriendlyString())
            .orElse(" ");
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof ExplosionS2CPacket && showCps.get()) explosionCount++;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (rpc == null) return;

        // Crystals per second: count explosions in the last 20 ticks.
        if (++ticks % 20 == 0) {
            cps = explosionCount;
            explosionCount = 0;
        }

        if (ticks % updateInterval.get() != 0) return;

        // Restart IPC if the App ID changed.
        long id = parseAppId();
        if (id != lastAppId || appIdChanged) {
            appIdChanged = false;
            lastAppId = id;
            DiscordIPC.stop();
            DiscordIPC.start(id, null);
        }
        if (!DiscordIPC.isConnected() && !DiscordIPC.start(lastAppId, null)) return;

        String raw1 = line1.get();
        if (showCps.get() && cps > 0 && !raw1.toLowerCase().contains("{cps}") && !raw1.toLowerCase().contains("cps")) {
            raw1 += " | " + cps + " CPS";
        }

        rpc.setDetails(fill(raw1));
        rpc.setState(fill(line2.get()));
        rpc.setLargeImage(largeImageName.get().trim(), fill(largeImageText.get()));
        if (smallImage.get()) {
            rpc.setSmallImage(smallImageName.get().trim(), fill(smallImageText.get()));
        }
        if (!timer.get()) rpc.setStart(0L);

        DiscordIPC.setActivity(rpc);
        forceUpdate = false;
    }
}
