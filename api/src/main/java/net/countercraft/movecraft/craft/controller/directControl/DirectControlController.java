package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Holds instances of AbstractDirectControlSlot
 *
 * Relays to the relevant slots in order on specific events.
 * OFFHAND slot is the last one it calls!
 */
// TODO: Add method stub for reacting to movement inputs!
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
                            byte index = Byte.parseByte(entry.getKey());
                            slotMap.put(index, adcs);
                        } catch(NumberFormatException nfe) {
                            System.err.println("Slot index <" + entry.getKey() + "> is not a byte!");
                        }
                    } else {
                        System.err.println("Provided entry is not a instance of AbstractDirectControlSlot!");
                    }
                }
            } catch(ClassCastException cce) {
                System.err.println("Invalid configuration! Slot mapping is not String-to-object");
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
        int currentSlot = pilot.getInventory().getHeldItemSlot() - 1;
        if (this.SLOTS[currentSlot] != null) {
            return this.SLOTS[currentSlot];
        } else {
            ItemStack offhand = pilot.getInventory().getItemInOffHand();
            if (offhand == null || offhand.isEmpty()) {
                return null;
            }
            return this.SLOTS[OFFHAND_SLOT];
        }
    }

    public void onPlayerInteract(final PlayerInteractEvent event, final Craft craft) {
        AbstractDirectControlSlot slot = this.getSlotForPilot(event.getPlayer());
        if (slot == null) {
            return;
        }

        if (event.getAction().isLeftClick()) {
            event.setCancelled(slot.onLeftClick(event.getItem(), event.getPlayer(), craft));
        } else if (event.getAction().isRightClick()) {
            event.setCancelled(slot.onRightClick(event.getItem(), event.getPlayer(), craft));
        }
    }

    public void onPlayerDropItem(final PlayerDropItemEvent event, final Craft craft) {
        AbstractDirectControlSlot slot = this.getSlotForPilot(event.getPlayer());
        if (slot == null) {
            return;
        }

        event.setCancelled(slot.onItemDrop(event.getItemDrop().getItemStack(), event.getPlayer(), craft));
    }

    public void onPlayerSwapItem(final PlayerSwapHandItemsEvent event, final Craft craft) {
        AbstractDirectControlSlot slot = this.getSlotForPilot(event.getPlayer());
        if (slot == null) {
            return;
        }

        event.setCancelled(slot.onSwapHand(event.getMainHandItem(), event.getOffHandItem(), event.getPlayer(), craft));
    }

    public boolean onPreCruise(final CruiseDirection cruiseDirection, final Craft craft, final Consumer<Integer> applyCooldown, final int currentCooldown) {
        if (craft instanceof PilotedCraft pilotedCraft) {
            AbstractDirectControlSlot slot = this.getSlotForPilot(pilotedCraft.getPilot());
            if (slot == null) {
                return false;
            }

            return slot.onPreCruise(pilotedCraft.getPilot(), craft, currentCooldown, applyCooldown, cruiseDirection);
        }
        return false;
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

    public static DirectControlController clone(DirectControlController toClone) {
        return new DirectControlController(toClone);
    }

}
