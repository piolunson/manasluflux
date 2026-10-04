package com.manasluflux.addon.hud;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class PlayerListHud extends HudElement {
    public static final HudElementInfo<PlayerListHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "player-list", "Box with all the other players on the server, sorted by name or ping.", PlayerListHud::new);

    private static final Minecraft mc = Minecraft.getInstance();

    private static final Color BACKGROUND = new Color(0, 0, 0, 140);
    private static final Color TITLE_COLOR = new Color(255, 196, 217);
    private static final Color NAME_COLOR = Color.WHITE;
    private static final Color GRAY = new Color(185, 185, 185);
    private static final Color PING_GOOD = new Color(140, 225, 130);
    private static final Color PING_OK = new Color(250, 210, 120);
    private static final Color PING_BAD = new Color(255, 105, 105);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> title = sgGeneral.add(new StringSetting.Builder()
        .name("title")
        .description("Text shown at the top of the box.")
        .defaultValue("Other Players")
        .build()
    );

    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>()
        .name("sort")
        .description("Sort the players by name or by ping.")
        .defaultValue(SortMode.Ping)
        .build()
    );

    private final Setting<Boolean> reverse = sgGeneral.add(new BoolSetting.Builder()
        .name("reverse")
        .description("Reverse the sort order (Z-A names, or highest ping first).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showSelf = sgGeneral.add(new BoolSetting.Builder()
        .name("show-self")
        .description("Include yourself in the list.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showPing = sgGeneral.add(new BoolSetting.Builder()
        .name("show-ping")
        .description("Show each player's latency in ms, color-coded (green < 100, yellow < 250, red above).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxRows = sgGeneral.add(new IntSetting.Builder()
        .name("max-rows")
        .description("Maximum number of players to show. 0 shows everyone.")
        .defaultValue(15)
        .min(0)
        .sliderMax(100)
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a dark box behind the list.")
        .defaultValue(true)
        .build()
    );

    public enum SortMode {
        Name, Ping;
    }

    public PlayerListHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double pad = 6;
        double rowGap = 3;
        double th = renderer.textHeight(true);

        // Collect players
        List<PlayerInfo> players = new ArrayList<>();
        ClientPacketListener conn = mc.getConnection();
        UUID self = mc.player != null ? mc.player.getUUID() : null;
        if (conn != null) {
            for (PlayerInfo p : conn.getOnlinePlayers()) {
                if (!showSelf.get() && self != null && p.getProfile().id().equals(self)) continue;
                players.add(p);
            }
        }

        Comparator<PlayerInfo> cmp = sortMode.get() == SortMode.Name
            ? Comparator.comparing(p -> p.getProfile().name().toLowerCase())
            : Comparator.comparingInt(PlayerInfo::getLatency);
        if (reverse.get()) cmp = cmp.reversed();
        players.sort(cmp);

        int cap = maxRows.get();
        List<PlayerInfo> shown = cap > 0 && players.size() > cap ? players.subList(0, cap) : players;

        // Measure
        String titleText = title.get();
        String emptyText = conn == null ? "not connected" : "no other players";
        double width = pad * 2 + renderer.textWidth(titleText, true);
        for (PlayerInfo p : shown) {
            double w = pad * 2 + renderer.textWidth(p.getProfile().name(), true);
            if (showPing.get()) w += 8 + renderer.textWidth(pingText(p.getLatency()), true);
            width = Math.max(width, w);
        }
        if (shown.isEmpty()) width = Math.max(width, pad * 2 + renderer.textWidth(emptyText, true));

        double height = pad * 2 + th + rowGap + shown.size() * th + Math.max(0, shown.size() - 1) * rowGap;
        if (shown.isEmpty()) height += th;

        setSize(width, height);

        if (background.get()) renderer.quad(x, y, width, height, BACKGROUND);

        // Title
        double cy = y + pad;
        renderer.text(titleText, x + pad, cy, TITLE_COLOR, true);
        cy += th + rowGap;

        if (shown.isEmpty()) {
            renderer.text(emptyText, x + pad, cy, GRAY, true);
            return;
        }

        // Rows
        for (PlayerInfo p : shown) {
            renderer.text(p.getProfile().name(), x + pad, cy, NAME_COLOR, true);
            if (showPing.get()) {
                String t = pingText(p.getLatency());
                double tx = x + width - pad - renderer.textWidth(t, true);
                renderer.text(t, tx, cy, pingColor(p.getLatency()), true);
            }
            cy += th + rowGap;
        }
    }

    private static String pingText(int ms) {
        return ms + "ms";
    }

    private static Color pingColor(int ms) {
        if (ms < 100) return PING_GOOD;
        if (ms < 250) return PING_OK;
        return PING_BAD;
    }
}
