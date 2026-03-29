package net.countercraft.movecraft.async;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ExecutionException;

public abstract class AbstractAsyncTask extends BukkitRunnable {

    protected final Craft craft;

    protected AbstractAsyncTask(Craft craft) {
        this.craft = craft;
    }

    @Override
    public void run() {
        try {
            this.execute();
            this.submitCompletedTask();
        } catch(Exception exception) {
            System.err.println("Internal - Error - Processor thread encountered an error");
            exception.printStackTrace();
        }

    }

    protected abstract void submitCompletedTask();

    protected abstract void execute() throws InterruptedException, ExecutionException;

    protected Craft getCraft() {
        return craft;
    }
}
