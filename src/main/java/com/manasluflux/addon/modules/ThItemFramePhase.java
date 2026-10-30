package com.manasluflux.addon.modules;

/**
 * Item-frame phase paced for <b>Themis</b>.
 *
 * <p>Themis advertises Fly, Step, Speed, Blink and Timer checks. Blink is the one that matters
 * here: it looks for large jumps across several packets, so this variant pushes fewer, larger
 * increments (0.25 blocks) and finishes the distance in one tick, which reads as a single step
 * rather than a series of teleports. The verification window is longer, because a Step check
 * usually answers a tick or so later than NCP does.
 *
 * <p>Not verified against a live Themis server: this is the pacing that matches the advertised
 * checks, not a proven bypass.
 */
public class ThItemFramePhase extends ItemFramePhase {
    public ThItemFramePhase() {
        super("th-itemframe",
            "Phases into the block you face (or behind an item frame) in a few larger steps, paced for Themis' Blink/Step checks.",
            0.25, 3);
    }
}
