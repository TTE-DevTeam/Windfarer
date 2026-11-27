package net.countercraft.movecraft.craft.state;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One active instance of a {@link CraftState} on a craft, owned by exactly one task.
 * Releasing is idempotent, so releasing twice (e.g. in error handling) cannot corrupt the flag counters.
 */
public final class CraftStateHandle implements AutoCloseable {
    private final CraftStateTracker tracker;
    // Both guarded by tracker's lock for writes
    private volatile CraftState state;
    private volatile boolean released;

    CraftStateHandle(@NotNull CraftStateTracker tracker, @NotNull CraftState state) {
        this.tracker = tracker;
        this.state = state;
    }

    public @NotNull CraftState getState() {
        return this.state;
    }

    public boolean isReleased() {
        return this.released;
    }

    CraftStateTracker tracker() {
        return this.tracker;
    }

    void setState(@NotNull CraftState state) {
        this.state = state;
    }

    void markReleased() {
        this.released = true;
    }

    /**
     * Releases the state and its flags. Does nothing if already released.
     *
     * @return true if this call released the state
     */
    public boolean release() {
        return this.tracker.release(this);
    }

    @Override
    public void close() {
        this.release();
    }

    /**
     * Atomically replaces the state of this handle by the given one, without a gap in between
     * (e.g. movement calculation -> movement processing).
     * Flags held by the current state are not considered as blocking.
     * Waiting reservations do not block a transition, as the task is already running.
     *
     * @return true if the transition happened, false if blocked (the handle keeps its current state)
     */
    public boolean tryTransitionTo(@NotNull CraftState next) {
        return this.tracker.tryTransition(this, next);
    }

    /**
     * Like {@link #tryTransitionTo(CraftState)}, but waits (without blocking a thread) until the transition is possible.
     * The returned future completes with this handle, or exceptionally if the handle was released while waiting.
     * While waiting, the handle keeps its current state.
     */
    public @NotNull CompletableFuture<CraftStateHandle> transitionToAndWait(@NotNull CraftState next) {
        return this.tracker.transitionAndWait(this, next);
    }

    /**
     * Like {@link #transitionToAndWait(CraftState)}, but completes exceptionally with a
     * {@link java.util.concurrent.TimeoutException} after the given time. The handle keeps its current state then.
     */
    public @NotNull CompletableFuture<CraftStateHandle> transitionToAndWait(@NotNull CraftState next, long timeout, @NotNull TimeUnit unit) {
        return this.tracker.transitionAndWait(this, next).orTimeout(timeout, unit);
    }

    @Override
    public String toString() {
        return "CraftStateHandle[" + this.state.name() + (this.released ? ", released" : "") + "]";
    }
}
