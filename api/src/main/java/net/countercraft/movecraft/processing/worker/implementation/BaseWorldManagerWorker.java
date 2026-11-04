package net.countercraft.movecraft.processing.worker.implementation;

import net.countercraft.movecraft.processing.NoOpTask;
import net.countercraft.movecraft.processing.WindfarerThreadPool;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BaseWorldManagerWorker implements WorldManagerWorker {

    protected static final Runnable POISON = new Runnable() {
        @Override
        public void run() {/* No-op */}
        @Override
        public String toString(){
            return "POISON TASK";
        }
    };

    // Value of < 0 means it has all the time it wants!
    protected final long maxTimeForWorldChanges;
    protected final long maxTimeForMainThreadRequests;

    protected final WindfarerThreadPool threadPool;

    protected final ConcurrentLinkedQueue<Effect> worldChanges = new ConcurrentLinkedQueue<>();
    protected final ConcurrentLinkedQueue<Supplier<@Nullable Effect>> tasks = new ConcurrentLinkedQueue<>();
    protected final BlockingQueue<Runnable> currentTasks = new LinkedBlockingQueue<>();
    protected final AtomicInteger pendingTasks = new AtomicInteger(0);
    protected final List<CompletableFuture<Effect>> inProgress = new ArrayList<>();

    public BaseWorldManagerWorker(long maxTimeForWorldChanges, long maxTimeForMainThreadRequests, final int poolSize, final int poolOverpressure, final String identifier) {
        this.maxTimeForWorldChanges = maxTimeForWorldChanges;
        this.maxTimeForMainThreadRequests = maxTimeForMainThreadRequests;
        this.threadPool = new WindfarerThreadPool(poolSize, poolOverpressure, identifier);
    }

    @Override
    public boolean hasUnfinishedWork() {
        return !(this.tasks.isEmpty() && this.currentTasks.isEmpty() && this.worldChanges.isEmpty() && this.pendingTasks.get() == 0 && inProgress.isEmpty() && threadPool.getActiveCount() == 0);
    }

    @Override
    public void run() {
        // Issue is, all the collected effects will be run in the same tick...
        if (this.threadPool.hasCapacity()) {
            startTasks();
        }
        processMainThreadRequests();
        processWorldChanges();
    }

    protected void processMainThreadRequests() {
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
            if (this.maxTimeForMainThreadRequests >= 0) {
                long timeElapsed = System.currentTimeMillis() - startTimeTasks;
                if (timeElapsed >= this.maxTimeForMainThreadRequests)
                    break;
            }
        }
    }

    protected void processWorldChanges() {
        // process world updates on the main thread
        // DONE: Limit the amount of time a effect has to run, otherwise, all effects from "now" must run in the same tick!
        final long startTime = System.currentTimeMillis();
        Effect sideEffect;
        while ((sideEffect = worldChanges.poll()) != null) {
            sideEffect.run();
            if (this.maxTimeForWorldChanges >= 0) {
                long timeElapsed = System.currentTimeMillis() - startTime;
                if (timeElapsed >= this.maxTimeForWorldChanges)
                    break;
            }
        }
    }

    protected void startTasks() {
        int allowedStarts = this.threadPool.getCapacity();
        while (!tasks.isEmpty() && allowedStarts > 0) {
            // DONE: Will this block our mainthread while the task is calculating?
            // => No, it simply builds a list of completableFutures, it waits later down the line (when polling from "currentTasks")
            final Supplier<@Nullable Effect> task = tasks.poll();
            startTask(task);
            allowedStarts--;
        }
    }

    @Override
    public void startTask(Supplier<@Nullable Effect> task) {
        pendingTasks.getAndIncrement();
        // NoOpTask tasks do not provide us any effects, we do not need to wait for those and can just start them normally
        if (!(task instanceof NoOpTask)) {
            final CompletableFuture<Effect> future = this.threadPool.enqueueTask(task);

            inProgress.add(future);

            // TODO: Does this return the same future?
            future.whenComplete((effect, exception) -> {
                 // We have finished running! Remove ourselves from inProgress
                inProgress.remove(future);
                // Once the task is complete, we add poison to currentTasks, which also holds all requests to the main thread
                poison();
                if(exception != null){
                    exception.printStackTrace();
                } else if(effect != null) {
                    // And if there were no exceptions, we add all or THE effect the task produced
                    addEffect(effect);
                }
            });
            /*inProgress.add(this.threadPool.enqueueTask(task).whenComplete((effect, exception) -> {
                // We have finished running! Remove ourselves from inProgress
                inProgress.remove(this);
                // Once the task is complete, we add poison to currentTasks, which also holds all requests to the main thread
                poison();
                if(exception != null){
                    exception.printStackTrace();
                } else if(effect != null) {
                    // And if there were no exceptions, we add all or THE effect the task produced
                    addEffect(effect);
                }
            }));*/
        } else 
            // TODO: Fix inProgress#remove()! The current implementation would remove the worldmanagerworker, not the future!
            inProgress.add(this.threadPool.enqueueTask(task).whenComplete((effect, exception) -> {
                // We have finished running! Remove ourselves from inProgress
                inProgress.remove(this);
                // Once the task is complete, we add poison to currentTasks, which also holds all requests to the main thread
                poison();
                if(exception != null){
                    exception.printStackTrace();
                }
            }));
        }
    }

    protected final void poison() {
        currentTasks.add(POISON);
    }

    @Override
    public void submit(Supplier<@Nullable Effect> task) {
        tasks.add(task);
    }

    @Override
    public void submitEffectToCurrentTasks(Runnable runnable) {
        currentTasks.add(runnable);
    }

    protected void addEffect(@Nullable Effect effect) {
        if (effect instanceof Effect.MultiEffect multiEffect) {
            final Iterator<Effect> iterator = multiEffect.iterator();
            while (iterator.hasNext()) {
                addEffect(iterator.next());
            }
        } else {
            worldChanges.add(effect);
        }
    }

}
