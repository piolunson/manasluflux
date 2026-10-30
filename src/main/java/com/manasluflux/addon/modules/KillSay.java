package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ChatCommandSignedC2SPacket;
import net.minecraft.network.packet.c2s.play.CommandExecutionC2SPacket;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Port of Sydney's <b>KillSay</b> ({@code me.aidan.sydney.modules.impl.combat.KillSayModule},
 * sydney-client/Sydney-Legacy, GPL-3.0): sends a message when you kill someone and, optionally, when
 * a player pops a totem.
 *
 * <p>The behaviour is Sydney's: a kill message only for a player <b>you</b> killed (its
 * {@code TargetDeathEvent} never fires for mobs or for deaths you were not credited with), a queue
 * that sends one message every {@code delay} seconds so a kill streak does not turn into chat spam,
 * {@code > } prefixed green text, a kill streak that resets when you die, and the {@code suicide}
 * bookkeeping that keeps {@code /kill} from resetting the streak when {@code suicide-ignore} is on.
 *
 * <p>Two things map onto Meteor differently. Sydney reads its {@code killfile.txt} / {@code popfile.txt}
 * from disk for {@code mode: Custom}; here those are {@code StringListSetting}s, so the messages are
 * edited in the GUI and travel with the config. Sydney's pop event comes from its target manager; the
 * port uses the totem status packet ({@code EntityStatuses.USE_TOTEM_OF_UNDYING}) and counts pops per
 * player, and it treats "not a friend" the way the rest of the addon does instead of Sydney's target
 * list.
 */
public class KillSay extends Module {
    public enum Mode {
        Default,
        Custom
    }

    /** Sydney's own four lines, with the client's name swapped for this addon's. */
    private static final List<String> DEFAULT_KILL_MESSAGES = List.of(
        "Sit [username], ManasluFlux owns me and all!",
        "[username] I killed you with the power of ManasluFlux!",
        "Sorry [username], get good get ManasluFlux!",
        "I just killed [username] thanks to ManasluFlux!"
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgMessages = settings.createGroup("Messages");

    private final Setting<Boolean> kills = sgGeneral.add(new BoolSetting.Builder()
        .name("kills")
        .description("Sends a message when you kill a player.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> popMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("pops")
        .description("Sends a message when a player pops a totem.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay")
        .description("Seconds between two messages, so a streak does not spam chat.")
        .defaultValue(5)
        .range(0, 10)
        .sliderRange(0, 10)
        .build()
    );

    private final Setting<Boolean> clientside = sgGeneral.add(new BoolSetting.Builder()
        .name("clientside")
        .description("Prints the messages in your own chat instead of sending them to the server.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> greenText = sgGeneral.add(new BoolSetting.Builder()
        .name("green-text")
        .description("Prefixes the message with \"> \" so servers show it as green text.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> killStreak = sgGeneral.add(new BoolSetting.Builder()
        .name("kill-streak")
        .description("Keeps track of your kills and announces streaks.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> suicideIgnore = sgGeneral.add(new BoolSetting.Builder()
        .name("suicide-ignore")
        .description("Does not reset the kill streak when you kill yourself with /kill.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Mode> mode = sgMessages.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Default uses the built-in messages, Custom uses the lists below.")
        .defaultValue(Mode.Default)
        .build()
    );

    private final Setting<List<String>> customKills = sgMessages.add(new StringListSetting.Builder()
        .name("kill-messages")
        .description("Custom kill messages. [username] is replaced with the player's name.")
        .defaultValue(DEFAULT_KILL_MESSAGES)
        .visible(() -> mode.get() == Mode.Custom)
        .build()
    );

    private final Setting<List<String>> customPops = sgMessages.add(new StringListSetting.Builder()
        .name("pop-messages")
        .description("Custom pop messages. [username] and [pops] are replaced.")
        .defaultValue("Sit [username], [pops] pops already!")
        .visible(() -> mode.get() == Mode.Custom)
        .build()
    );

    private final Deque<String> queue = new ArrayDeque<>();
    private final Map<UUID, Integer> popCounts = new HashMap<>();
    private final Random random = new Random();

    private long lastSend;
    private String lastMessage = "";
    private int streak;
    private boolean suicide;

    public KillSay() {
        super(AddonTemplate.COMBAT_CATEGORY, "kill-say",
            "Sends a message when you kill someone, and optionally when a player pops (port of Sydney's KillSay).");
    }

    @Override
    public void onActivate() {
        queue.clear();
        popCounts.clear();
        streak = 0;
        suicide = false;
        lastMessage = "";
        lastSend = System.currentTimeMillis();
    }

    @Override
    public void onDeactivate() {
        queue.clear();
        popCounts.clear();
    }

    @Override
    public String getInfoString() {
        return Integer.toString(streak);
    }

    // ------------------------------------------------------------------ events

    /**
     * Sydney's {@code TargetDeathEvent}: only a player you were credited with killing counts, and a
     * message is queued rather than sent so the {@code delay} can space it out.
     */
    @EventHandler
    private void onEntityRemoved(EntityRemovedEvent event) {
        if (mc.player == null) return;

        if (event.entity == mc.player) {
            if (!suicideIgnore.get() || !suicide) streak = 0;
            suicide = false;
            return;
        }

        if (!(event.entity instanceof PlayerEntity target) || target == mc.player) return;
        if (target.getHealth() > 0) return; // left the world (or a replay), not a death
        if (!wasKilledByUs(target)) return;
        if (!Friends.get().shouldAttack(target)) return;

        streak++;

        if (kills.get()) queue.add(killMessage(target.getName().getString()));
        if (killStreak.get() && streak > 1) info("You are on a " + streak + " kill streak.");
    }

    /** Sydney's {@code PlayerPopEvent}, taken from the totem status packet instead. */
    @EventHandler
    private void onReceive(PacketEvent.Receive event) {
        if (mc.player == null || mc.world == null || !popMessages.get()) return;
        if (!(event.packet instanceof EntityStatusS2CPacket packet)) return;
        if (packet.getStatus() != EntityStatuses.USE_TOTEM_OF_UNDYING) return;

        Entity entity = packet.getEntity(mc.world);
        if (!(entity instanceof PlayerEntity player) || player == mc.player) return;
        if (!Friends.get().shouldAttack(player)) return;

        int count = popCounts.merge(player.getUuid(), 1, Integer::sum);
        queue.add(popMessage(player.getName().getString(), count));
    }

    /** Sydney watches its command input: {@code /kill} is a suicide, so the streak is not reset for it. */
    @EventHandler
    private void onSend(PacketEvent.Send event) {
        if (mc.player == null) return;

        String command = null;

        if (event.packet instanceof ChatCommandSignedC2SPacket packet) command = packet.command();
        else if (event.packet instanceof CommandExecutionC2SPacket packet) command = packet.command();

        if (command == null) return;

        // Sydney matches on "/kill" appearing in the input; the packet carries the command without the slash.
        if (command.trim().toLowerCase().startsWith("kill")) suicide = true;
        lastSend = System.currentTimeMillis();
    }

    /** Sydney resets its timer when you talk, so a message never lands on top of your own chat. */
    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (mc.player == null) return;
        lastSend = System.currentTimeMillis();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || queue.isEmpty()) return;
        if (System.currentTimeMillis() - lastSend < delay.get() * 1000L) return;

        send(queue.poll());
    }

    // ------------------------------------------------------------------ messages

    private void send(String message) {
        if (message == null || message.isBlank()) return;

        if (clientside.get()) info(message);
        else ChatUtils.sendPlayerMsg((greenText.get() ? "> " : "") + message);

        lastMessage = message;
        lastSend = System.currentTimeMillis();
    }

    private boolean wasKilledByUs(LivingEntity target) {
        LivingEntity adversary = target.getPrimeAdversary();

        if (adversary == mc.player) return true;
        return adversary instanceof PlayerEntity player && player.getUuid().equals(mc.player.getUuid());
    }

    private String killMessage(String username) {
        List<String> messages = mode.get() == Mode.Custom ? customKills.get() : DEFAULT_KILL_MESSAGES;

        return pick(messages).replace("[username]", username);
    }

    private String popMessage(String username, int count) {
        List<String> messages = mode.get() == Mode.Custom ? customPops.get() : List.of();

        if (!messages.isEmpty()) {
            return pick(messages)
                .replace("[username]", username)
                .replace("[pops]", Integer.toString(count));
        }

        return count + (count > 1 ? " pops " : " pop ") + username + "!";
    }

    /** Sydney picks a random message and re-rolls while it repeats the previous one. */
    private String pick(List<String> messages) {
        List<String> usable = new ArrayList<>();
        for (String message : messages) {
            if (message != null && !message.isBlank()) usable.add(message);
        }

        if (usable.isEmpty()) return "";
        if (usable.size() == 1) return usable.get(0);

        String message = usable.get(random.nextInt(usable.size()));

        for (int i = 0; i < 8 && message.equals(lastMessage); i++) {
            message = usable.get(random.nextInt(usable.size()));
        }

        return message;
    }
}
