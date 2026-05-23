package net.countercraft.movecraft.features.directControl.slot;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.controller.directControl.AbstractDirectControlSlot;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.util.SerializationUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
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

    private final int ascendDescendDelta;
    private final double rotationAngle;
    private final double riseDiveAngle;

    private DefaultDirectControlSlot(final DefaultDirectControlSlot other) {
        super(other.cooldown);
        this.modifyBearing = other.modifyBearing;
        this.shiftToDive = other.shiftToDive;
        this.shiftToRise = other.shiftToRise;
        this.clickToAscendOrDescend = other.clickToAscendOrDescend;
        this.requirePilotToolForAscendOrDescend = other.requirePilotToolForAscendOrDescend;
        this.rotationAngle = other.rotationAngle;
        this.ascendDescendDelta = other.ascendDescendDelta;
        this.riseDiveAngle = other.riseDiveAngle;
    }

    public DefaultDirectControlSlot(Map<String, Object> args) {
        super(args);
        this.modifyBearing = SerializationUtil.deserializeBoolean("modify_bearing", args, false);
        this.shiftToDive = SerializationUtil.deserializeBoolean("shift_to_dive", args, false);
        this.shiftToRise = SerializationUtil.deserializeBoolean("shift_to_rise", args, false);
        this.clickToAscendOrDescend = SerializationUtil.deserializeBoolean("click_to_ascend_or_descend", args, false);
        this.requirePilotToolForAscendOrDescend = SerializationUtil.deserializeBoolean("require_pilot_tool_to_ascend_or_descend", args, false);
        this.rotationAngle = Math.toRadians(NumberConversions.toDouble(args.getOrDefault("bearing_delta", 0.0D)));
        this.riseDiveAngle = Math.toRadians(NumberConversions.toDouble(args.getOrDefault("rise_dive_angle", 0.0D)));
        this.ascendDescendDelta = NumberConversions.toInt(args.getOrDefault("delta_y", 1));
    }

    @Override
    protected boolean doOnLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        // Ignored, used to exit DC, handled on a higher level
        return false;
    }

    @Override
    protected boolean doOnRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        if (!this.clickToAscendOrDescend) {
            return false;
        }
        if (itemStack.getType() != Settings.PilotTool && this.requirePilotToolForAscendOrDescend) {
            return false;
        }

        int dy = this.ascendDescendDelta; // Default to up
        if (interactor.isSneaking())
            dy = -dy; // Down if sneaking
        if (craft.getCraftProperties().get(PropertyKeys.GEAR_SHIFT_AFFECT_DIRECT_MOVEMENT))
            dy *= craft.getCurrentGear(); // account for gear shifts

        craft.translate(craft.getWorld(), 0, dy, 0);

        craft.setLastCruiseUpdate(System.currentTimeMillis());

        return true;
    }

    @Override
    protected boolean doOnItemDrop(ItemStack itemStack, Player interactor, Craft craft) {
        // Ignored
        return false;
    }

    @Override
    protected boolean doOnSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft) {
        // Ignored
        return false;
    }

    @Override
    protected boolean doOnPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection) {
        // If configured, bank or dive/rise when shift is pressed
        boolean modHorizontal = false;
        boolean modVertical = false;

        if (this.modifyBearing) {
            modHorizontal = true;
            cruiseDirection.rotateAroundY(-this.rotationAngle);
        }
        if (activePilot.isSneaking()) {
            if (this.shiftToRise) {
                modVertical = true;
                cruiseDirection.rise2D(this.riseDiveAngle);
            }
            if (this.shiftToDive) {
                modVertical = true;
                cruiseDirection.rise2D(-this.riseDiveAngle);
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
        Map<String, Object> result = super.serialize();

        result.put("modify_bearing", this.modifyBearing);
        result.put("shift_to_dive", this.shiftToDive);
        result.put("shift_to_rise", this.shiftToRise);
        result.put("click_to_ascend_or_descend", this.clickToAscendOrDescend);
        result.put("require_pilot_tool_to_ascend_or_descend", this.requirePilotToolForAscendOrDescend);
        result.put("bearing_delta", Math.toDegrees(this.rotationAngle));
        result.put("rise_dive_angle", Math.toDegrees(this.riseDiveAngle));
        result.put("delta_y", this.ascendDescendDelta);

        return result;
    }
}
