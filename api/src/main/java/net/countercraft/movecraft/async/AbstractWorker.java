package net.countercraft.movecraft.async;

import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;

import java.util.concurrent.Callable;

public abstract class AbstractWorker<V> implements Callable<V> {

    private final WorldManagerWorker worker;

    protected AbstractWorker(WorldManagerWorker worker) {
        this.worker = worker;
    }

    @Override
    public V call() throws Exception {
        // AbstractWorkers are sometimes invoked inline by another worker. Preserve
        // that caller's context instead of clearing its ThreadLocal on return.
        WorldManagerWorker previousWorker = WorldManager.INSTANCE.getCurrentWorker();
        WorldManager.INSTANCE.setThreadLocalWorker(this.worker);
        try {
            return callInternally();
        } finally {
            if (previousWorker == null) {
                WorldManager.INSTANCE.removeThreadLocalWorker();
            } else {
                WorldManager.INSTANCE.setThreadLocalWorker(previousWorker);
            }
        }
    }

    protected abstract V callInternally();
}
