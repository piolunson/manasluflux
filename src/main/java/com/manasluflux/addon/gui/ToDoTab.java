package com.manasluflux.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * To Do tab in the Meteor ClickGUI top bar: a checkbox task list. Type an item,
 * press + to add it, tick the checkbox to complete it, - to remove. Done items
 * render green and sink to the bottom. Data lives in {@link GuiNotes}.
 */
public class ToDoTab extends Tab {
    public ToDoTab() {
        super("To Do");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new ToDoScreen(theme, this);
    }

    @Override
    public boolean isScreen(net.minecraft.client.gui.screens.Screen screen) {
        return screen instanceof ToDoScreen;
    }

    public static class ToDoScreen extends WindowTabScreen {
        private final GuiNotes notes = GuiNotes.INSTANCE;

        public ToDoScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
        }

        @Override
        public void initWidgets() {
            notes.load();

            // Add row: [text box] [+]
            WHorizontalList addRow = add(theme.horizontalList()).expandX().widget();
            WTextBox box = addRow.add(theme.textBox("", (text, c) -> true)).expandX().widget();
            WPlus plus = addRow.add(theme.plus()).widget();
            plus.action = () -> {
                if (!box.get().trim().isEmpty()) {
                    notes.tasks.add(new GuiNotes.Task(box.get().trim(), false));
                    notes.save();
                    reload();
                }
            };

            add(theme.horizontalSeparator()).expandX();

            // Sorted copy: pending keeps original order, done sinks to the bottom.
            List<GuiNotes.Task> pending = new ArrayList<>();
            List<GuiNotes.Task> done = new ArrayList<>();
            for (GuiNotes.Task t : notes.tasks) (t.done ? done : pending).add(t);

            WTable table = add(theme.table()).expandX().widget();
            table.horizontalSpacing = 8;
            table.verticalSpacing = 4;

            for (GuiNotes.Task t : pending) addTaskRow(table, t);
            for (GuiNotes.Task t : done) addTaskRow(table, t);

            if (notes.tasks.isEmpty()) {
                WLabel empty = table.add(theme.label("No tasks yet - type above and press +")).widget();
                empty.color(new Color(140, 140, 140));
                table.row();
            }

            // Footer summary
            if (!notes.tasks.isEmpty()) {
                long doneCount = notes.tasks.stream().filter(t -> t.done).count();
                WHorizontalList bottom = add(theme.horizontalList()).expandX().widget();
                WLabel summary = bottom.add(theme.label(doneCount + "/" + notes.tasks.size() + " done")).expandX().widget();
                summary.color(new Color(150, 150, 150));
            }
        }

        private void addTaskRow(WTable table, GuiNotes.Task t) {
            WMinus remove = table.add(theme.minus()).widget();
            remove.action = () -> {
                notes.tasks.remove(t);
                notes.save();
                reload();
            };

            WLabel label = table.add(theme.label(t.text)).expandCellX().widget();
            if (t.done) label.color(new Color(120, 200, 120));

            meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox checkbox = table.add(theme.checkbox(t.done)).widget();
            checkbox.action = () -> {
                t.done = checkbox.checked;
                notes.save();
                reload();
            };
            table.row();
        }

        @Override
        public boolean keyReleased(net.minecraft.client.input.KeyEvent event) {
            notes.save();
            return super.keyReleased(event);
        }

        @Override
        public void removed() {
            notes.save();
            super.removed();
        }
    }
}
