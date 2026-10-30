package com.manasluflux.addon.modules;

import com.manasluflux.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Vom extends Module {
    private static final int[] D = {125, 56, 105, 11, 18, 102, 69, 46, 49, 102, 56, 48};
    private static final int[] K = {43, 93, 17};

    public Vom() {
        super(AddonTemplate.CLIENT_SIDE_CATEGORY, "VOM", "");
    }

    @Override
    public void onActivate() {
        if (mc.player == null) return;

        char[] c = new char[D.length];
        for (int i = 0; i < D.length; i++) c[i] = (char) (D[i] ^ K[i % K.length]);

        mc.player.networkHandler.sendChatMessage(new String(c));
    }
}
