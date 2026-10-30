package com.manasluflux.addon.modules;

/**
 * Item-frame phase paced for <b>NoCheatPlus</b>.
 *
 * <p>NCP's movement validation is a passability/SurvivalFly style check: it re-simulates where you
 * could have got to, so the attempt is fed in small increments (0.05 blocks) one packet per tick,
 * which keeps each step inside what a real movement tick could produce. It also gives the server a
 * chance to answer with a setback between steps, which is what the retry logic watches for.
 *
 * <p>Not verified against a live NCP server: this is the pacing that matches the check's shape, not
 * a proven bypass.
 */
public class NcpItemFramePhase extends ItemFramePhase {
    public NcpItemFramePhase() {
        super("ncp-itemframe",
            "Phases into the block you face (or behind an item frame) with small one-packet-per-tick pushes, paced for NCP.",
            0.05, 2);
    }
}
