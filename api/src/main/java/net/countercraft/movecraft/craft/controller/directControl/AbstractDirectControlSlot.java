package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public abstract class AbstractDirectControlSlot implements ConfigurationSerializable {

    public abstract boolean onLeftClick(ItemStack itemStack, Player interactor, Craft craft);
    public abstract boolean onRightClick(ItemStack itemStack, Player interactor, Craft craft);
    public abstract boolean onItemDrop(ItemStack itemStack, Player interactor, Craft craft);
    public abstract boolean onSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft);
    public abstract boolean onPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection);

    public abstract AbstractDirectControlSlot clone();

}
