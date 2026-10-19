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
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

/**
 * World session info: in-game day number, clock time (HH:MM), weather and the biome
 * you are standing in. Uses the same overworld-clock math as the .mfday command.
 */
public class SessionInfoHud extends HudElement {
    public static final HudElementInfo<SessionInfoHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "session-info", "Shows the in-game day, clock time, weather and the biome you are standing in.", SessionInfoHud::new);

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Color BACKGROUND = new Color(0, 0, 0, 140);
    private static final Color TITLE_COLOR = new Color(255, 210, 120);
    private static final Color LABEL = new Color(185, 185, 185);
    private static final Color VALUE = Color.WHITE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showTime = sgGeneral.add(new BoolSetting.Builder()
        .name("show-time")
        .description("Show the in-game clock (HH:MM).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showWeather = sgGeneral.add(new BoolSetting.Builder()
        .name("show-weather")
        .description("Show current weather (Clear / Rain / Thunder).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showBiome = sgGeneral.add(new BoolSetting.Builder()
        .name("show-biome")
        .description("Show the biome at your position.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a dark box behind the info.")
        .defaultValue(true)
        .build()
    );

    public SessionInfoHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        double pad = 6;
        double rowGap = 3;
        double th = renderer.textHeight(true);

        long dayTime = mc.world != null ? mc.world.getTime() : 0;
        long day = dayTime / 24000L + 1;
        long ticks = dayTime % 24000L;
        int hours = (int) ((ticks / 1000L + 6) % 24);
        int minutes = (int) (ticks % 1000L * 60L / 1000L);
        String clock = String.format("%02d:%02d", hours, minutes);

        String weather;
        if (mc.world == null) weather = "-";
        else if (mc.world.isThundering()) weather = "Thunder";
        else if (mc.world.isRaining()) weather = "Rain";
        else weather = "Clear";

        String biome = "-";
        if (mc.player != null && mc.world != null) {
            BlockPos pos = mc.player.getBlockPos();
            RegistryEntry<Biome> holder = mc.world.getBiome(pos);
            var key = holder.getKey();
            if (key.isPresent()) {
                String path = key.get().getValue().getPath();
                biome = path.replace('_', ' ');
            }
        }

        // Measure
        double width = pad * 2 + renderer.textWidth("Day " + day, true);
        String timeRow = label("Time", clock);
        if (showTime.get()) width = Math.max(width, pad * 2 + renderer.textWidth(timeRow, true));
        String weatherRow = label("Weather", weather);
        if (showWeather.get()) width = Math.max(width, pad * 2 + renderer.textWidth(weatherRow, true));
        String biomeRow = label("Biome", biome);
        if (showBiome.get()) width = Math.max(width, pad * 2 + renderer.textWidth(biomeRow, true));

        double height = pad * 2;
        height += th + rowGap; // title row
        if (showTime.get()) height += th + rowGap;
        if (showWeather.get()) height += th + rowGap;
        if (showBiome.get()) height += th;
        height -= rowGap;

        setSize(width, height);

        if (background.get()) renderer.quad(x, y, width, height, BACKGROUND);

        double cy = y + pad;
        renderer.text("Day " + day, x + pad, cy, TITLE_COLOR, true);
        cy += th + rowGap;

        if (showTime.get()) {
            renderer.text(timeRow, x + pad, cy, VALUE, true);
            cy += th + rowGap;
        }
        if (showWeather.get()) {
            renderer.text(weatherRow, x + pad, cy, VALUE, true);
            cy += th + rowGap;
        }
        if (showBiome.get()) {
            renderer.text(biomeRow, x + pad, cy, VALUE, true);
        }
    }

    private static String label(String label, String value) {
        return label + "  " + value;
    }
}
