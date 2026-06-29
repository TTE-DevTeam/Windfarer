package net.countercraft.movecraft.features.fuel;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import org.bukkit.NamespacedKey;

public class FuelDataTags {

    public static final CraftDataTagKey<Boolean> IS_FUELED = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey(Movecraft.getInstance(), "is_fueled"), c -> false);
    public static final CraftDataTagKey<Double> FUEL_PERCENTAGE = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey(Movecraft.getInstance(), "fuel_percentage"), c -> 0.0D);


    public static final NamespacedKey FURNACES_KEY = new NamespacedKey(Movecraft.getInstance(), "furnaces");
    public static final NamespacedKey SOLID_FUEL_KEY = new NamespacedKey(Movecraft.getInstance(), "solid_fuel");
    //private static final CraftDataTagKey<Long> NEXT_FURNACE_CALCULATION = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey(Movecraft.getInstance(), "fuel_last_burner_calculation"), c -> System.currentTimeMillis() + 5000);

}
