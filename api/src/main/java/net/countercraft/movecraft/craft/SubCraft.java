package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.util.MathUtils;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataType;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SubCraft extends Craft {

    @NotNull
    Craft getParent();

    void setParent(@NotNull Craft parent);

    /**
     * @return true if the parent can neither move nor be read while this subcraft moves. Subcrafts which operate
     * independently of their parent (e.g. squadrons) return false.
     */
    default boolean locksParentWhileMoving() {
        return true;
    }

    /**
     * @return the state which is held on the parent for the whole lifetime of this subcraft
     * ({@link net.countercraft.movecraft.craft.state.CraftState#SUBCRAFT_MOVEMENT_PARENT}), or null if none is held
     */
    default @Nullable CraftStateHandle getParentStateHandle() {
        return null;
    }

    /**
     * Sets the state held on the parent. The subcraft owns it and releases it with {@link #releaseParentState()}.
     */
    default void setParentStateHandle(@Nullable CraftStateHandle handle) {
        throw new UnsupportedOperationException("This subcraft can't hold a state on its parent");
    }

    /**
     * Releases the state held on the parent, if any. Does nothing if there is none or it was released already.
     */
    default void releaseParentState() {
        CraftStateHandle handle = this.getParentStateHandle();
        if (handle != null) {
            handle.release();
        }
    }

    @Override
    default void removeUUIDMarkFromTile(TileState tile) {
        Craft parent = this.getParent();
        if (parent != null) {
            tile.getPersistentDataContainer().set(
                    MathUtils.KEY_CRAFT_UUID,
                    PersistentDataType.STRING,
                    parent.getUUID().toString()
            );
        } else {
            Craft.super.removeUUIDMarkFromTile(tile);
        }
    }
}
