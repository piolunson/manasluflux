package com.manasluflux.addon.modules;

/**
 * Frame-aware pearl phase paced for <b>NoCheatPlus</b>.
 *
 * <p>NCP validates rotation and movement together, so this variant gives the server <b>two ticks of
 * its own rotation</b> before the throw instead of snapping the look and interacting in the same
 * tick, and gives a throw <b>8 ticks</b> to produce a position correction before calling it dead.
 * The frame bypass itself is not NCP specific - it is the aim point, which is the same code for
 * both modules.
 */
public class NcpItemFramePhase extends ItemFramePhase {
    public NcpItemFramePhase() {
        super("ncp-itemframe",
            "Pearl phase that throws at the uncovered edge of a face when an item frame is in the way, with its rotation sent ahead of the throw (NCP pacing).",
            2, 8);
    }
}
