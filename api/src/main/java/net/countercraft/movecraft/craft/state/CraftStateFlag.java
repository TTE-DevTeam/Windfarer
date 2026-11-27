package net.countercraft.movecraft.craft.state;

/**
 * Flags which are held by active {@link CraftState}s of a craft.
 * Every flag is counted, so multiple tasks may hold the same flag at the same time.
 */
public enum CraftStateFlag {
    /** The shape (hitbox) of the craft is being read and must not change. */
    READ_CRAFT_SHAPE,
    /** The shape (hitbox) of the craft is being modified, e.g. by creating a subcraft. */
    MODIFY_CRAFT_SHAPE,
    /** A movement (translation, rotation) is being calculated. */
    MOVEMENT_CALCULATION,
    /** A calculated movement is being applied to the world. */
    MOVEMENT_PROCESSING;

    private final int mask = 1 << this.ordinal();

    int mask() {
        return this.mask;
    }
}
