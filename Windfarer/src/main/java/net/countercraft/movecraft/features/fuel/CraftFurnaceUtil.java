package net.countercraft.movecraft.features.fuel;

import net.countercraft.movecraft.craft.type.CraftProperties;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;

public class CraftFurnaceUtil {

    public static double getFurnaceFuelLevel(final FurnaceInventory furnaceInventory, final CraftProperties craftProperties) {
        if (furnaceInventory == null || furnaceInventory.isEmpty())
            return 0.0D;

        // Check fuel item
        // If fueled, check for special effects of the cooked item
        // If we consumed a bucket, add the bucket to the result slot or drop it in front of the furnace
        ItemStack fuelItemStack = furnaceInventory.getFuel();
        if (fuelItemStack == null || fuelItemStack.isEmpty())
            return 0.0D;
        NamespacedKey itemID = fuelItemStack.getType().getKey();
        if (!craftProperties.get(PropertyKeys.FUEL_TYPES).contains(itemID)) {
            return 0.0D;
        }
        // Return how full this itemstack is
        return (((double) fuelItemStack.getAmount()) / ((double) fuelItemStack.getMaxStackSize()));
    }

}
