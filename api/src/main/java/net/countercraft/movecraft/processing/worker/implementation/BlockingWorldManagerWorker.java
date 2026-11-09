package net.countercraft.movecraft.processing.worker.implementation;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class BlockingWorldManagerWorker extends BaseWorldManagerWorker {

    public BlockingWorldManagerWorker(long maxTimeForWorldChanges, long maxTimeForMainThreadRequests, int poolSize, int poolOverpressure, String identifier) {
        super(maxTimeForWorldChanges, maxTimeForMainThreadRequests, poolSize, poolOverpressure, identifier);
    }

    @Override
    protected void processMainThreadRequests() {
        // We are not waiting for any task to return
        if (pendingTasks.get() <= 0) {
            return;
        }
        // process pre-queued tasks and their requests to the main thread
        eventLoop: while(true){
            var runningTasks = new ArrayList<Runnable>();
            try {
                runningTasks.add(currentTasks.poll(this.maxTimeForMainThreadRequests, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                continue;
            }
            final boolean isEmpty = runningTasks.isEmpty();
            // If the above poll() call reaches a timeout, it will return null! Thus the null check!
            final boolean firstIsNull = runningTasks.get(0) == null;
            if (isEmpty || firstIsNull) {
                Bukkit.getLogger().severe("WorldManager timed out on task query! Dumping " + inProgress.size() + " tasks. IsEmpty: <" + isEmpty + "> FirstIsNull: <" + firstIsNull + ">");
                inProgress.forEach(task -> task.cancel(true));
                inProgress.clear();
                Bukkit.getLogger().severe("Discarding <" + worldChanges.size() + "> queued world changes!");
                worldChanges.clear();
                currentTasks.clear();
                pendingTasks.set(0);
                break;
            }
            currentTasks.drainTo(runningTasks);
            for (var runnable : runningTasks){
                if (runnable == POISON){
                    if (pendingTasks.decrementAndGet() <= 0){
                        pendingTasks.set(0);
                        break eventLoop;
                    }
                }
                runnable.run();
            }
        }
    }

    @Override
    public boolean isBlocking() {
        return true;
    }
}
