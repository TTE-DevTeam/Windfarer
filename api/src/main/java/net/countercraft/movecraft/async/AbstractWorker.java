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
        WorldManager.INSTANCE.setThreadLocalWorker(this.worker);
        try {
            return callInternally();
        } finally {
            WorldManager.INSTANCE.removeThreadLocalWorker();
        }
    }

    protected abstract V callInternally();
}
