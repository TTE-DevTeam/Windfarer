package net.countercraft.movecraft.craft.controller.directControl;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

// DONE: Rework methods into toggleDC(), setHelmsMan(), getHelmsMan(), removeHelmsMan()
// DONE: Rework into object that is stored in a datatag instead
// TODO: Add craft properties that toggle this feature
public class HelmsManManager {

    private static final CraftDataTagKey<HelmsManManager> HELMSMAN_HELPER = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey("movecraft", "helmsman_manager"), HelmsManManager::new);

    // TODO: Is this enough? Will the craft objects disappear on release?
    public static final Map<UUID, PlayerCraft> activePilotToCraftUUID = new WeakHashMap<>();

    private PlayerCraft parent;
    private UUID currentHelmsMan;

    HelmsManManager(final Craft craft) {
        if (craft instanceof PlayerCraft playerCraft) {
            this.parent = playerCraft;
            if (playerCraft.getPilotPlayer() != null) {
                // Result does not matter
                this.setHelmsMan(playerCraft.getPilotPlayer().getUniqueId());
            } else {
                this.setHelmsMan(playerCraft.getPilotUUID());
            }
        } else {
            throw new IllegalArgumentException("Provided craft must be a playercraft!");
        }
    }

    protected Player getHelmsMan() {
        if (this.currentHelmsMan == null) {
            return null;
        }
        Player bukkitPlayer = Bukkit.getPlayer(this.currentHelmsMan);
        if (!bukkitPlayer.isOnline()) {
            return null;
        }
        return bukkitPlayer;
    }

    protected void removeHelmsMan() {
        resetDirectControl(this.parent);
        activePilotToCraftUUID.remove(this.currentHelmsMan);
        this.currentHelmsMan = null;
    }

    public static void resetDirectControl(PlayerCraft craft) {
        craft.setPilotLocked(false);
        craft.setPilotLockedX(0.0);
        craft.setPilotLockedY(0.0);
        craft.setPilotLockedZ(0.0);
    }

    public static void enterDirectControl(Player pilot, PlayerCraft craft) {
        craft.setPilotLocked(true);
        craft.setPilotLockedX(pilot.getLocation().getBlockX() + 0.5);
        craft.setPilotLockedY(pilot.getLocation().getY());
        craft.setPilotLockedZ(pilot.getLocation().getBlockZ() + 0.5);
    }

    protected boolean setHelmsMan(final UUID newHelmsMan) {
        if (newHelmsMan == null) {
            return false;
        }
        if (newHelmsMan.equals(this.currentHelmsMan)) {
            return false;
        }
        if (this.currentHelmsMan == null || newHelmsMan.equals(this.parent.getPilotUUID())) {
            // TODO: Throw event
            // TODO: Check permissions
            // TODO: Add checks if we can override + parameter
            activePilotToCraftUUID.remove(this.currentHelmsMan);
            this.currentHelmsMan = newHelmsMan;
            activePilotToCraftUUID.put(this.currentHelmsMan, this.parent);
            return true;
        }
        return false;
    }

    protected void toggleDirectControl(Player player) {
        if (player.getUniqueId().equals(this.currentHelmsMan)) {
            // TODO: Run command
            player.performCommand("directcontrol toggle");
        }
    }

    private static HelmsManManager get(final PlayerCraft playerCraft) {
        return playerCraft.getDataTag(HELMSMAN_HELPER);
    }

    public static boolean setActivePilot(Player pilot, PlayerCraft craft) {
        return get(craft).setHelmsMan(pilot);
    }

    public static void removeActivePilot(PlayerCraft craft) {
        get(craft).removeHelmsMan();
    }

    public static Player getHelmsMan(PlayerCraft craft) {
        return get(craft).getHelmsMan();
    }

    public static void toggleDirectControl(PlayerCraft craft, Player player) {
        get(craft).toggleDirectControl(player);
    }

}
