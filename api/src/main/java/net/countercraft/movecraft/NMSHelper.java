package net.countercraft.movecraft;

import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public abstract class NMSHelper {

    public abstract boolean isFuel(ItemStack itemStack, World world);
    public abstract int getBurnDuration(ItemStack itemStack, World world);
    public abstract void setFurnaceBurnTime(int burnTime, int totalBurnTime, final Furnace furnace);

    public Component getEntityReferencingComponent(final Entity entity, final UUID fallback) {
        return Component.text(entity == null ? fallback.toString() : entity.getName());
    }

}
