package com.manasluflux.addon.hud;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Session combat tracker: counts entities killed and times you died since the last
 * world join (or manual reset via the toggle in the HUD editor). Shows kills, deaths
 * and the resulting K/D as three color-coded lines.
 */
public class KillCounterHud extends HudElement {
    public static final HudElementInfo<KillCounterHud> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "kill-counter", "Session kills, deaths and K/D ratio, tracked from the moment you join a world.", KillCounterHud::new);

    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static final Color BACKGROUND = new Color(0, 0, 0, 140);
    private static final Color TITLE_COLOR = new Color(255, 120, 120);
    private static final Color KILL_COLOR = new Color(140, 225, 130);
    private static final Color DEATH_COLOR = new Color(255, 105, 105);
    private static final Color KD_COLOR = new Color(250, 210, 120);

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> countPlayers = sgGeneral.add(new BoolSetting.Builder()
        .name("count-players")
        .description("PlayerEntity kills count towards the total.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> countMobs = sgGeneral.add(new BoolSetting.Builder()
        .name("count-mobs")
        .description("Hostile mob kills count towards the total.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDeaths = sgGeneral.add(new BoolSetting.Builder()
        .name("show-deaths")
        .description("Show the deaths line (and K/D).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background")
        .description("Draw a dark box behind the counter.")
        .defaultValue(true)
        .build()
    );

    private int kills;
    private int deaths;
    private String lastWorldKey = "";

    public KillCounterHud() {
        super(INFO);
    }

    private String worldKey() {
        if (mc.world == null) return "";
        return mc.world.getRegistryKey().getValue().toString() + "|" + (mc.isInSingleplayer() ? "sp" : (mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : "mp"));
    }

    @Override
    public void render(HudRenderer renderer) {
        // Reset when joining a new world/dimension.
        String key = worldKey();
        if (!key.equals(lastWorldKey)) {
            lastWorldKey = key;
            kills = 0;
            deaths = 0;
        }

        double pad = 6;
        double rowGap = 3;
        double th = renderer.textHeight(true);

        String killsLine = "Kills  " + kills;
        String deathsLine = "Deaths " + deaths;
        double kd = deaths == 0 ? kills : ((double) kills / deaths);
        String kdLine = String.format("K/D    %.2f", kd);

        double width = pad * 2 + renderer.textWidth("Kills  0", true);
        width = Math.max(width, pad * 2 + renderer.textWidth(killsLine, true));
        if (showDeaths.get()) {
            width = Math.max(width, pad * 2 + renderer.textWidth(deathsLine, true));
            width = Math.max(width, pad * 2 + renderer.textWidth(kdLine, true));
        }

        double height = pad * 2 + th + rowGap;
        height += th + rowGap;
        height += (showDeaths.get() ? th + rowGap + th - rowGap : 0);

        setSize(width, height);

        if (background.get()) renderer.quad(x, y, width, height, BACKGROUND);

        double cy = y + pad;
        renderer.text("Combat", x + pad, cy, TITLE_COLOR, true);
        cy += th + rowGap;

        renderer.text(killsLine, x + pad, cy, KILL_COLOR, true);
        cy += th + rowGap;

        if (showDeaths.get()) {
            renderer.text(deathsLine, x + pad, cy, DEATH_COLOR, true);
            cy += th;
            renderer.text(kdLine, x + pad, cy, KD_COLOR, true);
        }
    }

    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (mc.player == null) return;

        // Death of self.
        if (event.entity == mc.player) {
            deaths++;
            return;
        }

        // Kill credit: entities that die while we are their killer.
        if (!(event.entity instanceof LivingEntity living)) return;
        if (living == mc.player) return;
        if (living.getHealth() > 0) return; // not a death

        var killer = living.getPrimeAdversary();
        if (killer != mc.player && !(killer instanceof PlayerEntity p && p.getUuid().equals(mc.player.getUuid()))) return;

        if (living instanceof PlayerEntity target) {
            if (!countPlayers.get()) return;
            if (Friends.get().shouldAttack(target)) kills++;
        } else if (living instanceof HostileEntity) {
            if (countMobs.get()) kills++;
        }
    }
}
