package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.util.CompletableFutureTask;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 *
 */
// TODO: Refactor in oder to allow the existence of multiple thread pools with different priority levels
public final class WorldManager implements Executor {

    public static final WorldManager INSTANCE = new WorldManager();
    protected volatile boolean running = false;
    protected WorldManagerWorker currentWorker = null;

    protected WorldManager(){

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
            WorldManagerWorker worker = workers[i];
            this.currentWorker = worker;
            if (worker.hasUnfinishedWork()) {
                anyoneNeedsTheWorld = true;
                worker.run();
            }
        }

        // Once we are fully done, purge the worlds
        if (!anyoneNeedsTheWorld) {
            CachedMovecraftWorld.purge();
            running = false;
            currentWorker = null;
        }
    }

    public <T> T executeMain(@NotNull Supplier<T> callable){
        if(!this.isRunning()){
            throw new RejectedExecutionException("WorldManager must be running to execute on the main thread");
        }
        if(Bukkit.isPrimaryThread()){
            throw new RejectedExecutionException("Cannot schedule on main thread from the main thread");
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


    public void submitHighPriority(Runnable task){
        WorldManagerWorkers.HIGH_PRIORITY.submit(() -> {
            task.run();
            return null;
        });
    }

    public void submit(Runnable task){
        WorldManagerWorkers.NORMAL_PRIORITY.submit(() -> {
            task.run();
            return null;
        });
    }

    public void submitHighPriority(Supplier<@Nullable Effect> task){
        WorldManagerWorkers.HIGH_PRIORITY.submit(task);
    }

    public void submit(Supplier<@Nullable Effect> task){
        WorldManagerWorkers.NORMAL_PRIORITY.submit(task);
    }

    public void submitAndRunNow(Supplier<@Nullable Effect> task){
        if (this.isRunning()) {
            WorldManagerWorkers.NORMAL_PRIORITY.startTask(task);
        } else {
            this.submit(task);
        }
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        this.executeMain(command);
    }
}
