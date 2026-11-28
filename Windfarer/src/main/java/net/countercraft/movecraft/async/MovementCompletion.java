package net.countercraft.movecraft.async;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.Craft;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Lets code that requests a movement of a craft (e.g. the signs for subcrafts) run something once that movement is over,
 * no matter if it succeeded, failed or could not even be started. Internal helper of the movement handling.
 * <p>
 * The movement handling reports the end with {@link #movementFinished(Craft)}: when the states of a movement are released
 * ({@link MovementStates#release()}) and when a movement is rejected before it starts.
 */
public final class MovementCompletion {
    private static final Map<Craft, Runnable> ACTIONS = Collections.synchronizedMap(new WeakHashMap<>());

    private MovementCompletion() {
    }

    /**
     * Registers the action which is run (on the main thread) when the next movement of the craft is over.
     * Register it before requesting the movement. Replaces an action which did not run yet.
     */
    public static void runAfterNextMovement(@NotNull Craft craft, @NotNull Runnable action) {
        ACTIONS.put(craft, action);
    }

    /**
     * Reports the end of a movement of the craft. Runs the registered action, if any.
     */
    public static void movementFinished(@NotNull Craft craft) {
        final Runnable action = ACTIONS.remove(craft);
        if (action == null) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            action.run();
        } else {
            Bukkit.getScheduler().runTask(Movecraft.getInstance(), action);
        }
    }
}
