package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.util.CompletableFutureTask;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Coordinates access to world data from Windfarer's worker pools. */
public final class WorldManager implements Executor {

    public static final WorldManager INSTANCE = new WorldManager();

    protected volatile boolean running = false;
    protected final ThreadLocal<WorldManagerWorker> currentWorker = new ThreadLocal<>();

    private volatile Plugin plugin;
    private volatile long nextCachePurgeNanos;

    protected WorldManager() {
        currentWorker.set(null);
    }

    public void setPlugin(@Nullable Plugin plugin) {
        this.plugin = plugin;
    }

    @Nullable
    public Plugin getPlugin() {
        return plugin;
    }

    public void run() {
        if (!Bukkit.isPrimaryThread()) {
            throw new RuntimeException("WorldManager must be executed on the main thread.");
        }

        WorldManagerWorkers[] workers = WorldManagerWorkers.values();
        boolean anyoneNeedsTheWorld = false;
        for (WorldManagerWorkers workerEntry : workers) {
            WorldManagerWorker worker = workerEntry.worker();
            if (worker.hasUnfinishedWork()) {
                running = true;
                anyoneNeedsTheWorld = true;
                worker.run();
            }
        }

        // Expire idle chunk handles independently of whether crafts are active.
        long now = System.nanoTime();
        if (now >= nextCachePurgeNanos) {
            nextCachePurgeNanos = now + TimeUnit.SECONDS.toNanos(1);
            CachedMovecraftWorld.purgeExpired();
        }

        if (!anyoneNeedsTheWorld) {
            running = false;
        }
    }

    public <T> T executeMain(@NotNull Supplier<T> callable) {
        if (!isRunning()) {
            throw new RejectedExecutionException("WorldManager must be running to execute on the main thread");
        }
        if (Bukkit.isPrimaryThread()) {
            throw new RejectedExecutionException("Cannot schedule on main thread from the main thread");
        }
        WorldManagerWorker worker = currentWorker.get();
        if (worker == null) {
            throw new RejectedExecutionException("No current worker for this thread!");
        }

        CompletableFutureTask<T> task = new CompletableFutureTask<>(callable);
        worker.submitEffectToCurrentTasks(task);
        return task.join();
    }

    /**
     * Dispatches a callback to the server thread without depending on a worker's
     * blocking request loop. This is safe for CompletableFuture continuations,
     * which may execute on the thread registering the continuation when already complete.
     */
    public <T> CompletableFuture<T> executeMainAsync(@NotNull Supplier<T> callable) {
        if (Bukkit.isPrimaryThread()) {
            try {
                return CompletableFuture.completedFuture(callable.get());
            } catch (Throwable error) {
                return failedFuture(error);
            }
        }

        Plugin currentPlugin = plugin;
        if (currentPlugin == null || !currentPlugin.isEnabled()) {
            return failedFuture(new RejectedExecutionException("Windfarer is not enabled."));
        }

        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            Bukkit.getScheduler().runTask(currentPlugin, () -> {
                try {
                    result.complete(callable.get());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                }
            });
        } catch (Throwable error) {
            result.completeExceptionally(error);
        }
        return result;
    }

    public void executeMain(@NotNull Runnable runnable) {
        executeMain(() -> {
            runnable.run();
            return null;
        });
    }

    @Deprecated(forRemoval = true)
    public void submitHighPriority(Runnable task) {
        submit(task, WorldManagerWorkers.HIGH_PRIORITY);
    }

    @Deprecated(forRemoval = true)
    public void submit(Runnable task) {
        submit(task, WorldManagerWorkers.LOW_PRIORITY);
    }

    @Deprecated(forRemoval = true)
    public void submitHighPriority(Supplier<@Nullable Effect> task) {
        submit(task, WorldManagerWorkers.HIGH_PRIORITY);
    }

    @Deprecated(forRemoval = true)
    public void submit(Supplier<@Nullable Effect> task) {
        submit(task, WorldManagerWorkers.LOW_PRIORITY);
    }

    @Deprecated(forRemoval = true)
    public void submitAndRunNow(Supplier<@Nullable Effect> task) {
        submitAndRunNow(task, WorldManagerWorkers.LOW_PRIORITY);
    }

    public void submit(Supplier<@Nullable Effect> task, WorldManagerWorkers worker) {
        worker.worker().submitTaskToQueue(task);
    }

    public void submit(Runnable task, WorldManagerWorkers worker) {
        worker.worker().submitTaskToQueue(() -> {
            task.run();
            return null;
        });
    }

    public void submitAndRunNow(Supplier<@Nullable Effect> task, WorldManagerWorkers worker) {
        if (isRunning()) {
            worker.worker().startTask(task);
        } else {
            submit(task, worker);
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        executeMain(command);
    }

    public void setThreadLocalWorker(WorldManagerWorker worker) {
        currentWorker.set(worker);
    }

    public void removeThreadLocalWorker() {
        currentWorker.remove();
    }

    @Nullable
    public WorldManagerWorker getCurrentWorker() {
        return currentWorker.get();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
