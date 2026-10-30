package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.SendMessageEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites the chat you send so it reads like a polite British sentence: a capital letter at the
 * start of every sentence and a full stop at the end.
 *
 * <p>Every message goes through Meteor's {@code SendMessageEvent}, which is posted before the text
 * is signed, so the tidied message is what actually gets signed and sent - nothing odd shows up in
 * the server's chat logs, and nothing is re-sent as a second packet.
 *
 * <p><b>Grammar correct</b> (on by default) runs a fixed set of the mistakes people actually make
 * while typing fast in a fight: missing apostrophes ({@code dont}, {@code cant}, {@code im}),
 * swapped letters ({@code teh}, {@code adn}), doubled spaces, spaces before punctuation, a missing
 * space after it, and the chat-shorthand words ({@code u}, {@code ur}, {@code r}). It is a rule
 * list, not a language model - it only ever touches the words it knows, so whatever else you type
 * comes out exactly as you wrote it.
 */
public class BritishChat extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> capitalise = sgGeneral.add(new BoolSetting.Builder()
        .name("capitalise")
        .description("Puts a capital letter at the start of the message and after every full stop, question mark or exclamation mark, and always capitalises a standalone \"i\".")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> fullStops = sgGeneral.add(new BoolSetting.Builder()
        .name("full-stops")
        .description("Adds a full stop to the end of a message that does not already end with punctuation.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> grammar = sgGeneral.add(new BoolSetting.Builder()
        .name("grammar-correct")
        .description("Fixes the common typing mistakes: missing apostrophes, swapped letters, doubled or missing spaces and chat shorthand.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> lowercaseICorrect = sgGeneral.add(new BoolSetting.Builder()
        .name("capitalise-i")
        .description("Turns a standalone lowercase \"i\" into \"I\".")
        .defaultValue(true)
        .visible(capitalise::get)
        .build()
    );

    /** Words that are always wrong, keyed by the misspelling. */
    private static final Map<String, String> FIXES = new LinkedHashMap<>();

    static {
        // missing apostrophes
        FIXES.put("dont", "don't");
        FIXES.put("cant", "can't");
        FIXES.put("wont", "won't");
        FIXES.put("isnt", "isn't");
        FIXES.put("arent", "aren't");
        FIXES.put("wasnt", "wasn't");
        FIXES.put("werent", "weren't");
        FIXES.put("didnt", "didn't");
        FIXES.put("doesnt", "doesn't");
        FIXES.put("couldnt", "couldn't");
        FIXES.put("wouldnt", "wouldn't");
        FIXES.put("shouldnt", "shouldn't");
        FIXES.put("havent", "haven't");
        FIXES.put("hasnt", "hasn't");
        FIXES.put("hadnt", "hadn't");
        FIXES.put("youre", "you're");
        FIXES.put("theyre", "they're");
        FIXES.put("weve", "we've");
        FIXES.put("ive", "I've");
        FIXES.put("ill", "I'll");
        FIXES.put("im", "I'm");
        FIXES.put("thats", "that's");
        FIXES.put("whats", "what's");
        FIXES.put("lets", "let's");
        FIXES.put("aint", "ain't");
        FIXES.put("youve", "you've");
        FIXES.put("wouldve", "would've");

        // swapped or missing letters
        FIXES.put("teh", "the");
        FIXES.put("hte", "the");
        FIXES.put("adn", "and");
        FIXES.put("nad", "and");
        FIXES.put("thier", "their");
        FIXES.put("recieve", "receive");
        FIXES.put("seperate", "separate");
        FIXES.put("definately", "definitely");
        FIXES.put("becuase", "because");
        FIXES.put("beacuse", "because");
        FIXES.put("wich", "which");
        FIXES.put("alot", "a lot");
        FIXES.put("ure", "you're");

        // chat shorthand
        FIXES.put("u", "you");
        FIXES.put("ur", "your");
        FIXES.put("pls", "please");
        FIXES.put("plz", "please");
        FIXES.put("thx", "thanks");
        FIXES.put("ty", "thank you");
        FIXES.put("idk", "I don't know");
        FIXES.put("imo", "in my opinion");
        FIXES.put("rn", "right now");
    }

    private static final Pattern WORD = Pattern.compile("[A-Za-z']+");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern SPACE_BEFORE_PUNCT = Pattern.compile("\\s+([,.!?;:])");
    // Only a full stop or a question/exclamation mark followed by a capital, so hostnames in chat
    // ("mc.hypixel.net") are never split apart.
    private static final Pattern MISSING_SPACE = Pattern.compile("([!?]|\\.)(?=[A-Z])");

    public BritishChat() {
        super(AddonTemplate.UTILITY_CATEGORY, "british-chat",
            "Tidies the chat you send: capital letters, full stops, and optional grammar fixes for the words everyone mistypes.");
    }

    @EventHandler
    private void onSendMessage(SendMessageEvent event) {
        if (event.message == null || event.message.isEmpty()) return;

        // Anything that is already a command is left exactly as typed.
        if (event.message.startsWith("/")) return;

        String rewritten = rewrite(event.message);
        if (!rewritten.equals(event.message)) event.message = rewritten;
    }

    private String rewrite(String message) {
        String text = message;

        if (grammar.get()) text = fixGrammar(text);
        if (capitalise.get()) text = capitaliseSentences(text);

        // After capitalising, so "yo!look" has become "Yo!Look" and can now be split apart.
        if (capitalise.get()) text = MISSING_SPACE.matcher(text).replaceAll("$1 ");

        text = text.trim();
        if (fullStops.get()) text = addFullStop(text);

        // Vanilla chat is capped at 256 characters, and the punctuation we added can push a
        // max-length message over the edge.
        if (text.length() > 256) text = text.substring(0, 256);

        return text;
    }

    /** Replaces the known misspellings, then repairs the spacing around punctuation. */
    private String fixGrammar(String text) {
        Matcher matcher = WORD.matcher(text);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String word = matcher.group();
            String fixed = FIXES.get(word.toLowerCase());

            if (fixed == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(word));
                continue;
            }

            // Keep the capital the player typed: "Dont" -> "Don't", "dont" -> "don't".
            if (Character.isUpperCase(word.charAt(0)) && !fixed.isEmpty()) {
                fixed = Character.toUpperCase(fixed.charAt(0)) + fixed.substring(1);
            }

            matcher.appendReplacement(out, Matcher.quoteReplacement(fixed));
        }

        matcher.appendTail(out);

        String result = SPACES.matcher(out.toString()).replaceAll(" ");
        result = SPACE_BEFORE_PUNCT.matcher(result).replaceAll("$1");

        return result;
    }

    /** Uppercases the first letter of the message and of every sentence, plus a lone "i". */
    private String capitaliseSentences(String text) {
        StringBuilder out = new StringBuilder(text);
        boolean startOfSentence = true;

        for (int i = 0; i < out.length(); i++) {
            char c = out.charAt(i);

            if (Character.isLetterOrDigit(c)) {
                if (startOfSentence) {
                    out.setCharAt(i, Character.toUpperCase(c));
                    startOfSentence = false;
                }
            } else if (c == '.' || c == '!' || c == '?') {
                // A dot glued to a following lowercase letter is not the end of a sentence - it is
                // a hostname or a version number, and capitalising it would garble the address.
                boolean gluedLowercase = c == '.' && i + 1 < out.length()
                    && Character.isLowerCase(out.charAt(i + 1));

                startOfSentence = !gluedLowercase;
            }
        }

        // A standalone "i" is always the pronoun.
        if (lowercaseICorrect.get()) {
            Pattern loneI = Pattern.compile("(?<![A-Za-z'])i(?![A-Za-z'])");
            String fixed = loneI.matcher(out.toString()).replaceAll("I");
            return fixed;
        }

        return out.toString();
    }

    private String addFullStop(String text) {
        if (text.isEmpty()) return text;

        char last = text.charAt(text.length() - 1);
        if (last == '.' || last == '!' || last == '?' || last == ';' || last == ':'
            || last == ',' || last == '-' || last == ')' || last == '\u2026') return text;

        return text + ".";
    }
}
