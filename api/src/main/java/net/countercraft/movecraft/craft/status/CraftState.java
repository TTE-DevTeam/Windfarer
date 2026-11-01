package net.countercraft.movecraft.craft.status;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides thread-safe synchronization for concurrent operations
 * performed on a craft.
 *
 * CraftStatus manages access to craft resources based on the
 * synchronization rules defined by CraftTaskType.
 *
 * Multiple instances of the same task type may execute simultaneously,
 * as long as their compatibility requirements are satisfied.
 *
 * A task can only be acquired if:
 *
 * - All currently active task types allow the requested task type.
 * - The requested task type allows all currently active task types.
 * - All task types specified in the requested task's requiredMask
 *   are currently active.
 *
 * Task acquisition is blocking. Threads automatically wait until
 * their requested operation can safely be executed.
 *
 * Every successful acquisition returns a Guard. Closing the Guard
 * releases exactly one instance of the acquired task type.
 *
 * All modifications to the internal task registry are protected by
 * a fair ReentrantLock. This ensures thread-safe access and prevents
 * concurrent modifications of the internal state.
 *
 * The active task bitmask is volatile, allowing lock-free reads of
 * the currently active task types.
 *
 * Important:
 * The bitmask is only a snapshot of active task types. It does not
 * represent the number of active instances of each task type.
 *
 * @see CraftTaskType
 * @see CraftTaskTypes
 */
public final class CraftState {

    /**
     * Internal lock protecting all modifications to the active task registry.
     *
     * Fairness is enabled to reduce the risk of waiting threads being
     * indefinitely bypassed by newly arriving threads.
     */
    private final ReentrantLock lock = new ReentrantLock(true);

    /**
     * Condition used to suspend threads until their requested task
     * can safely be acquired.
     *
     * All waiting threads re-evaluate their acquisition conditions
     * whenever an active task is released.
     */
    private final Condition condition = lock.newCondition();

    /**
     * Registry of currently active task types.
     *
     * The map is indexed by the task identifier. Each entry stores
     * the corresponding task definition and the number of active
     * instances of that task type.
     *
     * Multiple instances of the same task type are represented
     * by a single entry with an incremented counter.
     *
     * Access is exclusively protected by {@link #lock}.
     */
    private final Map<String, ActiveTask> activeTasks = new HashMap<>();

    /**
     * Bitmask representing all currently active task types.
     *
     * A bit is set whenever at least one instance of its corresponding
     * task type is active.
     *
     * This field is volatile because it may be read without acquiring
     * the internal lock.
     */
    private volatile int activeMask = 0;

    /**
     * Internal representation of an active task type.
     *
     * @param type  Original task type definition.
     * @param count Number of currently active instances.
     */
    private record ActiveTask(CraftTaskType type, int count) {}

    /**
     * Checks whether the requested task type can currently be acquired.
     *
     * This method checks both directions of compatibility:
     *
     * - The requested task must allow every currently active task.
     * - Every currently active task must allow the requested task.
     *
     * Additionally, all task types specified in the requested task's
     * requiredMask must currently be active.
     *
     * The requiredMask check is performed against activeMask, which
     * represents task types rather than individual task instances.
     *
     * This method must only be called while holding {@link #lock}.
     *
     * @param requested Task type that should be acquired.
     * @return true if all synchronization requirements are satisfied.
     */
    private boolean canAcquire(CraftTaskType requested) {

        int requestedMask = requested.mask();

        // Check mutual compatibility with every currently active task type.
        for (ActiveTask active : activeTasks.values()) {

            int activeTaskMask = active.type().mask();

            // The requested task must allow the active task.
            // The active task must also allow the requested task.
            if ((requested.allowedMask() & activeTaskMask) == 0
                    || (active.type().allowedMask() & requestedMask) == 0) {
                return false;
            }
        }

        // Check whether every required task type is currently active.
        return (activeMask & requested.requiredMask())
                == requested.requiredMask();
    }

    /**
     * Acquires access for the specified task type.
     *
     * If the requested task cannot currently be executed, the calling
     * thread waits until another task is released and the acquisition
     * conditions are satisfied.
     *
     * Multiple threads may acquire the same task type simultaneously
     * if the compatibility rules allow it.
     *
     * The internal lock is released before the returned Guard is used,
     * allowing compatible operations to execute concurrently.
     *
     * @param task Task type to acquire.
     * @return Guard that releases the acquired task when closed.
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting for access.
     */
    public Guard acquire(CraftTaskType task) throws InterruptedException {

        if (task == null) {
            throw new IllegalArgumentException("Task must not be null");
        }

        lock.lockInterruptibly();

        try {

            // Wait until all compatibility and dependency requirements
            // are satisfied. The condition is checked again after every
            // wake-up to handle spurious wake-ups and concurrent changes.
            while (!canAcquire(task)) {
                condition.await();
            }

            // Register the newly acquired task.
            // Existing instances of the same task type increment the counter.
            activeTasks.compute(task.identifier(), (identifier, active) -> {

                if (active == null) {
                    return new ActiveTask(task, 1);
                }

                // Prevent different definitions from using the same identifier.
                if (!active.type().equals(task)) {
                    throw new IllegalArgumentException(
                            "Conflicting task definition: " + identifier
                    );
                }

                return new ActiveTask(task, active.count() + 1);
            });

            // Update the publicly visible active task bitmask.
            updateMask();

            // Return a handle that owns exactly one acquisition.
            return new Guard(this, task);

        } finally {
            // The internal lock must never remain held while the
            // actual craft operation is being executed.
            lock.unlock();
        }
    }

    /**
     * Attempts to acquire the specified task type without blocking.
     *
     * Unlike acquire(), this method never waits for other tasks to finish.
     * If the requested task cannot currently be acquired, null is returned.
     *
     * @param task Task type to acquire.
     * @return Guard if acquisition was successful, otherwise null.
     */
    public Guard tryAcquire(CraftTaskType task) {

        if (task == null) {
            throw new IllegalArgumentException("Task must not be null");
        }

        lock.lock();

        try {
            if (!canAcquire(task)) {
                return null;
            }

            activeTasks.compute(task.identifier(), (identifier, active) -> {

                if (active == null) {
                    return new ActiveTask(task, 1);
                }

                if (!active.type().equals(task)) {
                    throw new IllegalArgumentException(
                            "Conflicting task definition: " + identifier
                    );
                }

                return new ActiveTask(task, active.count() + 1);
            });

            updateMask();

            return new Guard(this, task);

        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases one active instance of the specified task type.
     *
     * If multiple instances of the same task type are active, only
     * the counter is decremented.
     *
     * The task type is removed from the registry only after its final
     * active instance has been released.
     *
     * All waiting threads are notified after the registry has been
     * updated so that they can re-evaluate their acquisition conditions.
     *
     * This method is exclusively called by Guard.close().
     *
     * @param task Task type to release.
     * @throws IllegalStateException if the task is not currently active.
     */
    private void release(CraftTaskType task) {

        lock.lock();

        try {

            ActiveTask active = activeTasks.get(task.identifier());

            // Prevent releasing a task that was never acquired or
            // whose definition does not match the registered task.
            if (active == null || !active.type().equals(task)) {
                throw new IllegalStateException(
                        "Task is not active: " + task.identifier()
                );
            }

            // Remove the task completely if this was its final instance.
            if (active.count() == 1) {
                activeTasks.remove(task.identifier());
            } else {
                // Otherwise, decrement the number of active instances.
                activeTasks.put(
                        task.identifier(),
                        new ActiveTask(task, active.count() - 1)
                );
            }

            // Recalculate the active task bitmask after the release.
            updateMask();

            // Wake up waiting threads. Each thread independently checks
            // whether its requested operation is now allowed.
            condition.signalAll();

        } finally {
            // Always release the internal lock, even if an exception occurs.
            lock.unlock();
        }
    }

    /**
     * Recalculates the active task bitmask from the current task registry.
     *
     * Each task type contributes its identifying bit to the resulting mask.
     * The number of active instances does not affect the resulting bitmask.
     *
     * This method must only be called while holding {@link #lock}.
     */
    private void updateMask() {

        int mask = 0;

        // Add the identifying bit of every currently active task type.
        for (ActiveTask active : activeTasks.values()) {
            mask |= active.type().mask();
        }

        // Publish the updated mask to threads reading it without the lock.
        activeMask = mask;
    }

    /**
     * Returns a snapshot of all currently active task types as a bitmask.
     *
     * A bit is set if at least one instance of the corresponding task
     * type is currently active.
     *
     * This method does not acquire the internal lock.
     *
     * @return Bitmask representing the currently active task types.
     */
    public int getActiveMask() {
        return activeMask;
    }

    /**
     * Checks whether at least one instance of the specified task type
     * is currently active.
     *
     * This method performs a lock-free check using the volatile
     * active task bitmask.
     *
     * @param task Task type to check.
     * @return true if at least one instance of this task type is active.
     */
    public boolean isActive(CraftTaskType task) {

        if (task == null) {
            throw new IllegalArgumentException("Task must not be null");
        }

        return (activeMask & task.mask()) != 0;
    }

    /**
     * Represents an acquired task and provides automatic release
     * through the AutoCloseable interface.
     *
     * Each Guard represents exactly one successful acquisition,
     * regardless of how many other instances of the same task type
     * are currently active.
     *
     * The Guard can safely be closed from multiple threads.
     * Only the first close() invocation releases the associated task.
     *
     * Guards should preferably be used with try-with-resources to
     * ensure that acquired tasks are always released.
     */
    public static final class Guard implements AutoCloseable {

        /**
         * CraftStatus instance responsible for managing the acquired task.
         */
        private final CraftState status;

        /**
         * Task type associated with this Guard.
         */
        private final CraftTaskType task;

        /**
         * Ensures that the associated task is released at most once.
         */
        private final AtomicBoolean closed = new AtomicBoolean(false);

        /**
         * Creates a new Guard for an acquired task.
         *
         * This constructor is private because Guards must only be created
         * by CraftStatus after a successful task acquisition.
         *
         * @param status CraftStatus instance managing the task.
         * @param task   Acquired task type.
         */
        private Guard(CraftState status, CraftTaskType task) {
            this.status = status;
            this.task = task;
        }

        /**
         * Releases the task associated with this Guard.
         *
         * The task is released only once, even if close() is called
         * multiple times or concurrently from different threads.
         *
         * After closing the Guard, the associated operation must no
         * longer access the protected craft resources.
         */
        @Override
        public void close() {

            // Atomic compare-and-set prevents multiple releases
            // of the same acquisition.
            if (closed.compareAndSet(false, true)) {
                status.release(task);
            }
        }
    }
}

