package net.countercraft.movecraft.craft.status;


/**
 * Central registry of task types used for craft synchronization.
 *
 * Each task type defines its own synchronization rules through bitmasks.
 * The bitmask values must be unique across all task types.
 *
 * This class only contains task definitions and does not perform
 * any synchronization itself.
 */
public final class CraftTaskTypes {

    // Bitmask identifiers for individual task types.
    public static final int BLOCK_DATA_READ_MASK = 0b001;
    public static final int SHAPE_CHANGE_MASK    = 0b010;
    public static final int MOVEMENT_MASK        = 0b100;

    /**
     * Allows multiple threads to read craft block data simultaneously.
     *
     * Block data reading cannot run alongside shape changes or movement.
     */
    public static final CraftTaskType BLOCK_DATA_READ =
            new CraftTaskType(
                    "block_data_read",
                    BLOCK_DATA_READ_MASK,
                    BLOCK_DATA_READ_MASK,
                    0b000
            );

    public static final CraftTaskType STATUS_CALCULATION =
            new CraftTaskType(
                    "status_calculation",
                    BLOCK_DATA_READ_MASK,
                    BLOCK_DATA_READ_MASK,
                    0b000
            );

    /**
     * Allows multiple threads to modify the craft shape simultaneously.
     *
     * Shape changes cannot run alongside block data reading or movement.
     */
    public static final CraftTaskType SHAPE_CHANGE =
            new CraftTaskType(
                    "shape_change",
                    SHAPE_CHANGE_MASK,
                    SHAPE_CHANGE_MASK,
                    0b000
            );

    /**
     * Represents exclusive movement of a craft.
     *
     * Movement cannot run alongside any other task type and therefore
     * does not allow any concurrent operations.
     */
    public static final CraftTaskType MOVEMENT =
            new CraftTaskType(
                    "movement",
                    MOVEMENT_MASK,
                    0b000,
                    0b000
            );

    /**
     * Prevents instantiation of this utility class.
     */
    private CraftTaskTypes() {
        throw new UnsupportedOperationException(
                "Utility class cannot be instantiated"
        );
    }
}

