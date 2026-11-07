package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.processing.worker.implementation.BlockingWorldManagerWorker;
import net.countercraft.movecraft.processing.worker.implementation.NonBlockignWorldManagerWorker;

import java.util.function.Consumer;

// TODO: Replace with registry later to allow for addons to define their own workers
public enum WorldManagerWorkers {

    HIGH_PRIORITY(new BlockingWorldManagerWorker(-1, 5000, Settings.highPriorityThreadCount, Settings.highPriorityPoolOverpressure, "HIGH_PRIORITY")),
    NORMAL_PRIORITY(new NonBlockignWorldManagerWorker(Settings.maxElapsedTimeForWorldChanges, Settings.maxElapsedTimeForSyncTaskProcessing, Settings.normalPriorityThreadCount, Settings.normalPriorityPoolOverpressure, "NORMAL_PRIORITY")),
    LOW_PRIORITY(new NonBlockignWorldManagerWorker(Settings.maxElapsedTimeForWorldChanges, Settings.maxElapsedTimeForSyncTaskProcessing, Settings.normalPriorityThreadCount, Settings.normalPriorityPoolOverpressure, "LOW_PRIORITY")),
    ;

    private final WorldManagerWorker worker;

    WorldManagerWorkers(WorldManagerWorker worker) {
        this.worker = worker;
    }

    WorldManagerWorker worker() {
        return worker;
    }

    public void sendDebugInfo(Consumer<String> consumer) {
        worker().dumpDebugInfo(consumer);
    }

}
