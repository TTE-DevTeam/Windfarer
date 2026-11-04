package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class WindfarerThreadPool extends ThreadPoolExecutor {

    protected Map<Class<?>, AtomicLong> statisticMap = new ConcurrentHashMap<>();
    protected final int overpressure;
    protected final String identifier;

    public WindfarerThreadPool(final int poolSize, final int overpressure, final String identifier) {
        super(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
            private final AtomicInteger id = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName(identifier + "-Worker-" + id.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });

        this.identifier = identifier;
        this.overpressure = overpressure;
    }

    public boolean hasCapacity() {
        if (Settings.Debug) {
            dumpDebugInformation(Bukkit.getLogger()::info);
        }

        return getCapacity() > 0;
    }

    public void dumpDebugInformation(Consumer<String> printer) {
        int active = this.getActiveCount();
        int queued = this.getQueue().size();
        long completed = this.getCompletedTaskCount();
        int workers = this.getMaximumPoolSize();
        int freeWorkers = workers - active;

        printer.accept("### Debug informations for  threadpool: " + identifier + " ###");
        printer.accept("Active: " + active);
        printer.accept("Queued: " + queued);
        printer.accept("Completed: " + completed);
        printer.accept("Free workers: " + freeWorkers);

        printer.accept("Task statistics: ");
        final long total = completed + active;
        for (Map.Entry<Class<?>, AtomicLong> entry : this.statisticMap.entrySet()) {
            double pct = entry.getValue().get() / total;
            printer.accept("  " + entry.getKey().getName() + ": " + entry.getValue() + " (" + pct + ")");
        }
    }

    public int getCapacity() {
        int taskOccupation = (this.getActiveCount() + this.getQueue().size());
        int capacity = this.getMaximumPoolSize() + this.overpressure;

        return Math.max(capacity - taskOccupation, 0);
    }

    public CompletableFuture<Effect> enqueueTask(final Supplier<Effect> task, final WorldManagerWorker worker) {
        this.statisticMap.computeIfAbsent(task.getClass(), k -> new AtomicLong(0)).incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                WorldManager.INSTANCE.setThreadLocalWorker(worker);
                return task.get();
            } finally {
                WorldManager.INSTANCE.removeThreadLocalWorker();
            }
        }, this);
    }

}
