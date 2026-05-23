package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractDirectControlSlot implements ConfigurationSerializable {

    protected final long cooldown;
    // We have one instance per craft as this all gets cloned!
    protected long lastUseTime;

    protected abstract boolean doOnLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action);
    protected abstract boolean doOnRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action);
    protected abstract boolean doOnItemDrop(ItemStack itemStack, Player interactor, Craft craft);
    protected abstract boolean doOnSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft);
    protected abstract boolean doOnPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection);

    public abstract AbstractDirectControlSlot clone();

    protected AbstractDirectControlSlot(final long cooldown) {
        this.cooldown = cooldown;
    }

    public AbstractDirectControlSlot(final Map<String, Object> yamlData) {
        this.cooldown = NumberConversions.toLong(yamlData.getOrDefault("cooldown", 200L));
    }

    protected boolean isReady() {
        if (this.cooldown <= 0) {
            return true;
        } else {
            return this.lastUseTime < System.currentTimeMillis();
        }
    }

    public final boolean onLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        if (!this.isReady()) {
            return false;
        }
        final boolean result = this.doOnLeftClick(itemStack, interactor, craft, action);
        if (result) {
            this.lastUseTime = System.currentTimeMillis();
        }
        return result;
    }

    public final boolean onRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        if (!this.isReady()) {
            return false;
        }
        final boolean result = this.doOnRightClick(itemStack, interactor, craft, action);
        if (result) {
            this.lastUseTime = System.currentTimeMillis();
        }
        return result;
    }

    public final boolean onItemDrop(ItemStack itemStack, Player interactor, Craft craft) {
        if (!this.isReady()) {
            return false;
        }
        final boolean result = this.doOnItemDrop(itemStack, interactor, craft);
        if (result) {
            this.lastUseTime = System.currentTimeMillis();
        }
        return result;
    }

    public final boolean onSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft) {
        if (!this.isReady()) {
            return false;
        }
        final boolean result = this.doOnSwapHand(itemStackMainHand, itemStackOffHand, interactor, craft);
        if (result) {
            this.lastUseTime = System.currentTimeMillis();
        }
        return result;
    }

    public final boolean onPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection) {
        if (!this.isReady()) {
            return false;
        }
        final boolean result = this.doOnPreCruise(activePilot, craft, tickCooldown, modifyTickCooldown, cruiseDirection);
        if (result) {
            this.lastUseTime = System.currentTimeMillis();
        }
        return result;
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();

        result.put("cooldown", this.cooldown);

        return result;
    }

}
