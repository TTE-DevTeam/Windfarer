package net.countercraft.movecraft.features.fuel;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;

public class FuelUtil {

    public static boolean doesBurnFuel(final Craft craft) {
        if (craft instanceof SinkingCraft) {
            return false;
        }
        // TODO: Squadrons are subcrafts too! So treat them properly
        if (craft instanceof SubCraft) {
            return false;
        }
        double fuelBurnRate = craft.getCraftProperties().get(PropertyKeys.FUEL_BURN_RATE, craft.getMovecraftWorld());
        return fuelBurnRate > 0.0D;
    }

    public static boolean onlyBurnsFuelOnMovement(final Craft craft) {
        return craft.getCraftProperties().get(PropertyKeys.ONLY_CONSUME_FUEL_ON_MOVEMENT);
    }

}
