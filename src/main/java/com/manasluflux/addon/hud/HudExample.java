package com.manasluflux.addon.hud;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class HudExample extends HudElement {
    public static final HudElementInfo<HudExample> INFO = new HudElementInfo<>(AddonTemplate.HUD_GROUP, "example", "HUD element example.", HudExample::new);

    public HudExample() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        setSize(renderer.textWidth("ManasluFlux", true), renderer.textHeight(true));

        renderer.quad(x, y, getWidth(), getHeight(), Color.LIGHT_GRAY);
        renderer.text("ManasluFlux", x, y, Color.WHITE, true);
    }
}
