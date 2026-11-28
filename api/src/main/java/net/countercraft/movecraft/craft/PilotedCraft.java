package net.countercraft.movecraft.craft;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public interface PilotedCraft extends Craft {

    @Nullable
    @Deprecated(forRemoval = true)
    /*
     * If you want a PLAYER pilot entity, use PlayerCraft!
     */
    default Player getPilot() {
        if (this.getPilotEntity() == null) {
            return null;
        }
        if (this.getPilotEntity() instanceof Player player) {
            return player;
        }
        return null;
    }

    @Nullable
    Entity getPilotEntity();

    @NotNull
    UUID getPilotUUID();

    @Override
    default boolean shouldAutoRelease(final long autoReleaseTimeout, final long maxTimeBetweenCruiseUpdates) {
        // Never releases the craft, but acts as a watchdog: a cruising craft which is still moving, although it
        // was not updated for far too long, is stuck. Its states are reset then, so it can move again.
        // !!DANGEROUS!!: this also ends a movement which is in progress, so it is logged with the state of the craft.
        // The movement states are released by their owners as well, doing it twice is harmless.
        if (this.getCruising() && this.getStateTracker().isMoving()
                && this.getLastCruiseUpdate() < System.currentTimeMillis() - maxTimeBetweenCruiseUpdates) {
            Bukkit.getLogger().warning("Craft " + this.getUUID() + " seems to be stuck while cruising, resetting its states: "
                    + this.getStateTracker());
            this.getStateTracker().forceReset();
        }
        return false;
    }

    @Override
    default Set<Entity> getEntitiesInMovebox() {
        final Set<Entity> result = Craft.super.getEntitiesInMovebox();
        Entity pilot = this.getPilotEntity();
        if (pilot != null) {
            result.add(pilot);
        }
        return result;
    }
}
