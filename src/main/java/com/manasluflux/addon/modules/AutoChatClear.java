package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import com.manasluflux.addon.commands.Msg;
import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.Text;

/**
 * Automation of vanilla's F3+D (clear chat): after every N messages received,
 * the chat is cleared the same way F3+D does - refreshed history stays available
 * by scrolling up, it just wipes the on-screen backlog. Useful on busy servers
 * where a flood of messages would otherwise lag the chat renderer.
 * Purely client-side: nothing is sent to the server.
 */
public class AutoChatClear extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> every = sgGeneral.add(new IntSetting.Builder()
        .name("every-messages")
        .description("Clear the chat after this many messages have been received.")
        .defaultValue(20)
        .range(0, 10000)
        .sliderRange(0, 10000)
        .build()
    );

    private final Setting<Boolean> clearRecent = sgGeneral.add(new BoolSetting.Builder()
        .name("clear-recent-history")
        .description("Also wipe the recent-message history (what you scroll back through). Off = exactly what F3+D does: history stays, only the visible backlog is emptied (and it refills from history as new messages arrive).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> countOwn = sgGeneral.add(new BoolSetting.Builder()
        .name("count-own")
        .description("Also count messages you send yourself towards the trigger.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> notify = sgGeneral.add(new BoolSetting.Builder()
        .name("notify")
        .description("Show an action-bar-free chat info line each time the chat is cleared.")
        .defaultValue(false)
        .build()
    );

    private int counter;

    public AutoChatClear() {
        super(AddonTemplate.REWRITE_CATEGORY, "auto-chat-clear", "Automatically does the F3+D chat clear after every N received messages - keeps chat clean on busy servers without manual keypresses. Purely client-side.");
    }

    @Override
    public void onActivate() {
        counter = 0;
    }

    @EventHandler
    private void onGameJoin(GameJoinedEvent event) {
        counter = 0;
    }

    @EventHandler
    private void onReceiveMessage(ReceiveMessageEvent event) {
        if (event.getMessage() == null) return;

        // Ignore our own feedback line so the counter doesn't feed itself.
        if (!countOwn.get() && isOwnFeedback(event.getMessage())) return;

        counter++;
        if (counter >= every.get()) {
            counter = 0;
            clearChat();
        }
    }

    /**
     * Feedback lines printed by the module itself (Msg.info("Chat cleared."))
     * carry text we can detect cheaply.
     */
    private static boolean isOwnFeedback(Text message) {
        String text = message.getString();
        return text.startsWith("Chat cleared") || text.contains("AutoChatClear");
    }

    private void clearChat() {
        ChatHud chat = mc.inGameHud.getChatHud();
        if (chat != null) {
            chat.clear(clearRecent.get());
            if (notify.get()) {
                Msg.info("Chat cleared.");
            }
        }
    }
}
