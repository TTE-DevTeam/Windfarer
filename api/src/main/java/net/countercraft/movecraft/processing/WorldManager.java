package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.util.CompletableFutureTask;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.lang.ThreadLocal;

/**
 *
 */
// DONE: Refactor in oder to allow the existence of multiple thread pools with different priority levels
public final class WorldManager implements Executor {

    public static final WorldManager INSTANCE = new WorldManager();
    protected volatile boolean running = false;
    protected final ThreadLocal<WorldManagerWorker> currentWorker = new ThreadLocal<>();

    protected WorldManager(){
        currentWorker.set(null);
    }

    // TODO: Add a blocking task that keeps the main thread (whatever is calling this method) occupied, this allows for faster detection at the cost of occupying the main thread
    // Issue is that if the detection fails to deliver effects on time, the effects will have to wait 50ms until it is processed
    // This also applies to requests to the world such as getBlock() for example!
    public void run() {
        if (!Bukkit.isPrimaryThread()) {
            throw new RuntimeException("WorldManager must be executed on the main thread.");
        }

        WorldManagerWorkers[] workers = WorldManagerWorkers.values();
        boolean anyoneNeedsTheWorld = false;
        for (int i = 0; i < workers.length; i++) {
            WorldManagerWorker worker = workers[i].worker();
            if (worker.hasUnfinishedWork()) {
                this.running = true;
                anyoneNeedsTheWorld = true;
                worker.run();
            }
        }

        // Once we are fully done, purge the worlds
        if (!anyoneNeedsTheWorld) {
            CachedMovecraftWorld.purge();
            running = false;
        }
    }

    public <T> T executeMain(@NotNull Supplier<T> callable){
        if(!this.isRunning()){
            throw new RejectedExecutionException("WorldManager must be running to execute on the main thread");
        }
        if(Bukkit.isPrimaryThread()){
            throw new RejectedExecutionException("Cannot schedule on main thread from the main thread");
        }
        final WorldManagerWorker currentWorker = this.currentWorker.get();
        if (currentWorker == null) {
            throw new RejectedExecutionException("No current worker for this thread!");
        }
        var task = new CompletableFutureTask<>(callable);
        currentWorker.submitEffectToCurrentTasks(task);
        return task.join();
    }

    public void executeMain(@NotNull Runnable runnable){
        this.executeMain(() -> {
            runnable.run();
            return null;
        });
    }

    @Deprecated(forRemoval = true)
    public void submitHighPriority(Runnable task){
        WorldManagerWorkers.HIGH_PRIORITY.worker().submitTaskToQueue(() -> {
            task.run();
            return null;
        });
    }

    @Deprecated(forRemoval = true)
    public void submit(Runnable task){
        WorldManagerWorkers.NORMAL_PRIORITY.worker().submitTaskToQueue(() -> {
            task.run();
            return null;
        });
    }

    @Deprecated(forRemoval = true)
    public void submitHighPriority(Supplier<@Nullable Effect> task){
        WorldManagerWorkers.HIGH_PRIORITY.worker().submitTaskToQueue(task);
    }

    @Deprecated(forRemoval = true)
    public void submit(Supplier<@Nullable Effect> task){
        WorldManagerWorkers.NORMAL_PRIORITY.worker().submitTaskToQueue(task);
    }

    @Deprecated(forRemoval = true)
    public void submitAndRunNow(Supplier<@Nullable Effect> task){
        if (this.isRunning()) {
            WorldManagerWorkers.NORMAL_PRIORITY.worker().startTask(task);
        } else {
            this.submit(task);
        }
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

    public void submitAndRunNow(Supplier<@Nullable Effect> task, WorldManagerWorkers worker){
        if (this.isRunning()) {
            worker.worker().startTask(task);
        } else {
            this.submit(task, worker);
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        this.executeMain(command);
    }

    public void setThreadLocalWorker(WorldManagerWorker worker) {
        this.currentWorker.set(worker);
    }

    public void removeThreadLocalWorker() {
        //this.currentWorker.remove();
    }

    public @Nullable WorldManagerWorker getCurrentWorker() {
        return this.currentWorker.get();
    }

        
}
