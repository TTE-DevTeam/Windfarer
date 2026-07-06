package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.util.CompletableFutureTask;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 *
 */
public final class WorldManager implements Executor {

    public static final WorldManager INSTANCE = new WorldManager();
    private static final Runnable POISON = new Runnable() {
        @Override
        public void run() {/* No-op */}
        @Override
        public String toString(){
            return "POISON TASK";
        }
    };

    private final ConcurrentLinkedQueue<Effect> worldChanges = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Supplier<@Nullable Effect>> tasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> currentTasks = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pendingTasks = new AtomicInteger(0);
    private volatile boolean running = false;

    private WorldManager(){}

    public void run() {
        if(!Bukkit.isPrimaryThread()){
            throw new RuntimeException("WorldManager must be executed on the main thread.");
        }
        // If we have nothing to start and nothing to wait for, we can quit early :)
        if(tasks.isEmpty() && currentTasks.isEmpty() && worldChanges.isEmpty() && pendingTasks.get() == 0)
            return;
        running = true;
        // TODO: Allow the task to also supply lists of effects instead of a single one
        // TODO: Add a option on how long or how many tasks we can start
        // Issue is, all the collected effects will be run in the same tick...
        while(!tasks.isEmpty()){
            // DONE: Will this block our mainthread while the task is calculating?
            // => No, it simply builds a list of completableFutures, it waits later down the line (when polling from "currentTasks")
            final Supplier<@Nullable Effect> task = tasks.poll();
            startTask(task);
        }

        // Run all requests to main thread now!
        final long startTimeTasks = System.currentTimeMillis();
        while (!currentTasks.isEmpty()) {
            Runnable task = currentTasks.poll();
            if (task == POISON) {
                // Ensure this never goes below zero
                pendingTasks.updateAndGet(val -> Math.max(0, val -1));
            } else {
                task.run();
            }
            long timeElapsed = System.currentTimeMillis() - startTimeTasks;
            if (timeElapsed >= Settings.maxElapsedTimeForSyncTaskProcessing)
                break;
        }

        // process world updates on the main thread
        // DONE: Limit the amount of time a effect has to run, otherwise, all effects from "now" must run in the same tick!
        final long startTime = System.currentTimeMillis();
        Effect sideEffect;
        while((sideEffect = worldChanges.poll()) != null){
            sideEffect.run();
            long timeElapsed = System.currentTimeMillis() - startTime;
            if (timeElapsed >= Settings.maxElapsedTimeForWorldChanges)
                break;
        }
        // Once we are fully done, purge the worlds
        if (pendingTasks.get() == 0 && currentTasks.isEmpty() && tasks.isEmpty() && worldChanges.isEmpty()) {
            CachedMovecraftWorld.purge();
            running = false;
        }
    }

    private void startTask(Supplier<@Nullable Effect> task) {
        List<CompletableFuture<Effect>> inProgress = new ArrayList<>();
        pendingTasks.getAndIncrement();
        // NoOpTask tasks do not provide us any effects, we do not need to wait for those and can just start them normally
        if (!(task instanceof NoOpTask)) {
            inProgress.add(CompletableFuture.supplyAsync(task).whenComplete((effect, exception) -> {
                // Once the task is complete, we add poison to currentTasks, which also holds all requests to the main thread
                poison();
                if(exception != null){
                    exception.printStackTrace();
                } else if(effect != null) {
                    // And if there were no exceptions, we add all or THE effect the task produced
                    addEffect(effect);
                }
            }));
        } else {
            CompletableFuture.supplyAsync(task);
        }
    }

    private void addEffect(@Nullable Effect effect) {
        if (effect instanceof Effect.MultiEffect multiEffect) {
            final Iterator<Effect> iterator = multiEffect.iterator();
            while (iterator.hasNext()) {
                addEffect(iterator.next());
            }
        } else {
            worldChanges.add(effect);
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
        currentTasks.add(task);
        return task.join();
    }

    public void executeMain(@NotNull Runnable runnable){
        this.executeMain(() -> {
            runnable.run();
            return null;
        });
    }

    private void poison(){
        currentTasks.add(POISON);
    }

    public void submit(Runnable task){
        tasks.add(() -> {
            task.run();
            return null;
        });
    }

    public void submit(Supplier<@Nullable Effect> task){
        tasks.add(task);
    }

    public void submitAndRunNow(Supplier<@Nullable Effect> task){
        startTask(task);
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void execute(@NotNull Runnable command) {
        this.executeMain(command);
    }
}
