package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A per-priority work-stealing pool used by WorldManager workers.
 *
 * CompletableFuture remains the public task abstraction. Tasks submitted from
 * a worker in this pool are submitted back to the same ForkJoinPool, allowing
 * ForkJoinPool's join compensation and work-stealing mechanisms to make
 * progress when parent tasks wait for child tasks.
 */
public class WindfarerThreadPool extends ForkJoinPool {

    private static final int MINIMUM_COMPENSATION_THREADS = 8;

    protected final Map<Class<?>, AtomicLong> statisticMap = new ConcurrentHashMap<>();
    protected final int overpressure;
    protected final String identifier;

    private final AtomicInteger activeTaskCount = new AtomicInteger();
    private final AtomicLong completedTaskCount = new AtomicLong();
    private final int configuredMaximumPoolSize;

    public WindfarerThreadPool(final int poolSize, final int overpressure, final String identifier) {
        this(normalizeParallelism(poolSize), overpressure, identifier, createMaximumPoolSize(poolSize));
    }

    private WindfarerThreadPool(
            final int parallelism,
            final int overpressure,
            final String identifier,
            final int maximumPoolSize
    ) {
        super(
                parallelism,
                createThreadFactory(identifier),
                createExceptionHandler(identifier),
                false,
                parallelism,
                maximumPoolSize,
                1,
                null,
                60L,
                TimeUnit.SECONDS
        );

        this.identifier = Objects.requireNonNull(identifier, "identifier");
        this.overpressure = overpressure;
        this.configuredMaximumPoolSize = maximumPoolSize;
    }

    private static int normalizeParallelism(int poolSize) {
        return Math.max(1, poolSize);
    }

    /**
     * Permit a bounded number of compensation threads when workers block on
     * joins. This is deliberately finite to avoid unbounded thread creation.
     */
    private static int createMaximumPoolSize(int poolSize) {
        int parallelism = normalizeParallelism(poolSize);
        int spareThreads = Math.max(MINIMUM_COMPENSATION_THREADS, parallelism);
        return Math.min(32767, parallelism + spareThreads);
    }

    private static ForkJoinWorkerThreadFactory createThreadFactory(String identifier) {
        AtomicInteger threadId = new AtomicInteger();
        return pool -> {
            ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            thread.setName(identifier + "-Worker-" + threadId.incrementAndGet());
            return thread;
        };
    }

    private static Thread.UncaughtExceptionHandler createExceptionHandler(String identifier) {
        return (thread, exception) -> {
            Bukkit.getLogger().severe("Uncaught exception in " + identifier + " thread " + thread.getName());
            exception.printStackTrace();
        };
    }

    public boolean hasCapacity() {
        if (Settings.Debug) {
            dumpDebugInformation(Bukkit.getLogger()::info);
        }

        return getCapacity() > 0;
    }

    public void dumpDebugInformation(Consumer<String> printer) {
        int activeTasks = activeTaskCount.get();
        long queuedTasks = getQueuedTasksEstimate();
        long completed = completedTaskCount.get();
        int parallelism = getParallelism();
        long total = completed + activeTasks;

        printer.accept("### Debug informations for threadpool: " + identifier + " ###");
        printer.accept("Parallelism: " + parallelism);
        printer.accept("Pool size (including compensation threads): " + getPoolSize());
        printer.accept("Maximum pool size: " + configuredMaximumPoolSize);
        printer.accept("Active submitted tasks: " + activeTasks);
        printer.accept("Active ForkJoin workers: " + getActiveThreadCount());
        printer.accept("Running ForkJoin workers: " + getRunningThreadCount());
        printer.accept("Queued tasks (estimated): " + queuedTasks);
        printer.accept("Queued submissions: " + getQueuedSubmissionCount());
        printer.accept("Completed submitted tasks: " + completed);
        printer.accept("Stolen tasks: " + getStealCount());

        printer.accept("Task statistics:");
        for (Map.Entry<Class<?>, AtomicLong> entry : statisticMap.entrySet()) {
            double percentage = total == 0L
                    ? 0.0D
                    : (double) entry.getValue().get() / (double) total;
            printer.accept("  " + entry.getKey().getName() + ": " + entry.getValue().get() + " (" + percentage + ")");
        }
    }

    /**
     * Returns the number of additional top-level tasks this worker may start.
     * The estimate includes active task bodies and both ForkJoin queue types.
     */
    public int getCapacity() {
        long occupied = (long) activeTaskCount.get() + getQueuedTasksEstimate();
        long capacity = (long) getParallelism() + overpressure;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, capacity - occupied));
    }

    private long getQueuedTasksEstimate() {
        return getQueuedTaskCount() + (long) getQueuedSubmissionCount();
    }

    /**
     * Compatibility metric for the existing WorldManager timeout diagnostics.
     * Counts task suppliers currently executing, including those waiting in join().
     */
    public int getActiveCount() {
        return activeTaskCount.get();
    }

    /**
     * Compatibility metric: the configured target parallelism, not the
     * temporary pool size including compensation workers.
     */
    public int getMaximumPoolSize() {
        return getParallelism();
    }

    /** Number of submitted task bodies that have returned or thrown. */
    public long getCompletedTaskCount() {
        return completedTaskCount.get();
    }

    /**
     * ForkJoinPool has no public queue-purge operation. Cancelled
     * CompletableFuture async tasks are skipped when their queued wrapper runs.
     */
    public void purge() {
        // Intentionally empty; ForkJoinPool owns and manages its work queues.
    }

    public <T> CompletableFuture<T> enqueueTask(final Supplier<T> task, final WorldManagerWorker worker) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(worker, "worker");
        statisticMap.computeIfAbsent(task.getClass(), ignored -> new AtomicLong()).incrementAndGet();

        // Preserve the WorldManager worker context for nested tasks and callbacks.
        return CompletableFuture.supplyAsync(() -> {
            activeTaskCount.incrementAndGet();

            // ForkJoinPool may execute a joined child task on the current worker thread.
            // Preserve the parent's ThreadLocal context when that nested task returns.
            WorldManagerWorker previousWorker = WorldManager.INSTANCE.getCurrentWorker();
            WorldManager.INSTANCE.setThreadLocalWorker(worker);
            try {
                return task.get();
            } finally {
                if (previousWorker == null) {
                    WorldManager.INSTANCE.removeThreadLocalWorker();
                } else {
                    WorldManager.INSTANCE.setThreadLocalWorker(previousWorker);
                }
                activeTaskCount.decrementAndGet();
                completedTaskCount.incrementAndGet();
            }
        }, this);
    }
}
