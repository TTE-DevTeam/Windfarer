package net.countercraft.movecraft.craft.status;


/**
 * Defines a specific type of operation that can be performed on a craft.
 *
 * Each task type is identified by a unique string identifier and a
 * dedicated bitmask. The allowedMask and requiredMask properties define
 * how this task interacts with other concurrently running tasks.
 *
 * Synchronization rules:
 *
 * - mask:
 *   Unique bit identifying this task type. Each task type must use
 *   exactly one bit, and no two task types may share the same bit.
 *
 * - allowedMask:
 *   Bitmask containing all task types that may execute concurrently
 *   with this task. Compatibility must be mutual: both task types
 *   must allow each other.
 *
 * - requiredMask:
 *   Bitmask containing all task types that must already be active
 *   before this task can be acquired.
 *
 * Task types should generally be defined as shared static constants
 * to ensure consistent synchronization rules throughout the application.
 *
 * The record is immutable and uses value-based equality, making it
 * suitable for sharing between multiple threads.
 *
 * @param identifier  Unique, human-readable identifier of this task type.
 * @param mask        Unique single-bit identifier of this task type.
 * @param allowedMask Bitmask of task types allowed to execute concurrently.
 * @param requiredMask Bitmask of task types required to be active.
 */
public record CraftTaskType(
        String identifier,
        int mask,
        int allowedMask,
        int requiredMask
) {

    /**
     * Validates the task type definition.
     *
     * Ensures that the identifier is valid, the task has exactly one
     * identifying bit and that the task is allowed to execute alongside
     * itself. A task type must always be compatible with other instances
     * of the same task type.
     *
     * @throws IllegalArgumentException if the task definition is invalid.
     */
    public CraftTaskType {

        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(
                    "Task identifier must not be null or blank"
            );
        }

        if (mask <= 0 || Integer.bitCount(mask) != 1) {
            throw new IllegalArgumentException(
                    "Task mask must contain exactly one bit"
            );
        }

        if ((allowedMask & mask) == 0) {
            throw new IllegalArgumentException(
                    "A task type must allow itself"
            );
        }

        if ((requiredMask & mask) != 0) {
            throw new IllegalArgumentException(
                    "A task type cannot require itself"
            );
        }
    }
}
