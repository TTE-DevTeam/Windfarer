package net.countercraft.movecraft.async;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.state.CraftState;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The states held for one movement (translation/rotation) of a craft: the state of the moving craft itself
 * and, for subcrafts, the state on the direct parent. Released together, releasing is idempotent.
 */
public final class MovementStates {
    private final @NotNull Craft craft;
    private final @NotNull CraftStateHandle own;
    private final @Nullable CraftStateHandle parent;
    // false if the parent state belongs to the subcraft (for its whole lifetime) and is released by it
    private final boolean ownsParent;
    private final AtomicBoolean released = new AtomicBoolean();

    private MovementStates(@NotNull Craft craft, @NotNull CraftStateHandle own, @Nullable CraftStateHandle parent, boolean ownsParent) {
        this.craft = craft;
        this.own = own;
        this.parent = parent;
        this.ownsParent = ownsParent;
    }

    /** How long a movement waits for a busy craft before it is dropped */
    private static final long RESERVATION_TIMEOUT_SECONDS = 5;

    /**
     * Reserves {@link CraftState#MOVEMENT_CALCULATION} on the craft (and, for a subcraft, the state on its direct
     * parent). Does not block a thread: the returned future completes with the states as soon as the craft is free,
     * in the order of the requests, or exceptionally with a {@link java.util.concurrent.TimeoutException} if the craft
     * stayed busy. It may complete on any thread. Nothing is held in the latter case.
     * <p>
     * A subcraft which holds its parent for its whole lifetime (see {@link SubCraft#getParentStateHandle()}) uses that
     * state, otherwise the parent is reserved for this movement only.
     */
    public static @NotNull CompletableFuture<MovementStates> reserve(@NotNull Craft craft) {
        return craft.getStateTracker()
                .reserveStateAndWait(CraftState.MOVEMENT_CALCULATION, RESERVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenCompose(own -> reserveParent(craft, own));
    }

    private static CompletableFuture<MovementStates> reserveParent(@NotNull Craft craft, @NotNull CraftStateHandle own) {
        if (craft instanceof SubCraft subCraft && subCraft.locksParentWhileMoving()) {
            final CraftStateHandle lifetimeHandle = subCraft.getParentStateHandle();
            if (lifetimeHandle != null && !lifetimeHandle.isReleased()) {
                // The subcraft already holds its parent, e.g. since it was detected by a sign
                return CompletableFuture.completedFuture(new MovementStates(craft, own, lifetimeHandle, false));
            }
            final Craft parent = subCraft.getParent();
            if (parent != null && parent != craft) {
                return parent.getStateTracker()
                        .reserveStateAndWait(CraftState.SUBCRAFT_MOVEMENT_PARENT, RESERVATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .handle((parentHandle, throwable) -> {
                            if (throwable != null) {
                                own.release();
                                throw throwable instanceof CompletionException completion ? completion : new CompletionException(throwable);
                            }
                            return new MovementStates(craft, own, parentHandle, true);
                        });
            }
        }
        return CompletableFuture.completedFuture(new MovementStates(craft, own, null, true));
    }

    /**
     * Switches the moving craft from calculation to execution without a gap.
     *
     * @return false if the craft can not be executed right now (e.g. the shape is being read). Nothing changed then, try again later.
     */
    public boolean tryEnterExecution() {
        return this.own.tryTransitionTo(CraftState.MOVEMENT_EXECUTION);
    }

    /**
     * Ends the movement: releases the states and tells the craft that its movement is over.
     * Does nothing if called again.
     */
    public void release() {
        if (!this.released.compareAndSet(false, true)) {
            return;
        }
        this.own.release();
        if (this.parent != null && this.ownsParent) {
            this.parent.release();
        }
        MovementCompletion.movementFinished(this.craft);
    }
}
