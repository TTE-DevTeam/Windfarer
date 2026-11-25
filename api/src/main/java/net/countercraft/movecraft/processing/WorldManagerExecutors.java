package net.countercraft.movecraft.processing;

import java.util.concurrent.ForkJoinPool;

/**
 * Shared executors for CPU-bound work which must not occupy a WorldManager worker
 * while waiting for child tasks.
 */
public final class WorldManagerExecutors {

    /**
     * The JVM-wide work-stealing pool. By default its parallelism is based on the
     * available processor count (normally max(1, processors - 1)). This is a shared
     * CPU budget, not a reservation of CPU capacity for Movecraft.
     */
    public static final ForkJoinPool WORK_STEALING_POOL = ForkJoinPool.commonPool();

    private WorldManagerExecutors() {
    }
}
