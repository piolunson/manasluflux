package com.manasluflux.addon.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Helper for "packet level" modes: a sliding one-second cap on how many packets a module may
 * send, plus a small queue that resends a packet when the server did not react in time.
 *
 * Both are cheap and allocation-light: the cap only stores timestamps of the last second and
 * the retry queue only holds entries for packets that are still unconfirmed.
 */
public final class PacketLevel {
    private final Deque<Long> sent = new ArrayDeque<>();
    private final List<Retry> retries = new ArrayList<>();

    /**
     * @return true when the module is still under its per-second cap (and books the packet),
     *         false when the packet has to be skipped this time.
     */
    public boolean allow(int maxPerSecond) {
        int limit = Math.max(1, maxPerSecond);
        prune();
        if (sent.size() >= limit) return false;
        sent.addLast(System.currentTimeMillis());
        return true;
    }

    /**
     * @return how many packets can still be sent in the current one-second window.
     */
    public int remaining(int maxPerSecond) {
        prune();
        return Math.max(0, Math.max(1, maxPerSecond) - sent.size());
    }

    /**
     * @return how many packets were sent in the last second.
     */
    public int sentLastSecond() {
        prune();
        return sent.size();
    }

    /**
     * Queues a packet that should be resent until the server is seen reacting.
     *
     * @param attempts   how many resends are allowed (0 disables resending)
     * @param delayMs    milliseconds to wait before the first resend (also used as the interval)
     * @param alreadyDone returns true once the packet obviously worked - the queue drops it then
     * @param resend      sends the packet again
     */
    public void retry(int attempts, int delayMs, BooleanSupplier alreadyDone, Runnable resend) {
        if (attempts <= 0 || delayMs <= 0) return;
        retries.add(new Retry(Math.min(attempts, 5), delayMs, alreadyDone, resend));
    }

    /**
     * Runs the retry queue. Call this once per tick (and from any instant packet callback).
     */
    public void tick() {
        if (retries.isEmpty()) return;

        long now = System.currentTimeMillis();
        for (Iterator<Retry> it = retries.iterator(); it.hasNext(); ) {
            Retry retry = it.next();
            if (now < retry.dueAt) continue;

            if (retry.alreadyDone.getAsBoolean() || retry.attemptsLeft <= 0) {
                it.remove();
                continue;
            }

            retry.attemptsLeft--;
            retry.resend.run();
            retry.dueAt = System.currentTimeMillis() + retry.intervalMs;

            if (retry.attemptsLeft <= 0) it.remove();
        }
    }

    /**
     * Forgets everything - call this when the module is turned on or off.
     */
    public void reset() {
        sent.clear();
        retries.clear();
    }

    /**
     * Drops all pending resends (used when the server confirms the action another way).
     */
    public void clearRetries() {
        retries.clear();
    }

    public int pendingRetries() {
        return retries.size();
    }

    private void prune() {
        long now = System.currentTimeMillis();
        while (!sent.isEmpty() && now - sent.peekFirst() >= 1000L) sent.pollFirst();
    }

    private static final class Retry {
        private int attemptsLeft;
        private final int intervalMs;
        private final BooleanSupplier alreadyDone;
        private final Runnable resend;
        private long dueAt;

        private Retry(int attempts, int delayMs, BooleanSupplier alreadyDone, Runnable resend) {
            this.attemptsLeft = attempts;
            this.intervalMs = delayMs;
            this.alreadyDone = alreadyDone;
            this.resend = resend;
            this.dueAt = System.currentTimeMillis() + delayMs;
        }
    }
}
