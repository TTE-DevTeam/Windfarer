package net.countercraft.movecraft.craft.state;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Immutable description of a state a craft can be in while a task works on it.
 * <p>
 * A state defines
 * <ul>
 *     <li>{@link #blockedBy()}: flags which, if currently held by anyone, prevent this state from being set</li>
 *     <li>{@link #holds()}: flags which are held while this state is active</li>
 * </ul>
 * A state is compatible with itself (can be active multiple times in parallel) unless it lists
 * one of its own flags in {@code blockedBy}.
 * <p>
 * A state is only a description. Setting it on a craft yields a {@link CraftStateHandle}, see {@link CraftStateTracker}.
 */
public final class CraftState {
    /**
     * Status update of a craft: reads the craft shape and blocks everything else but other readers.
     */
    public static final CraftState STATUS_UPDATE = new CraftState(
            "STATUS_UPDATE",
            EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.MODIFY_CRAFT_SHAPE, CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING)
    );

    /**
     * Pre-calculation of a movement (translation/rotation task). Blocks other movements, shape modifications and
     * subcraft operations. Reading the shape stays allowed.
     */
    public static final CraftState MOVEMENT_CALCULATION = new CraftState(
            "MOVEMENT_CALCULATION",
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.READ_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING, CraftStateFlag.MODIFY_CRAFT_SHAPE,
                    CraftStateFlag.SUBCRAFT)
    );

    /**
     * The calculated movement is applied (new shape, map update commands). Entered by a transition from
     * {@link #MOVEMENT_CALCULATION}, which is why it keeps the calculation flag until the very end of the movement.
     * Nothing else may read or modify the craft meanwhile.
     */
    public static final CraftState MOVEMENT_EXECUTION = new CraftState(
            "MOVEMENT_EXECUTION",
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING, CraftStateFlag.MODIFY_CRAFT_SHAPE),
            EnumSet.allOf(CraftStateFlag.class)
    );

    /**
     * Set on the direct parent of a subcraft for the lifetime of the subcraft (it is cut out of the parent, moves and
     * is merged back). The parent can not move or be modified from the outside meanwhile, but it can be read.
     * Only one such subcraft per parent at a time (sequential, so subcrafts can not overlap).
     */
    public static final CraftState SUBCRAFT_MOVEMENT_PARENT = new CraftState(
            "SUBCRAFT_MOVEMENT_PARENT",
            EnumSet.of(CraftStateFlag.SUBCRAFT),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING,
                    CraftStateFlag.MODIFY_CRAFT_SHAPE, CraftStateFlag.SUBCRAFT)
    );

    /**
     * Set on the parent while a subcraft which does not come back (e.g. a torpedo) is cut out of it. Only held while
     * cutting out. The parent can not move or be modified from the outside meanwhile, but it can be read, and several
     * of these may run at the same time.
     */
    public static final CraftState SUBCRAFT_CUT_OUT = new CraftState(
            "SUBCRAFT_CUT_OUT",
            EnumSet.of(CraftStateFlag.SUBCRAFT),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING, CraftStateFlag.MODIFY_CRAFT_SHAPE)
    );

    /**
     * The shape of the craft is changed from the outside (e.g. blocks of a sinking craft disintegrate).
     * Needs a craft which is neither moving nor read. Several of these may run at the same time.
     */
    public static final CraftState CRAFT_SHAPE_MODIFICATION = new CraftState(
            "CRAFT_SHAPE_MODIFICATION",
            EnumSet.of(CraftStateFlag.MODIFY_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE, CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING,
                    CraftStateFlag.SUBCRAFT)
    );

    /**
     * A piston is moving a block on a craft, this might change it's shape
     * Needs a craft which is not moving or being changed. Several of these may run at the same time.
     * Technically, reading is also problematic, but this is for SINGLE blocks, so it might just be fine
     */
    public static final CraftState PISTON_MODIFICATION = new CraftState(
            "CRAFT_SHAPE_MODIFICATION",
            EnumSet.of(CraftStateFlag.MODIFY_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING)
    );

    /**
     * Burning fuel: the craft must not move or change while the fuel is searched and consumed, as the locations of
     * the burners are used. Changes the shape of nothing and reads no more than the burners, so reading stays allowed
     * for others. A movement may be calculated meanwhile, but not be applied (that needs the craft not to be read).
     */
    public static final CraftState FUEL_BURN = new CraftState(
            "FUEL_BURN",
            EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING, CraftStateFlag.MODIFY_CRAFT_SHAPE)
    );

    /**
     * Set on the parent of a subcraft while the subcraft is detected (the world is read and the player may still
     * cancel or fail). Nothing may move or change the parent meanwhile, reading is fine. It is turned into
     * {@link #SUBCRAFT_MOVEMENT_PARENT} as soon as the subcraft exists.
     */
    public static final CraftState SUBCRAFT_DETECTION = new CraftState(
            "SUBCRAFT_DETECTION",
            EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE, CraftStateFlag.SUBCRAFT),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING, CraftStateFlag.MODIFY_CRAFT_SHAPE)
    );

    /**
     * Compatibility state for the deprecated {@code Craft#setProcessing(true)}: holds and is blocked by everything.
     * <p>
     * REPLACEMENT: Do not use it. Define a state which describes what you do (what it holds, what blocks it), or use
     * one of the states of this class, and set it with {@link CraftStateTracker#tryAcquire(CraftState)} or
     * {@link CraftStateTracker#reserveStateAndWait(CraftState)}. See docs/craft-state.md.
     *
     * @deprecated Only exists for the old processing flag
     */
    @Deprecated(forRemoval = true)
    public static final CraftState LEGACY_PROCESSING = new CraftState(
            "LEGACY_PROCESSING",
            EnumSet.allOf(CraftStateFlag.class),
            EnumSet.allOf(CraftStateFlag.class)
    );

    private final String name;
    private final Set<CraftStateFlag> holds;
    private final Set<CraftStateFlag> blockedBy;
    private final int holdsMask;
    private final int blockedByMask;

    private CraftState(@NotNull String name, @NotNull Collection<CraftStateFlag> holds, @NotNull Collection<CraftStateFlag> blockedBy) {
        this.name = name;
        this.holds = Collections.unmodifiableSet(copy(holds));
        this.blockedBy = Collections.unmodifiableSet(copy(blockedBy));
        this.holdsMask = maskOf(this.holds);
        this.blockedByMask = maskOf(this.blockedBy);
    }

    public static @NotNull CraftState of(@NotNull String name, @NotNull Collection<CraftStateFlag> holds, @NotNull Collection<CraftStateFlag> blockedBy) {
        return new CraftState(name, holds, blockedBy);
    }

    private static Set<CraftStateFlag> copy(Collection<CraftStateFlag> flags) {
        return flags.isEmpty() ? EnumSet.noneOf(CraftStateFlag.class) : EnumSet.copyOf(flags);
    }

    private static int maskOf(Set<CraftStateFlag> flags) {
        int mask = 0;
        for (CraftStateFlag flag : flags) {
            mask |= flag.mask();
        }
        return mask;
    }

    public @NotNull String name() {
        return this.name;
    }

    /** Flags held while this state is active. */
    public @NotNull Set<CraftStateFlag> holds() {
        return this.holds;
    }

    /** Flags which prevent this state from being set. */
    public @NotNull Set<CraftStateFlag> blockedBy() {
        return this.blockedBy;
    }

    int holdsMask() {
        return this.holdsMask;
    }

    int blockedByMask() {
        return this.blockedByMask;
    }

    @Override
    public String toString() {
        return "CraftState[" + this.name + ", holds=" + this.holds + ", blockedBy=" + this.blockedBy + "]";
    }
}
