package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public abstract class AbstractDirectControlSlot implements ConfigurationSerializable {

    protected final long cooldown;

    protected abstract boolean doOnLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action);
    protected abstract boolean doOnRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action);
    protected abstract boolean doOnItemDrop(ItemStack itemStack, Player interactor, Craft craft);
    protected abstract boolean doOnSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft);
    protected abstract boolean doOnPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection);

    private static final CraftDataTagKey<Map<AbstractDirectControlSlot, Long>> COOLDOWN_MAP = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey("windfarer", "direct_control/cooldowns"), c -> new ConcurrentHashMap<>());


    public abstract AbstractDirectControlSlot clone();

    protected AbstractDirectControlSlot(final long cooldown) {
        this.cooldown = cooldown;
    }

    protected long defaultCooldown() {
        return 200L;
    }

    public AbstractDirectControlSlot(final Map<String, Object> yamlData) {
        this.cooldown = NumberConversions.toLong(yamlData.getOrDefault("cooldown", this.defaultCooldown()));
    }

    protected boolean isReady(final Craft craft) {
        if (this.cooldown <= 0) {
            return true;
        } else {
            Optional<Long> optLastUseTime = this.getCooldown(craft);
            if (optLastUseTime.isEmpty()) {
                return true;
            } else {
                return optLastUseTime.get() < System.currentTimeMillis();
            }
        }
    }

    public final boolean onLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        if (!this.isReady(craft)) {
            return false;
        }
        final boolean result = this.doOnLeftClick(itemStack, interactor, craft, action);
        if (result) {
            this.setCooldown(craft);
        }
        return result;
    }

    public final boolean onRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        if (!this.isReady(craft)) {
            return false;
        }
        final boolean result = this.doOnRightClick(itemStack, interactor, craft, action);
        if (result) {
            this.setCooldown(craft);
        }
        return result;
    }

    public final boolean onItemDrop(ItemStack itemStack, Player interactor, Craft craft) {
        if (!this.isReady(craft)) {
            return false;
        }
        final boolean result = this.doOnItemDrop(itemStack, interactor, craft);
        if (result) {
            this.setCooldown(craft);
        }
        return result;
    }

    public final boolean onSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft) {
        if (!this.isReady(craft)) {
            return false;
        }
        final boolean result = this.doOnSwapHand(itemStackMainHand, itemStackOffHand, interactor, craft);
        if (result) {
            this.setCooldown(craft);
        }
        return result;
    }

    public final boolean onPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection) {
        if (!this.isReady(craft)) {
            return false;
        }
        final boolean result = this.doOnPreCruise(activePilot, craft, tickCooldown, modifyTickCooldown, cruiseDirection);
        if (result) {
            this.setCooldown(craft);
        }
        return result;
    }

    protected final Optional<Long> getCooldown(final Craft craft) {
        return Optional.ofNullable(craft.getDataTag(COOLDOWN_MAP).getOrDefault(this, null));
    }

    protected void setCooldown(final Craft craft) {
        if (this.cooldown <= 0) {
            return;
        }
        craft.getDataTag(COOLDOWN_MAP).put(this, System.currentTimeMillis() + this.cooldown);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();

        result.put("cooldown", this.cooldown);

        return result;
    }

}
