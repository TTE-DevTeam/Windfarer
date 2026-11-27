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
            EnumSet.complementOf(EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE))
    );

    /**
     * Pre-calculation of a movement (translation/rotation task). Blocks other movements and shape modifications.
     * Reading the shape stays allowed.
     */
    public static final CraftState MOVEMENT_CALCULATION = new CraftState(
            "MOVEMENT_CALCULATION",
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.READ_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING, CraftStateFlag.MODIFY_CRAFT_SHAPE)
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
     * Set on the direct parent of a subcraft while the subcraft moves. The parent can neither move nor be read
     * nor be modified from the outside, and only one subcraft per parent moves at a time
     * (sequential, so subcrafts can not overlap).
     * It holds the processing flag as well, so that nothing can be cut out of the parent meanwhile.
     */
    public static final CraftState SUBCRAFT_MOVEMENT_PARENT = new CraftState(
            "SUBCRAFT_MOVEMENT_PARENT",
            EnumSet.of(CraftStateFlag.MODIFY_CRAFT_SHAPE, CraftStateFlag.MOVEMENT_PROCESSING),
            EnumSet.of(CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING,
                    CraftStateFlag.READ_CRAFT_SHAPE, CraftStateFlag.MODIFY_CRAFT_SHAPE)
    );

    /**
     * The shape of the craft is changed from the outside (e.g. blocks of a sinking craft disintegrate).
     * Needs a craft which is neither moving nor read. Several of these may run at the same time.
     */
    public static final CraftState CRAFT_SHAPE_MODIFICATION = new CraftState(
            "CRAFT_SHAPE_MODIFICATION",
            EnumSet.of(CraftStateFlag.MODIFY_CRAFT_SHAPE),
            EnumSet.of(CraftStateFlag.READ_CRAFT_SHAPE, CraftStateFlag.MOVEMENT_CALCULATION, CraftStateFlag.MOVEMENT_PROCESSING)
    );

    /**
     * Compatibility state for the deprecated {@code setProcessing(true)}: holds and is blocked by everything.
     */
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
