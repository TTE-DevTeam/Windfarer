package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.state.CraftState;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds {@link CraftState#SUBCRAFT_DETECTION} on the parent of a subcraft while the subcraft is detected.
 * <p>
 * If detection succeeds, the state is handed over to the new subcraft ({@link #transferTo(SubCraft)}), which keeps it
 * for its whole lifetime. In all other cases it has to be {@link #release() released}. Both is safe to be done
 * multiple times and from any thread.
 */
public final class ParentDetectionLock {
    private final @NotNull Craft parent;
    private final @NotNull CraftStateHandle handle;
    private boolean transferred;

    private ParentDetectionLock(@NotNull Craft parent, @NotNull CraftStateHandle handle) {
        this.parent = parent;
        this.handle = handle;
    }

    /**
     * @return the lock, or null if the parent moves, is read or modified and can not be locked right now
     */
    public static @Nullable ParentDetectionLock tryAcquire(@NotNull Craft parent) {
        return parent.getStateTracker().tryAcquire(CraftState.SUBCRAFT_DETECTION)
                .map(handle -> new ParentDetectionLock(parent, handle))
                .orElse(null);
    }

    public @NotNull Craft getParent() {
        return this.parent;
    }

    /**
     * Turns the state into {@link CraftState#SUBCRAFT_MOVEMENT_PARENT} without a gap and gives it to the subcraft.
     *
     * @return false if the subcraft belongs to another parent, the lock was released already (e.g. detection took too long)
     * or the parent is being read right now. The lock is still held in the last case, release it.
     */
    public synchronized boolean transferTo(@NotNull SubCraft subcraft) {
        if (this.transferred || this.handle.isReleased() || subcraft.getParent() != this.parent) {
            return false;
        }
        if (!this.handle.tryTransitionTo(CraftState.SUBCRAFT_MOVEMENT_PARENT)) {
            return false;
        }
        subcraft.setParentStateHandle(this.handle);
        this.transferred = true;
        return true;
    }

    /**
     * Releases the state, unless it was handed over to a subcraft.
     */
    public synchronized void release() {
        if (!this.transferred) {
            this.handle.release();
        }
    }
}
