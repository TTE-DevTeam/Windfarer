package net.countercraft.movecraft.craft.state;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Manages the {@link CraftState}s of a single craft.
 * <p>
 * Every flag has a counter of how many active {@link CraftStateHandle}s currently hold it, so several tasks can be
 * active at the same time. State changes are guarded by a short lock (check + change must be atomic), reads are lock-free.
 * <p>
 * Waiting reservations ({@link #reserveStateAndWait(CraftState)}) are served in order. A state which would take
 * flags that an earlier waiting reservation is blocked by is not granted before that reservation. This prevents
 * starvation by a constant stream of compatible tasks. Futures are always completed outside of the lock.
 */
public final class CraftStateTracker {
    private static final CraftStateFlag[] FLAGS = CraftStateFlag.values();

    private final Object lock = new Object();
    private final AtomicIntegerArray counts = new AtomicIntegerArray(FLAGS.length);
    // guarded by lock
    private final Set<CraftStateHandle> active = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Waiter> waiters = new ArrayList<>();
    private CraftStateHandle legacyHandle;

    private record Waiter(CraftState state, CraftStateHandle replacing, CompletableFuture<CraftStateHandle> future) {}

    // ------------------------------------------------------------------ queries (lock-free)

    /** @return true if no flag is held at all (the semantic of the legacy {@code isNotProcessing()}) */
    public boolean isIdle() {
        for (int i = 0; i < FLAGS.length; i++) {
            if (this.counts.get(i) != 0) {
                return false;
            }
        }
        return true;
    }

    /** @return true if a movement of the craft is being calculated or applied */
    public boolean isMoving() {
        return this.isActive(CraftStateFlag.MOVEMENT_CALCULATION) || this.isActive(CraftStateFlag.MOVEMENT_PROCESSING);
    }

    /** @return true if the craft is moving (see {@link #isMoving()}) or its shape is being modified */
    public boolean isMovingOrChangingShape() {
        return this.isMoving() || this.isActive(CraftStateFlag.MODIFY_CRAFT_SHAPE);
    }

    public boolean isActive(@NotNull CraftStateFlag flag) {
        return this.counts.get(flag.ordinal()) > 0;
    }

    public int count(@NotNull CraftStateFlag flag) {
        return this.counts.get(flag.ordinal());
    }

    /** Snapshot of all currently active handles, e.g. for debugging. */
    public @NotNull List<CraftStateHandle> getActiveHandles() {
        synchronized (this.lock) {
            return new ArrayList<>(this.active);
        }
    }

    // ------------------------------------------------------------------ acquiring

    /**
     * Tries to set the state right now.
     *
     * @return the handle, or empty if a flag the state is blocked by is held, or an earlier waiting reservation would be delayed
     */
    public @NotNull Optional<CraftStateHandle> tryAcquire(@NotNull CraftState state) {
        synchronized (this.lock) {
            if (!this.canAcquire(state, null) || this.delaysWaiter(state, this.waiters.size())) {
                return Optional.empty();
            }
            return Optional.of(this.grant(state));
        }
    }

    /**
     * Reserves the state and completes the returned future with the handle once it could be set.
     * No thread is blocked while waiting. Reservations are served in order; later compatible
     * tasks can not overtake a reservation they would delay.
     * Cancelling the future removes the reservation.
     */
    public @NotNull CompletableFuture<CraftStateHandle> reserveStateAndWait(@NotNull CraftState state) {
        return this.enqueue(new Waiter(state, null, new CompletableFuture<>()));
    }

    /**
     * Like {@link #reserveStateAndWait(CraftState)}, but completes exceptionally with a
     * {@link java.util.concurrent.TimeoutException} (and removes the reservation) after the given time.
     */
    public @NotNull CompletableFuture<CraftStateHandle> reserveStateAndWait(@NotNull CraftState state, long timeout, @NotNull TimeUnit unit) {
        return this.reserveStateAndWait(state).orTimeout(timeout, unit);
    }

    boolean tryTransition(@NotNull CraftStateHandle handle, @NotNull CraftState next) {
        List<Runnable> completions;
        synchronized (this.lock) {
            this.requireOwned(handle);
            if (handle.isReleased()) {
                return false;
            }
            if (!this.canAcquire(next, handle)) {
                return false;
            }
            this.applyState(handle, next);
            completions = this.processWaiters();
        }
        completions.forEach(Runnable::run);
        return true;
    }

    @NotNull CompletableFuture<CraftStateHandle> transitionAndWait(@NotNull CraftStateHandle handle, @NotNull CraftState next) {
        this.requireOwned(handle);
        return this.enqueue(new Waiter(next, handle, new CompletableFuture<>()));
    }

    private CompletableFuture<CraftStateHandle> enqueue(Waiter waiter) {
        // Registered first so a timeout/cancel of the future always removes the waiter
        waiter.future().whenComplete((handle, throwable) -> {
            if (throwable != null) {
                this.cancel(waiter);
            }
        });
        List<Runnable> completions;
        synchronized (this.lock) {
            this.waiters.add(waiter);
            completions = this.processWaiters();
        }
        completions.forEach(Runnable::run);
        return waiter.future();
    }

    private void cancel(Waiter waiter) {
        List<Runnable> completions;
        synchronized (this.lock) {
            if (!this.waiters.remove(waiter)) {
                return;
            }
            // Other waiters might have been held back by this one
            completions = this.processWaiters();
        }
        completions.forEach(Runnable::run);
    }

    // ------------------------------------------------------------------ releasing

    boolean release(@NotNull CraftStateHandle handle) {
        List<Runnable> completions;
        synchronized (this.lock) {
            if (!this.active.remove(handle)) {
                return false;
            }
            handle.markReleased();
            this.changeCounts(handle.getState().holdsMask(), -1);
            if (handle == this.legacyHandle) {
                this.legacyHandle = null;
            }
            completions = this.processWaiters();
        }
        completions.forEach(Runnable::run);
        return true;
    }

    /**
     * Emergency reset: releases all active handles. Handles of still running tasks become released, so
     * releasing them later is harmless. Waiting reservations stay and are served afterwards.
     *
     * @return the number of released handles
     */
    public int forceReset() {
        List<Runnable> completions;
        int released;
        synchronized (this.lock) {
            released = this.active.size();
            for (CraftStateHandle handle : this.active) {
                handle.markReleased();
            }
            this.active.clear();
            for (int i = 0; i < FLAGS.length; i++) {
                this.counts.set(i, 0);
            }
            this.legacyHandle = null;
            completions = this.processWaiters();
        }
        completions.forEach(Runnable::run);
        return released;
    }

    // ------------------------------------------------------------------ legacy bridge

    /**
     * Legacy {@code setProcessing(boolean)}: {@code true} sets {@link CraftState#LEGACY_PROCESSING}
     * (unconditionally, as the old flag did), {@code false} releases it. Other active states are not affected.
     */
    public void setLegacyProcessing(boolean processing) {
        if (!processing) {
            CraftStateHandle handle;
            synchronized (this.lock) {
                handle = this.legacyHandle;
            }
            if (handle != null) {
                handle.release();
            }
            return;
        }
        synchronized (this.lock) {
            if (this.legacyHandle == null) {
                this.legacyHandle = this.grant(CraftState.LEGACY_PROCESSING);
            }
        }
    }

    /** Legacy {@code setProcessingActiveIfAvailable()}: sets {@link CraftState#LEGACY_PROCESSING} if nothing at all is active. */
    public boolean tryAcquireLegacyProcessing() {
        synchronized (this.lock) {
            if (this.legacyHandle != null) {
                return false;
            }
            Optional<CraftStateHandle> handle = this.tryAcquire(CraftState.LEGACY_PROCESSING);
            handle.ifPresent(h -> this.legacyHandle = h);
            return handle.isPresent();
        }
    }

    // ------------------------------------------------------------------ internals (lock must be held)

    private void requireOwned(CraftStateHandle handle) {
        if (handle.tracker() != this) {
            throw new IllegalArgumentException("Handle belongs to another craft");
        }
    }

    /** Is the state blocked by flags which are held right now? Flags of {@code replacing} are not counted. */
    private boolean canAcquire(CraftState state, CraftStateHandle replacing) {
        int blocked = state.blockedByMask();
        int own = replacing == null ? 0 : replacing.getState().holdsMask();
        for (int i = 0; i < FLAGS.length; i++) {
            if ((blocked & FLAGS[i].mask()) == 0) {
                continue;
            }
            int held = this.counts.get(i) - ((own & FLAGS[i].mask()) != 0 ? 1 : 0);
            if (held > 0) {
                return false;
            }
        }
        return true;
    }

    /** Would setting the state take flags one of the first {@code count} waiters is blocked by? */
    private boolean delaysWaiter(CraftState state, int count) {
        for (int i = 0; i < count; i++) {
            if ((state.holdsMask() & this.waiters.get(i).state().blockedByMask()) != 0) {
                return true;
            }
        }
        return false;
    }

    private CraftStateHandle grant(CraftState state) {
        CraftStateHandle handle = new CraftStateHandle(this, state);
        this.active.add(handle);
        this.changeCounts(state.holdsMask(), 1);
        return handle;
    }

    private void applyState(CraftStateHandle handle, CraftState next) {
        this.changeCounts(handle.getState().holdsMask(), -1);
        this.changeCounts(next.holdsMask(), 1);
        handle.setState(next);
    }

    private void changeCounts(int mask, int delta) {
        for (int i = 0; i < FLAGS.length; i++) {
            if ((mask & FLAGS[i].mask()) != 0) {
                this.counts.addAndGet(i, delta);
            }
        }
    }

    /** Serves the waiting reservations in order. Returns the completions to run after the lock was released. */
    private List<Runnable> processWaiters() {
        List<Runnable> completions = new ArrayList<>();
        boolean progress = true;
        while (progress) {
            progress = false;
            Iterator<Waiter> iterator = this.waiters.iterator();
            int index = 0;
            while (iterator.hasNext()) {
                Waiter waiter = iterator.next();
                if (waiter.future().isDone()) {
                    iterator.remove();
                    progress = true;
                    continue;
                }
                CraftStateHandle replacing = waiter.replacing();
                if (replacing != null) {
                    if (replacing.isReleased()) {
                        iterator.remove();
                        completions.add(() -> waiter.future().completeExceptionally(
                                new IllegalStateException("Craft state was released while waiting for a transition")));
                        progress = true;
                        continue;
                    }
                    if (this.canAcquire(waiter.state(), replacing)) {
                        iterator.remove();
                        CraftState previous = replacing.getState();
                        this.applyState(replacing, waiter.state());
                        completions.add(() -> {
                            if (!waiter.future().complete(replacing)) {
                                // timed out/cancelled in the meantime: undo
                                this.undoTransition(replacing, previous);
                            }
                        });
                        progress = true;
                        continue;
                    }
                } else if (this.canAcquire(waiter.state(), null) && !this.delaysWaiter(waiter.state(), index)) {
                    iterator.remove();
                    CraftStateHandle handle = this.grant(waiter.state());
                    completions.add(() -> {
                        if (!waiter.future().complete(handle)) {
                            // timed out/cancelled in the meantime: do not leak the flags
                            handle.release();
                        }
                    });
                    progress = true;
                    continue;
                }
                index++;
            }
        }
        return completions;
    }

    private void undoTransition(CraftStateHandle handle, CraftState previous) {
        List<Runnable> completions;
        synchronized (this.lock) {
            if (handle.isReleased()) {
                return;
            }
            this.applyState(handle, previous);
            completions = this.processWaiters();
        }
        completions.forEach(Runnable::run);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("CraftStateTracker[");
        for (int i = 0; i < FLAGS.length; i++) {
            builder.append(i == 0 ? "" : ", ").append(FLAGS[i]).append('=').append(this.counts.get(i));
        }
        return builder.append(']').toString();
    }
}
