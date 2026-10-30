package dev.leonetic.features.modules.world;

import net.minecraft.core.BlockPos;

/**
 * Programmatic access to the client's silent miner (SpeedMine), so other
 * modules (AutoMine, Nuker, ...) can request block breaks without simulating clicks.
 */
public interface MineApi {

    /** True when the miner is enabled and able to accept break requests. */
    boolean isAvailable();

    /**
     * Requests a silent break of the given block. Behaves like a player click:
     * an in-progress primary break is demoted to the secondary (double break)
     * slot when possible, otherwise aborted.
     *
     * @return true if the block is now (or already was) being mined.
     */
    boolean requestBreak(BlockPos pos);

    /** True if the given position is the primary or secondary mine target. */
    boolean isMining(BlockPos pos);

    /** The position currently held for rebreaking, or null if none. */
    BlockPos getRebreakPos();

    /** True if the primary slot can take a new target without aborting a break in progress. */
    boolean hasFreePrimary();

    /** True if the secondary (double break) slot can take a demoted break. */
    boolean hasFreeSecondary();

    /** True if the position is within the miner's configured break range. */
    boolean inMineRange(BlockPos pos);
}
