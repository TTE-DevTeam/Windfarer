package net.countercraft.movecraft.processing.worker.implementation;

public class NonBlockignWorldManagerWorker extends BaseWorldManagerWorker {
    public NonBlockignWorldManagerWorker(long maxTimeForWorldChanges, long maxTimeForMainThreadRequests, int poolSize, int poolOverpressure, String identifier) {
        super(maxTimeForWorldChanges, maxTimeForMainThreadRequests, poolSize, poolOverpressure, identifier);
    }
}
