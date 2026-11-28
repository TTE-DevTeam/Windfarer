package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One use of a subcraft sign, from the click until the subcraft is gone again: the start point is marked as in use and
 * the parent craft is locked while the subcraft is detected.
 * <p>
 * It has to be {@link #finish() finished} exactly when everything is over, however that happened: when the detection
 * failed, when the action was aborted or when the subcraft was released after its movement. Finishing is safe to be
 * done multiple times and from any thread.
 */
public final class SubcraftSignOperation {
    private final @NotNull Set<MovecraftLocation> inUse;
    private final @NotNull MovecraftLocation startPoint;
    private final @Nullable ParentDetectionLock parentLock;
    private final AtomicBoolean finished = new AtomicBoolean();

    SubcraftSignOperation(@NotNull Set<MovecraftLocation> inUse, @NotNull MovecraftLocation startPoint, @Nullable ParentDetectionLock parentLock) {
        this.inUse = inUse;
        this.startPoint = startPoint;
        this.parentLock = parentLock;
    }

    /**
     * @return the lock on the parent craft during detection, or null if the sign does not belong to a craft
     */
    public @Nullable ParentDetectionLock getParentLock() {
        return this.parentLock;
    }

    public boolean isFinished() {
        return this.finished.get();
    }

    /**
     * Ends the operation: unmarks the start point and releases the parent lock, unless it was handed over to the subcraft.
     */
    public void finish() {
        if (!this.finished.compareAndSet(false, true)) {
            return;
        }
        if (this.parentLock != null) {
            this.parentLock.release();
        }
        this.inUse.remove(this.startPoint);
    }
}
