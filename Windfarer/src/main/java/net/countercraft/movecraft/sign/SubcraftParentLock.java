package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.state.CraftState;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

final class SubcraftParentLock {
    private SubcraftParentLock() {
    }

    /**
     * Sets {@link CraftState#SUBCRAFT_MOVEMENT_PARENT} on the parent of a freshly detected subcraft. The subcraft holds it
     * until it is released (after its parent got the blocks back), so the parent can neither move nor be read meanwhile.
     * <p>
     * Must be called on the main thread right before the subcraft is cut out of its parent.
     * If the parent is busy, the player is informed and the subcraft is released again.
     * // TODO: Instead of aborting, delay the execution until the parent is free (reserveStateAndWait)
     *
     * @return true if the parent is locked, false if the action has to be aborted
     */
    static boolean acquire(@NotNull SubCraft subcraft, @Nullable Entity interactor, @Nullable ParentDetectionLock detectionLock) {
        final Craft parent = subcraft.getParent();
        // The parent was locked while the subcraft was detected, the lock turns into the lifetime state of the subcraft
        if (detectionLock != null && detectionLock.getParent() == parent) {
            if (detectionLock.transferTo(subcraft)) {
                return true;
            }
            detectionLock.release();
            abort(subcraft, interactor);
            return false;
        }
        // Parent differs from the one of the sign (or there was none): lock it now
        if (detectionLock != null) {
            detectionLock.release();
        }
        Optional<CraftStateHandle> handle = parent.getStateTracker().tryAcquire(CraftState.SUBCRAFT_MOVEMENT_PARENT);
        if (handle.isEmpty()) {
            abort(subcraft, interactor);
            return false;
        }
        subcraft.setParentStateHandle(handle.get());
        return true;
    }

    private static void abort(@NotNull SubCraft subcraft, @Nullable Entity interactor) {
        if (interactor != null) {
            interactor.sendMessage(I18nSupport.getInternationalisedString("Detection - Parent Craft is busy"));
        }
        CraftManager.getInstance().release(subcraft, CraftReleaseEvent.Reason.SUB_CRAFT, false);
    }
}
