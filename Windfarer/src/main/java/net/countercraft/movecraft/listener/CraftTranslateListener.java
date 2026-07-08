package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftCache;
import net.countercraft.movecraft.events.CraftFinishMovementEvent;
import net.countercraft.movecraft.events.CraftPreTranslateEvent;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.tasks.UpdateBannersTask;
import net.countercraft.movecraft.util.ContactBlockHelper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class CraftTranslateListener implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraftTranslated(final CraftFinishMovementEvent event) {
        Craft craft = event.getCraft();
        if (craft == null) {
            return;
        }

        // TODO: This thing is super slow, introduce caching of relevant blocks with TrackedLocations
        UpdateBannersTask task = UpdateBannersTask.createTask(craft);
        if (task != null) {
            WorldManager.INSTANCE.submit(task);
        }

        CraftCache.onCraftFinishedMovement(craft);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraftPreTranslate(final CraftPreTranslateEvent event) {
        ContactBlockHelper.onPreTranslate(event);
    }

}
