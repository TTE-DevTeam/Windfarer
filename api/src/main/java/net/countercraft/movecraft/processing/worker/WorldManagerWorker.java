package net.countercraft.movecraft.processing.worker;

import net.countercraft.movecraft.processing.effects.Effect;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

public interface WorldManagerWorker {

    public boolean hasUnfinishedWork();
    public void run();

    void startTask(Supplier<@Nullable Effect> task);

    public void submit(Supplier<@Nullable Effect> task);
    public void submitEffectToCurrentTasks(Runnable runnable);

    void dumpDebugInfo(Consumer<String> consumer);
}
