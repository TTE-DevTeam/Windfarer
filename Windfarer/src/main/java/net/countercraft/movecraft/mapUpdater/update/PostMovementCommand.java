package net.countercraft.movecraft.mapUpdater.update;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.async.MovementStates;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
import net.countercraft.movecraft.processing.tasks.UpdateBannersTask;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Level;

/**
 * Scheduled after the commands of a movement (the map update queue is FIFO) and ends the movement.
 * <p>
 * Runs the post movement steps (currently: updating the banners) while the craft still holds the states of the
 * movement. The states are released once the last step has finished, no matter whether the steps or the
 * preceding commands succeeded.
 */
public class PostMovementCommand extends UpdateCommand {
    private final @NotNull Craft craft;
    private final @NotNull MovementStates states;

    public PostMovementCommand(@NotNull Craft craft, @NotNull MovementStates states) {
        this.craft = craft;
        this.states = states;
    }

    @Override
    public void doUpdate() {
        if (this.craft.getHitBox().isEmpty()) {
            this.states.release();
            return;
        }
        try {
            // The task releases the states itself when it is done
            WorldManager.INSTANCE.submit(UpdateBannersTask.createTask(this.craft, this.states), WorldManagerWorkers.NORMAL_PRIORITY);
        } catch (RuntimeException | Error throwable) {
            this.states.release();
            Movecraft.getInstance().getLogger().log(Level.SEVERE, "Could not start post movement steps for craft " + this.craft.getUUID(), throwable);
        }
    }
}
