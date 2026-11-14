package net.countercraft.movecraft.processing.worker;

import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
import net.countercraft.movecraft.processing.effects.Effect;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface WorldManagerWorker {

    public boolean hasUnfinishedWork();
    public void run();

    void startTask(Supplier<@Nullable Effect> task);

    public <T> CompletableFuture<T> submit(Supplier<T> task);
    public <T> CompletableFuture<T> submit(Callable<T> callable);

    /**
     * Keep this worker active while work is executing outside its own thread pool.
     * The future should complete only after its final callback has been queued.
     */
    void trackAsyncWork(CompletableFuture<?> future);

    /** Queue an effect to be executed by the worker on the main thread. */
    void submitEffectToWorldChanges(Effect effect);
    public void submitTaskToQueue(Supplier<@Nullable Effect> task);
    public void submitEffectToCurrentTasks(Runnable runnable);

    void dumpDebugInfo(Consumer<String> consumer);

    public static WorldManagerWorker getThreadLocalOrDefault(final WorldManagerWorkers defaultWorker) {
        final WorldManagerWorker worker = WorldManager.INSTANCE.getCurrentWorker();
        if (worker == null) {
            return defaultWorker.worker();
        } else {
            return worker;
        }

    }

    int getThreadCount();

    default boolean isBlocking() {
        return false;
    }
}
