package net.countercraft.movecraft.features.directControl.slot;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.controller.directControl.AbstractDirectControlSlot;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.util.SerializationUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Consumer;

public class DefaultDirectControlSlot extends AbstractDirectControlSlot {

    private final boolean modifyBearing;
    private final boolean shiftToDive;
    private final boolean shiftToRise;
    private final boolean clickToAscendOrDescend;
    private final boolean requirePilotToolForAscendOrDescend;

    private final double rotationAngle;

    private DefaultDirectControlSlot(final DefaultDirectControlSlot other) {
        this.modifyBearing = other.modifyBearing;
        this.shiftToDive = other.shiftToDive;
        this.shiftToRise = other.shiftToRise;
        this.clickToAscendOrDescend = other.clickToAscendOrDescend;
        this.requirePilotToolForAscendOrDescend = other.requirePilotToolForAscendOrDescend;
        this.rotationAngle = other.rotationAngle;
    }

    public DefaultDirectControlSlot(Map<String, Object> args) {
        this.modifyBearing = SerializationUtil.deserializeBoolean("modify_bearing", args, false);
        this.shiftToDive = SerializationUtil.deserializeBoolean("shift_to_dive", args, false);
        this.shiftToRise = SerializationUtil.deserializeBoolean("shift_to_rise", args, false);
        this.clickToAscendOrDescend = SerializationUtil.deserializeBoolean("click_to_ascend_or_descend", args, false);
        this.requirePilotToolForAscendOrDescend = SerializationUtil.deserializeBoolean("require_pilot_tool_to_ascend_or_descend", args, false);
        this.rotationAngle = Math.toRadians(NumberConversions.toDouble(args.getOrDefault("bearing_delta", 0.0D)));
    }

    @Override
    public boolean onLeftClick(ItemStack itemStack, Player interactor, Craft craft) {
        // Ignored, used to exit DC, handled on a higher level
        return false;
    }

    @Override
    public boolean onRightClick(ItemStack itemStack, Player interactor, Craft craft) {
        if (itemStack.getType() != Settings.PilotTool && this.requirePilotToolForAscendOrDescend) {
            return false;
        }
        if (!this.clickToAscendOrDescend) {
            return false;
        }

        // No shift: ascend
        // Shift: descend

        return false;
    }

    @Override
    public boolean onItemDrop(ItemStack itemStack, Player interactor, Craft craft) {
        // Ignored
        return false;
    }

    @Override
    public boolean onSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft) {
        // Ignored
        return false;
    }

    @Override
    public boolean onPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection) {
        // If configured, bank or dive/rise when shift is pressed
        boolean modHorizontal = false;
        boolean modVertical = false;

        if (this.modifyBearing) {
            modHorizontal = true;
            cruiseDirection.rotateAroundY(this.rotationAngle);
        }
        if (activePilot.isSneaking()) {
            if (this.shiftToRise) {
                modVertical = true;
                cruiseDirection.rise2D(this.rotationAngle);
            }
            if (this.shiftToDive) {
                modVertical = true;
                cruiseDirection.rise2D(-this.rotationAngle);
            }
        }

        int cruiseSkipBlocks = craft.getCraftProperties().get(PropertyKeys.CRUISE_SKIP_BLOCKS, craft.getWorld());
        int tickCooldownNew = tickCooldown;
        if (modHorizontal) {
            if (!modVertical) {
                tickCooldownNew *= (Math.sqrt(Math.pow(1 + cruiseSkipBlocks, 2)
                        + Math.pow(cruiseSkipBlocks >> 1, 2)) / (1 + cruiseSkipBlocks));
            } else {
                tickCooldownNew *= (Math.sqrt(Math.pow(1 + cruiseSkipBlocks, 2)
                        + Math.pow(cruiseSkipBlocks >> 1, 2) + 1) / (1 + cruiseSkipBlocks));
            }
        }
        else if (modVertical) {
            tickCooldownNew *= (Math.sqrt(Math.pow(1 + cruiseSkipBlocks, 2) + 1) / (1 + cruiseSkipBlocks));
        } else {
            return false;
        }

        modifyTickCooldown.accept(tickCooldownNew);

        return true;
    }

    @Override
    public AbstractDirectControlSlot clone() {
        return new DefaultDirectControlSlot(this);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        return Map.of();
    }
}
