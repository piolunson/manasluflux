package com.manasluflux.addon.hud;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

/**
 * Small info box for the multiplayer session: server name/address, online players
 * and your live latency. Updates every few ticks from the server list handshake data.
 */
public class ServerInfoHud extends HudElement {
    public static final HudElementInfo<ServerInfoHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "server-info", "Shows the server you are on, online players and your live ping.", ServerInfoHud::new);

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Color BACKGROUND = new Color(0, 0, 0, 140);
    private static final Color TITLE_COLOR = new Color(140, 220, 255);
    private static final Color LABEL = new Color(185, 185, 185);
    private static final Color VALUE = Color.WHITE;
    private static final Color PING_GOOD = new Color(140, 225, 130);
    private static final Color PING_OK = new Color(250, 210, 120);
    private static final Color PING_BAD = new Color(255, 105, 105);
    private static final Color GRAY = new Color(140, 140, 140);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showIp = sgGeneral.add(new BoolSetting.Builder()
        .name("show-address")
        .description("Show the server address (IP:port) under the title.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showPing = sgGeneral.add(new BoolSetting.Builder()
        .name("show-ping")
        .description("Show your live latency with color coding (green < 100, yellow < 250, red above).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a dark box behind the info.")
        .defaultValue(true)
        .build()
    );

    public ServerInfoHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double pad = 6;
        double rowGap = 3;
        double th = renderer.textHeight(true);

        boolean singlePlayer = mc.isInSingleplayer();

        ServerInfo data = mc.getCurrentServerEntry();
        String serverName = singlePlayer ? "Singleplayer" : (data != null && data.name != null ? data.name : "Unknown");
        String serverIp = singlePlayer ? "localhost" : (data != null && data.address != null ? data.address : "unknown");

        int playerCount = mc.getNetworkHandler() != null ? mc.getNetworkHandler().getPlayerList().size() : 0;
        int ping = 0;
        if (!singlePlayer && mc.player != null && mc.getNetworkHandler() != null) {
            var info = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (info != null) ping = info.getLatency();
        }

        // Measure
        double width = pad * 2 + renderer.textWidth(serverName, true);
        if (showIp.get()) width = Math.max(width, pad * 2 + renderer.textWidth(serverIp, true));

        String labelPlayers = label(VALUE, "Players", String.valueOf(playerCount));
        width = Math.max(width, pad * 2 + renderer.textWidth(labelPlayers, true));

        String pingText = ping + "ms";
        String labelPing = label(showPingColor(ping), "Ping", pingText);
        if (showPing.get()) width = Math.max(width, pad * 2 + renderer.textWidth(labelPing, true));

        double height = pad * 2 + th + rowGap;
        height += (showIp.get() ? th + rowGap : 0);
        height += th + rowGap;
        height += (showPing.get() && !singlePlayer ? th : 0);
        if (!showPing.get() || singlePlayer) height += th; // placeholder row keeps the box stable

        setSize(width, height);

        if (background.get()) renderer.quad(x, y, width, height, BACKGROUND);

        double cy = y + pad;
        renderer.text(serverName, x + pad, cy, TITLE_COLOR, true);
        cy += th + rowGap;

        if (showIp.get()) {
            renderer.text(serverIp, x + pad, cy, LABEL, true);
            cy += th + rowGap;
        }

        renderer.text(labelPlayers, x + pad, cy, VALUE, true);
        cy += th + rowGap;

        if (showPing.get() && !singlePlayer) {
            renderer.text(labelPing, x + pad, cy, showPingColor(ping), true);
        } else {
            renderer.text(singlePlayer ? "no ping in singleplayer" : "-", x + pad, cy, GRAY, true);
        }
    }

    /** Renders "Players N" / "Ping 35ms" as one string, colored uniformly. */
    private static String label(Color unusedColor, String label, String value) {
        return label + " " + value;
    }

    private static Color showPingColor(int ms) {
        if (ms < 100) return PING_GOOD;
        if (ms < 250) return PING_OK;
        return PING_BAD;
    }
}
