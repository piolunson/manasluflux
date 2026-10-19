package com.manasluflux.addon.gui;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.Tab;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.WindowTabScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPlus;
import net.minecraft.client.gui.screen.Screen;

/**
 * Notepad tab in the Meteor ClickGUI top bar: multiple free-text pages in one
 * draggable window. Data lives in {@link GuiNotes} and is saved to disk on page
 * change, +/- and screen close.
 */
public class NotepadTab extends Tab {
    public NotepadTab() {
        super("Notepad");
    }

    @Override
    public TabScreen createScreen(GuiTheme theme) {
        return new NotepadScreen(theme, this);
    }

    @Override
    public boolean isScreen(Screen screen) {
        return screen instanceof NotepadScreen;
    }

    public static class NotepadScreen extends WindowTabScreen {
        private final GuiNotes notes = GuiNotes.INSTANCE;
        private int page;

        public NotepadScreen(GuiTheme theme, Tab tab) {
            super(theme, tab);
        }

        @Override
        public void initWidgets() {
            notes.load();
            if (page >= notes.pages.size()) page = Math.max(0, notes.pages.size() - 1);

            // Page controls: -  "Page X / N"  +
            WHorizontalList top = add(theme.horizontalList()).expandX().widget();
            WMinus minus = top.add(theme.minus()).widget();
            minus.action = () -> {
                if (notes.pages.size() <= 1) return;
                notes.pages.remove(page);
                if (page >= notes.pages.size()) page = notes.pages.size() - 1;
                notes.save();
                reload();
            };
            top.add(theme.label("Page " + (page + 1) + " / " + notes.pages.size())).expandCellX().center();
            WPlus plus = top.add(theme.plus()).widget();
            plus.action = () -> {
                notes.pages.add("");
                page = notes.pages.size() - 1;
                notes.save();
                reload();
            };

            add(theme.horizontalSeparator()).expandX();

            // The editable note text for the current page.
            WTextBox box = add(theme.textBox(notes.pages.get(page), (text, c) -> true)).expandX().widget();
            box.action = () -> notes.pages.set(page, box.get());
            box.actionOnUnfocused = notes::save;
        }

        @Override
        public void removed() {
            notes.save();
            super.removed();
        }
    }
}
