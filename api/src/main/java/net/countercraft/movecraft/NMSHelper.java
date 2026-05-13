package net.countercraft.movecraft;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Furnace;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;

public abstract class NMSHelper {

    // Dirty hack, needs to be replaced in the future
    private static class Hidden {
        static NMSHelper instance;
    }

    {
        Hidden.instance = this;
    }

    public static NMSHelper getInstance() {
        return Hidden.instance;
    }

    public abstract boolean isFuel(ItemStack itemStack, World world);
    public abstract int getBurnDuration(ItemStack itemStack, World world);
    public abstract void setFurnaceBurnTime(int burnTime, int totalBurnTime, final Furnace furnace);

    public Component getEntityReferencingComponent(final Entity entity, final UUID fallback) {
        return Component.text(entity == null ? fallback.toString() : entity.getName());
    }

    public Component getBlockListComponent(final Set<NamespacedKey> blockIds) {
        Component result = Component.empty();
        for (NamespacedKey key : blockIds) {
            result = result.append(Component.text(" - " + key.toString()).appendNewline());
        }
        return result;
    }

    public abstract boolean tryInteractLever(Location bukkitLoc);
}
