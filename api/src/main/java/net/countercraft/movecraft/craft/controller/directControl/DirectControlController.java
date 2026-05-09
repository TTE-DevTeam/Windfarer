package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.util.MathUtils;
import net.countercraft.movecraft.util.SerializationUtil;
import org.bukkit.GameMode;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Holds instances of AbstractDirectControlSlot
 *
 * Relays to the relevant slots in order on specific events.
 * OFFHAND slot is the last one it calls!
 */
// TODO: Add method stub for reacting to movement inputs!
// TODO: configurable list of commands that is run when DC is entered and left
public class DirectControlController implements ConfigurationSerializable {

    protected boolean playerMustBeInMoveBox;

    // Slots. Slot 10 is the offhand
    static final byte OFFHAND_SLOT = 9;
    protected final AbstractDirectControlSlot[] SLOTS = new AbstractDirectControlSlot[10];

    public static DirectControlController deserialize(Map<String, Object> yamlData) {
        boolean playerMustBeInMoveBox = SerializationUtil.deserializeBoolean("pilot_must_be_in_movebox", yamlData, true);
        Map<Byte, AbstractDirectControlSlot> slotMap = new HashMap<>(10);
        Object slotMappingRaw = yamlData.getOrDefault("slots", null);
        if (slotMappingRaw != null && slotMappingRaw instanceof Map slotMappingRawMap) {
            try {
                Map<String, Object> slotMapping = (Map<String, Object>) slotMappingRawMap;
                for (Map.Entry<String, Object> entry : slotMapping.entrySet()) {
                    if (entry.getValue() instanceof AbstractDirectControlSlot adcs) {
                        try {
                            Object indexObj = entry.getKey();
                            byte index = -1;
                            // TODO: Maybe support arrays in the future?
                            if (indexObj instanceof Number number) {
                                index = number.byteValue();
                            } else if (indexObj instanceof String string) {
                                index = Byte.parseByte(entry.getKey());
                            }

                            if (index < 0) {
                                System.err.println("Slot index <" + entry.getKey() + "> is not a byte or less than zero!");
                            } else {
                                slotMap.put(index, adcs);
                            }
                        } catch(NumberFormatException nfe) {
                            System.err.println("Slot index <" + entry.getKey() + "> is not a byte!");
                        }
                    } else {
                        System.err.println("Provided entry is not a instance of AbstractDirectControlSlot!");
                    }
                }
            } catch(ClassCastException cce) {
                System.err.println("Invalid configuration! Slot mapping is not String-to-object");
                cce.printStackTrace();
            }
        }

        if (!slotMap.isEmpty()) {
            return new DirectControlController(slotMap, playerMustBeInMoveBox);
        }
        return null;
    }

    protected DirectControlController(final DirectControlController other) {
        this.playerMustBeInMoveBox = other.playerMustBeInMoveBox;
        for (int i = 0; i < other.SLOTS.length; i++) {
            AbstractDirectControlSlot slot = other.SLOTS[i];
            if (slot == null) {
                continue;
            }
            this.SLOTS[i] = slot.clone();
        }
    }

    protected DirectControlController(final Map<Byte, AbstractDirectControlSlot> slotInstances, final boolean playerMustBeInMoveBox) {
        this.playerMustBeInMoveBox = playerMustBeInMoveBox;
        for (byte i = 0; i < this.SLOTS.length; i++) {
            AbstractDirectControlSlot slot = slotInstances.getOrDefault(i, null);
            this.SLOTS[i] = slot;
        }
    }

    protected @Nullable AbstractDirectControlSlot getSlotForPilot(final Player pilot) {
        if (pilot == null || pilot.getGameMode() == GameMode.SPECTATOR) {
            return null;
        }

        // TODO: Proper offhand support!
        ItemStack offhand = pilot.getInventory().getItemInOffHand();
        if (offhand == null || offhand.isEmpty()) {
            int currentSlot = pilot.getInventory().getHeldItemSlot();
            if (currentSlot < 0 || currentSlot > this.SLOTS.length) {
                return null;
            }
            if (this.SLOTS[currentSlot] != null) {
                return this.SLOTS[currentSlot];
            }
        }
        return this.SLOTS[OFFHAND_SLOT];
    }

    public boolean onPlayerInteract(final PlayerInteractEvent event, final PlayerCraft craft) {
        if (!checkPilot(event.getPlayer(), craft)) {
            return false;
        }
        AbstractDirectControlSlot slot = this.getSlotForPilot(event.getPlayer());
        if (slot == null) {
            return false;
        }

        if (event.getAction().isLeftClick()) {
            return slot.onLeftClick(event.getItem(), event.getPlayer(), craft, event.getAction());
        } else if (event.getAction().isRightClick()) {
            return slot.onRightClick(event.getItem(), event.getPlayer(), craft, event.getAction());
        } else {
            return false;
        }
    }

    public void onPlayerDropItem(final PlayerDropItemEvent event, final PlayerCraft craft) {
        if (!checkPilot(event.getPlayer(), craft)) {
            return;
        }
        AbstractDirectControlSlot slot = this.getSlotForPilot(event.getPlayer());
        if (slot == null) {
            return;
        }

        event.setCancelled(slot.onItemDrop(event.getItemDrop().getItemStack(), event.getPlayer(), craft));
    }

    public void onPlayerSwapItem(final PlayerSwapHandItemsEvent event, final PlayerCraft craft) {
        if (!checkPilot(event.getPlayer(), craft)) {
            return;
        }
        AbstractDirectControlSlot slot = this.getSlotForPilot(event.getPlayer());
        if (slot == null) {
            return;
        }

        event.setCancelled(slot.onSwapHand(event.getMainHandItem(), event.getOffHandItem(), event.getPlayer(), craft));
    }

    public boolean onPreCruise(final CruiseDirection cruiseDirection, final PlayerCraft craft, final Consumer<Integer> applyCooldown, final int currentCooldown) {
        final Player activePilot = this.getActivePilot(craft);
        if (!checkPilot(activePilot, craft)) {
            return false;
        }
        AbstractDirectControlSlot slot = this.getSlotForPilot(activePilot);
        if (slot == null) {
            return false;
        }

        return slot.onPreCruise(activePilot, craft, currentCooldown, applyCooldown, cruiseDirection);
    }

    protected Player getActivePilot(final PlayerCraft craft) {
        return HelmsManManager.getHelmsMan(craft);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();
        result.put("pilot_must_be_in_movebox", this.playerMustBeInMoveBox);
        final Map<String, Object> slotMap = new HashMap<>(10);
        for (int i = 0; i < this.SLOTS.length; i++) {
            AbstractDirectControlSlot controlSlot = this.SLOTS[i];
            if (controlSlot == null)
                continue;

            slotMap.put("" + i, controlSlot);
        }
        result.put("slots", slotMap);

        return result;
    }

    protected boolean checkPilot(final Player pilot, final PlayerCraft craft) {
        if (pilot == null) {
            return false;
        }
        // Direct control is not active
        if (!craft.getPilotLocked()) {
            return false;
        }
        if (this.playerMustBeInMoveBox) {
            return MathUtils.locationNearHitBox(craft.getHitBox(), pilot.getLocation(), 2);
        } else {
            return true;
        }
    }

    public static DirectControlController clone(DirectControlController toClone) {
        return new DirectControlController(toClone);
    }

}
