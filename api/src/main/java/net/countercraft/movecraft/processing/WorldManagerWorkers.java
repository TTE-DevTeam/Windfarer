package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.processing.worker.implementation.BlockingWorldManagerWorker;
import net.countercraft.movecraft.processing.worker.implementation.NonBlockignWorldManagerWorker;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public enum WorldManagerWorkers implements WorldManagerWorker {

    HIGH_PRIORITY(new BlockingWorldManagerWorker(-1, 5000, Settings.highPriorityThreadCount, Settings.highPriorityPoolOverpressure, "HIGH_PRIORITY")),
    NORMAL_PRIORITY(new NonBlockignWorldManagerWorker(Settings.maxElapsedTimeForWorldChanges, Settings.maxElapsedTimeForSyncTaskProcessing, Settings.normalPriorityThreadCount, Settings.normalPriorityPoolOverpressure, "NORMAL_PRIORITY")),
    LOW_PRIORITY(new NonBlockignWorldManagerWorker(Settings.maxElapsedTimeForWorldChanges, Settings.maxElapsedTimeForSyncTaskProcessing, Settings.normalPriorityThreadCount, Settings.normalPriorityPoolOverpressure, "LOW_PRIORITY")),
    ;

    private final WorldManagerWorker worker;

    WorldManagerWorkers(WorldManagerWorker worker) {
        this.worker = worker;
    }

    @Override
    public boolean hasUnfinishedWork() {
        return worker.hasUnfinishedWork();
    }

    @Override
    public void run() {
        worker.run();
    }

    @Override
    public void startTask(Supplier<@Nullable Effect> task) {
        worker.startTask(task);
    }

    @Override
    public void submit(Supplier<@Nullable Effect> task) {
        worker.submit(task);
    }

    @Override
    public void submitEffectToCurrentTasks(Runnable runnable) {
        worker.submitEffectToCurrentTasks(runnable);
    }
}
