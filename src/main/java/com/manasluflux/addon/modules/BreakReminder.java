package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;

/**
 * Nags you to get up every so often. Long sessions of crystal fights are exactly the kind of thing
 * that turns into six hours without noticing, so the module keeps a wall-clock timer and speaks up
 * once the interval has passed.
 *
 * <p>The clock is real time, not ticks: it keeps counting at the same speed whatever the server is
 * doing, and it is reset when the module is switched on, so the interval always means "since I
 * enabled this".
 */
public class BreakReminder extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("How many minutes between reminders.")
        .defaultValue(45)
        .range(0, 10000)
        .sliderRange(5, 180)
        .build()
    );

    private final Setting<Boolean> chat = sgGeneral.add(new BoolSetting.Builder()
        .name("chat")
        .description("Print the reminder in chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> title = sgGeneral.add(new BoolSetting.Builder()
        .name("title")
        .description("Show the reminder as the big title text in the middle of the screen.")
        .defaultValue(true)
        .build()
    );

    private long lastReminder;

    public BreakReminder() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "break-reminder",
            "Reminds you to take a break every so many minutes - chat message and a title on screen.");
    }

    @Override
    public void onActivate() {
        lastReminder = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        int minutes = interval.get();
        if (minutes <= 0) return;

        long now = System.currentTimeMillis();
        if (now - lastReminder < minutes * 60_000L) return;

        lastReminder = now;
        remind(minutes);
    }

    private void remind(int minutes) {
        String message = "You have been playing for " + minutes + " minute" + (minutes == 1 ? "" : "s")
            + " - time for a short break.";

        if (chat.get()) info(message);

        if (title.get() && mc.inGameHud != null) {
            mc.inGameHud.setDefaultTitleFade();
            mc.inGameHud.setTitle(Text.literal("Take a break"));
            mc.inGameHud.setSubtitle(Text.literal("You have been playing for " + minutes + " min"));
        }
    }

    @Override
    public String getInfoString() {
        return interval.get() + "m";
    }
}
