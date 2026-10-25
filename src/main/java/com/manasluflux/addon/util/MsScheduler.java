package com.manasluflux.addon.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Millisecond scheduler used by packet-flood style modules. A single daemon thread drives it,
 * so a module can space packets 1 ms apart instead of waiting for the next 50 ms game tick.
 *
 * Tasks must only send packets or touch volatile state - never read the world or the player.
 */
public final class MsScheduler implements AutoCloseable {
    private final ScheduledExecutorService executor;

    public MsScheduler(String name) {
        ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
        pool.setRemoveOnCancelPolicy(true);
        this.executor = pool;
    }

    /**
     * Runs the task after the given delay. Delays of zero or less run on the calling thread.
     */
    public void schedule(long delayMs, Runnable task) {
        if (executor.isShutdown()) return;

        if (delayMs <= 0) {
            task.run();
            return;
        }

        executor.schedule(task, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Runs the task over and over every interval. Exceptions are swallowed so the repeating task
     * is never cancelled by the pool - a broken tick must not kill the packet loop.
     */
    public void scheduleRepeating(long intervalMs, Runnable task) {
        if (executor.isShutdown()) return;

        executor.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Throwable ignored) {
                // keep the loop alive
            }
        }, Math.max(1, intervalMs), Math.max(1, intervalMs), TimeUnit.MILLISECONDS);
    }

    public void cancelAll() {
        ScheduledThreadPoolExecutor pool = (ScheduledThreadPoolExecutor) executor;
        pool.getQueue().clear();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
