package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import net.countercraft.movecraft.util.Holder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// TODO: Rework methods into toggleDC(), setHelmsMan(), getHelmsMan(), removeHelmsMan()
// TODO: Rework into object that is stored in a datatag instead
// TODO: Add craft properties that toggle this feature
public class ActivePilotHelper {

    private static final CraftDataTagKey<Holder<UUID>> CURRENT_PILOT = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey("movecraft", "direct_control/active_pilot"), c -> new Holder<>());

    // TODO: Is this enough? Will the craft objects disappear on release?
    public static final Map<UUID, PlayerCraft> activePilotToCraftUUID = new WeakHashMap<>();


    public static void setActivePilot(Player pilot, PlayerCraft craft) {
        setActivePilot(pilot, craft, true);
    }
    public static void setActivePilot(Player pilot, PlayerCraft craft, boolean updateDC) {
        // TODO: Throw event
        // TODO: Check permissions
        // TODO: Add checks if we can override + parameter
        activePilotToCraftUUID.put(pilot.getUniqueId(), craft);

        if (updateDC) {
            craft.setPilotLocked(true);
            craft.setPilotLockedX(pilot.getLocation().getBlockX() + 0.5);
            craft.setPilotLockedY(pilot.getLocation().getY());
            craft.setPilotLockedZ(pilot.getLocation().getBlockZ() + 0.5);
        }

        craft.getDataTag(CURRENT_PILOT).set(pilot.getUniqueId());
        // TODO: Print out message
    }

    public static void removeActivePilot(PlayerCraft craft) {
        craft.setPilotLocked(false);
        UUID currentPilot = craft.getDataTag(CURRENT_PILOT).get();
        // ONly when it differs do we remove the link
        if (!craft.getPilotUUID().equals(currentPilot)) {
            activePilotToCraftUUID.remove(currentPilot);
        }
        craft.getDataTag(CURRENT_PILOT).clear();
    }

    public static Player getActivePilot(PlayerCraft craft) {
        if (!craft.getPilotLocked()) {
            return null;
        }
        UUID currentPilot = craft.getDataTag(CURRENT_PILOT).get();
        Player bukkitPlayer = currentPilot != null ? Bukkit.getPlayer(currentPilot) : null;
        if (bukkitPlayer == null) {
            removeActivePilot(craft);

            // A little bit unsafe but it should work
            if (craft.getPilotPlayer() != null && !craft.getPilotUUID().equals(currentPilot)) {
                setActivePilot(craft.getPilotPlayer(), craft);
                return craft.getPilotPlayer();
            } else {
                return null;
            }
        } else {
            return bukkitPlayer;
        }
    }

    public static Player getActivePilot(Craft craft) {
        if (craft instanceof PlayerCraft playerCraft) {
            return getActivePilot(playerCraft);
        } else if (craft instanceof PilotedCraft pilotedCraft) {
            Entity entity = pilotedCraft.getPilotEntity();
            if (entity instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
