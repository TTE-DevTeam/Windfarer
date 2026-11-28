package net.countercraft.movecraft.async;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ExecutionException;

public abstract class AbstractAsyncTask extends BukkitRunnable {

    protected final Craft craft;
    private volatile Throwable executionFailure;

    protected AbstractAsyncTask(Craft craft) {
        this.craft = craft;
    }

    @Override
    public void run() {
        try {
            this.execute();
        } catch (Throwable exception) {
            executionFailure = exception;
            System.err.println("Internal - Error - Processor task failed");
            exception.printStackTrace();
        } finally {
            // AsyncManager owns the craft states of the movement after submission. Always notify it,
            // including on failure, so the craft is not left locked indefinitely.
            this.submitCompletedTask();
        }
    }

    public final Throwable getExecutionFailure() {
        return executionFailure;
    }

    protected abstract void submitCompletedTask();

    protected abstract void execute() throws InterruptedException, ExecutionException;

    protected Craft getCraft() {
        return craft;
    }
}
