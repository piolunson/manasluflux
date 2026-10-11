package com.manasluflux.addon.gui;

import com.manasluflux.addon.AddonTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared persistence for the Notepad and To Do GUI tabs. Everything is stored in one
 * NBT file next to Meteor's config so notes/tasks survive Minecraft restarts.
 */
public final class GuiNotes {
    public static final GuiNotes INSTANCE = new GuiNotes();

    private static final Path FILE = meteordevelopment.meteorclient.MeteorClient.FOLDER.toPath().resolve("manasluflux-gui-notes.nbt");

    public String notepad = "";
    public final List<String> pages = new ArrayList<>(List.of(""));

    public static class Task {
        public String text = "";
        public boolean done;

        public Task() {}

        public Task(String text, boolean done) {
            this.text = text;
            this.done = done;
        }
    }

    public final List<Task> tasks = new ArrayList<>();

    private GuiNotes() {}

    public void load() {
        if (Files.notExists(FILE)) return;
        try {
            CompoundTag tag = NbtIo.read(FILE);
            notepad = tag.getStringOr("notepad", "");

            pages.clear();
            ListTag pagesList = tag.getListOrEmpty("pages");
            for (int i = 0; i < pagesList.size(); i++) pages.add(pagesList.getStringOr(i, ""));
            if (pages.isEmpty()) pages.add("");

            tasks.clear();
            ListTag tasksList = tag.getListOrEmpty("tasks");
            for (int i = 0; i < tasksList.size(); i++) {
                CompoundTag t = tasksList.getCompoundOrEmpty(i);
                tasks.add(new Task(t.getStringOr("text", ""), t.getBooleanOr("done", false)));
            }
        } catch (IOException e) {
            AddonTemplate.LOG.warn("Failed to read ManasluFlux GUI notes", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            CompoundTag tag = new CompoundTag();
            tag.putString("notepad", notepad);

            ListTag pagesList = new ListTag();
            for (String p : pages) pagesList.add(StringTag.valueOf(p));
            tag.put("pages", pagesList);

            ListTag tasksList = new ListTag();
            for (Task t : tasks) {
                CompoundTag tTag = new CompoundTag();
                tTag.putString("text", t.text);
                tTag.putBoolean("done", t.done);
                tasksList.add(tTag);
            }
            tag.put("tasks", tasksList);

            NbtIo.write(tag, FILE);
        } catch (IOException e) {
            AddonTemplate.LOG.warn("Failed to save ManasluFlux GUI notes", e);
        }
    }
}
