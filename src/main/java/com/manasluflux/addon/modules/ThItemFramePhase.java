package com.manasluflux.addon.modules;

/**
 * Frame-aware pearl phase paced for <b>Themis</b>.
 *
 * <p>Themis advertises Fly, Step, Speed, <b>Blink</b> and Timer checks. Blink is about large jumps
 * across packets, so this variant <b>does not spend a tick on a bare rotation packet</b>: the look
 * and the throw go out together, so there is no rotation-only packet for a check to pair with a
 * position change. It also waits longer (<b>14 ticks</b>) for a correction before moving to the next
 * point on the face, because a Step-style check tends to answer later than NCP does.
 *
 * <p>Not verified against a live Themis server: the aim points are geometry, the pacing is an
 * inference from the advertised checks.
 */
public class ThItemFramePhase extends ItemFramePhase {
    public ThItemFramePhase() {
        super("th-itemframe",
            "Pearl phase that throws at the uncovered edge of a face when an item frame is in the way, rotation and throw on one tick (Themis pacing).",
            0, 14);
    }
}
