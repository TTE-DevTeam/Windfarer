package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.effects.Effect;

import java.util.function.Supplier;

public class UpdateFuelBurnersTask implements Supplier<Effect> {

    private final Craft craft;
    private final boolean burnersActive;

    public UpdateFuelBurnersTask(Craft craft, boolean burnersActive) {
        this.craft = craft;
        this.burnersActive = burnersActive;
    }

    @Override
    public Effect get() {
        // TODO: Add API for additional fuel sources and how full they are
        // Step 0): Determine if we can modify furnaces or not
        // Step 1): Collect the data about every furnace on our craft that still exists, use the tracked locations for that
        // Step 2): For every fuel source, determine how full it is
        // Step 3): Special case furnaces: set the lit and progress state of the furnace => Do this in a effect and collec tit
        // Step 4): Calculate, how full the craft's total fuel supply is and apply it onto the craft
        return null;
    }
}
