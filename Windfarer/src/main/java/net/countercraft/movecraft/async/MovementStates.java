package net.countercraft.movecraft.async;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.state.CraftState;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The states held for one movement (translation/rotation) of a craft: the state of the moving craft itself
 * and, for subcrafts, the state on the direct parent. Released together, releasing is idempotent.
 */
public final class MovementStates {
    private final @NotNull CraftStateHandle own;
    private final @Nullable CraftStateHandle parent;

    private MovementStates(@NotNull CraftStateHandle own, @Nullable CraftStateHandle parent) {
        this.own = own;
        this.parent = parent;
    }

    /**
     * Tries to set {@link CraftState#MOVEMENT_CALCULATION} on the craft (and
     * {@link CraftState#SUBCRAFT_MOVEMENT_PARENT} on its direct parent, if it is a subcraft).
     *
     * @return the states, or null if the craft (or its parent) is busy. Nothing is held in that case.
     */
    public static @Nullable MovementStates tryAcquire(@NotNull Craft craft) {
        Optional<CraftStateHandle> own = craft.getStateTracker().tryAcquire(CraftState.MOVEMENT_CALCULATION);
        if (own.isEmpty()) {
            return null;
        }
        CraftStateHandle parentHandle = null;
        if (craft instanceof SubCraft subCraft) {
            Craft parent = subCraft.getParent();
            if (parent != null && parent != craft) {
                Optional<CraftStateHandle> acquired = parent.getStateTracker().tryAcquire(CraftState.SUBCRAFT_MOVEMENT_PARENT);
                if (acquired.isEmpty()) {
                    own.get().release();
                    return null;
                }
                parentHandle = acquired.get();
            }
        }
        return new MovementStates(own.get(), parentHandle);
    }

    /**
     * Switches the moving craft from calculation to execution without a gap.
     *
     * @return false if the craft can not be executed right now (e.g. the shape is being read). Nothing changed then, try again later.
     */
    public boolean tryEnterExecution() {
        return this.own.tryTransitionTo(CraftState.MOVEMENT_EXECUTION);
    }

    public void release() {
        this.own.release();
        if (this.parent != null) {
            this.parent.release();
        }
    }
}
