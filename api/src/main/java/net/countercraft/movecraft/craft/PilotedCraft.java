package net.countercraft.movecraft.craft;

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
        if (!this.isNotProcessing()) {
            if (this.getCruising()) {
                if(this.getLastCruiseUpdate() < System.currentTimeMillis() - maxTimeBetweenCruiseUpdates) {
                    this.setProcessing(false);
                }
            }
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
