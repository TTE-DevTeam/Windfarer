package net.countercraft.movecraft.async;

import com.google.common.collect.Lists;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.features.fuel.FuelUtil;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.tasks.FuelBurnTask;

import java.util.List;

// TODO: Migrate to processing system and run async if possible!
public class FuelBurnRunnable implements Runnable {
    // FuelBurnRate: How much fuel gets burnt per tick?
    // "BurningFuel": How many ticks of fuel does the craft still have aboard?

    @Override
    public void run() {
        List<Craft> crafts = Lists.newArrayList(CraftManager.getInstance());
        for (Craft craft : crafts) {
            if (!FuelUtil.doesBurnFuel(craft)) {
                continue;
            }

            if (FuelUtil.onlyBurnsFuelOnMovement(craft)) {
                continue;
            }

            runFuelBurnLogic(craft, false);
        }
    }

    public static void runFuelBurnLogic(Craft craft, boolean isStick) {
        // Burn current item or find a new one
        double fuelBurnRate = getFuelBurnRate(craft, isStick);
        // New logic starts a task per craft which handles like everything
        WorldManager.INSTANCE.submit(new FuelBurnTask(craft, fuelBurnRate));
    }

    static double getFuelBurnRateStickMovement(final Craft craft) {
        double fuelBurnRate = craft.getCurrentGear();

        // Different fuel burn rate depending on gear and if the craft is moving
        fuelBurnRate *= craft.getCraftProperties().get(PropertyKeys.FUEL_BURN_RATE, craft.getWorld());

        return fuelBurnRate;
    }

    static double getFuelBurnRate(final Craft craft, boolean isStick) {
        if (isStick) {
            return getFuelBurnRateStickMovement(craft);
        }
        double fuelBurnRate = craft.getCurrentGear();

        // Different fuel burn rate depending on gear and if the craft is moving
        boolean craftIsMoving = craft.getCruising();
        if (craftIsMoving) {
            fuelBurnRate *= craft.getCraftProperties().get(PropertyKeys.FUEL_BURN_RATE, craft.getWorld());
        } else {
            fuelBurnRate *= craft.getCraftProperties().get(PropertyKeys.INACTIVE_FUEL_BURN_RATE);
        }

        return fuelBurnRate;
    }


}
