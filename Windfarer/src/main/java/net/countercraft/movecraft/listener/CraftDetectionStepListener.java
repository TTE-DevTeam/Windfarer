package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.events.CraftGatherAdditionalDetectionStepsEvent;
import net.countercraft.movecraft.processing.tasks.detection.SignDetectionTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class CraftDetectionStepListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDetectAddSteps(final CraftGatherAdditionalDetectionStepsEvent event) {
        // Sign locations
        event.addStep((task, craft) -> new SignDetectionTask(craft));
    }

}
