package net.countercraft.movecraft.async;

import com.google.common.collect.Lists;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.features.fuel.FuelDataTags;
import net.countercraft.movecraft.features.fuel.FuelUtil;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.tasks.FuelBurnTask;
import net.countercraft.movecraft.processing.tasks.UpdateFuelBurnersTask;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

// TODO: Migrate to processing system and run async if possible!
public class FuelBurnRunnable implements Runnable {
    // FuelBurnRate: How much fuel gets burnt per tick?
    // "BurningFuel": How many ticks of fuel does the craft still have aboard?

    @Deprecated(forRemoval = true)
    public static final CraftDataTagKey<Boolean> IS_FUELED = FuelDataTags.IS_FUELED;
    @Deprecated(forRemoval = true)
    public static final CraftDataTagKey<Double> FUEL_PERCENTAGE = FuelDataTags.FUEL_PERCENTAGE;
    @Deprecated(forRemoval = true)
    public static final CraftDataTagKey<ItemStack> CURRENT_FUEL_ITEM = FuelDataTags.CURRENT_FUEL_ITEM;
    @Deprecated(forRemoval = true)
    public static final CraftDataTagKey<Set<TrackedLocation>> FURNACES = CraftDataTagRegistry.INSTANCE.registerTagKey(FuelDataTags.FURNACES_KEY, FuelUtil::getFuelBurners);


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

    @Deprecated(forRemoval = true)
    public static boolean doesBurnFuel(final Craft craft) {
       return FuelUtil.doesBurnFuel(craft);
    }

    @Deprecated(forRemoval = true)
    public static void updateFurnaces(Craft craft, boolean active) {
        WorldManager.INSTANCE.submit(new UpdateFuelBurnersTask(craft, active));
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
