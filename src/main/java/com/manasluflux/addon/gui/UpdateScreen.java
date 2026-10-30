package com.manasluflux.addon.gui;

import com.manasluflux.addon.update.UpdateChecker;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;

/**
 * The update window: installed vs latest version, one line of live state (download percent or
 * the force countdown) and the buttons. Opened automatically when a new version is found, and
 * by {@code .mfupdate}.
 */
public class UpdateScreen extends WindowScreen {
    private final UpdateChecker updater = UpdateChecker.INSTANCE;

    private WLabel versionsLabel;
    private WLabel statusLabel;
    private WButton updateButton;

    public UpdateScreen() {
        super(GuiThemes.get(), "ManasluFlux Update");
    }

    @Override
    public void initWidgets() {
        versionsLabel = add(theme.label(versionsText())).expandX().widget();
        statusLabel = add(theme.label(updater.statusLine())).expandX().widget();

        WHorizontalList buttons = add(theme.horizontalList()).expandX().widget();

        updateButton = buttons.add(theme.button(updater.buttonLabel())).expandX().widget();
        updateButton.action = () -> {
            if (updater.hasUpdate()) updater.startUpdate();
            else updater.checkNow();
        };

        buttons.add(theme.button("Check now")).widget().action = updater::checkNow;
        buttons.add(theme.button("Later")).widget().action = this::close;
    }

    @Override
    public void tick() {
        super.tick();

        if (versionsLabel != null) versionsLabel.set(versionsText());
        if (statusLabel != null) statusLabel.set(updater.statusLine());
        if (updateButton != null) updateButton.set(updater.buttonLabel());
    }

    private String versionsText() {
        String installed = updater.installedVersion();
        String latest = updater.latestVersion();

        if (latest.equals(installed)) return "Installed v" + installed + " - latest v" + latest;
        return "Installed v" + installed + "  ->  v" + latest;
    }
}
