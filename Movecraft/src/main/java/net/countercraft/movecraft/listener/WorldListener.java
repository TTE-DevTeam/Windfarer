package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.WorldHandler;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.countercraft.movecraft.util.hitboxes.SetHitBox;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Set;

public class WorldListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        World world = event.getWorld();
        if (world == null) {
            // Weird, but ok...
            return;
        }
        Set<Craft> craftsInWorld = CraftManager.getInstance().getCraftsInWorld(world);
        if (craftsInWorld == null || craftsInWorld.isEmpty()) {
            return;
        }
        boolean allSuccessful = true;
        for (Craft craft : craftsInWorld) {
            if (craft.getCraftProperties().get(PropertyKeys.SAVE_TO_DISK, world)) {
                if (!processPersistableCraft(craft, world))
                    allSuccessful = false;
            }
            // If we want to delete the craft when the world unloads, we need to do that now
            else if (craft.getCraftProperties().get(PropertyKeys.DELETE_ON_WORLD_UNLOAD, world)) {
                if (!deleteCraftBlocks(craft, world))
                    allSuccessful = false;
            }
            // Standard craft
            else {
                if (!processStandardCraft(craft))
                    allSuccessful = false;
            }
        }
        if (!allSuccessful) {
            event.setCancelled(true);
            // If unsuccessful, try to unload the world later
            Bukkit.getScheduler().runTaskLater(Movecraft.getInstance(), () -> {
                Bukkit.unloadWorld(world, true);
            }, 10);
        }
    }

    private static boolean processStandardCraft(Craft craft) {
        // Clear the collapsed hitbox, otherwise a wreck task will be queued!
        craft.getCollapsedHitBox().clear();
        return CraftManager.getInstance().tryRelease(craft, CraftReleaseEvent.Reason.FORCE, true);
    }

    private static boolean processPersistableCraft(Craft craft, World world) {
        // TODO: Implement
        return true;
    }

    private static boolean deleteCraftBlocks(Craft craft, World world) {
        final HitBox hitBox = new SetHitBox(craft.getHitBox());
        craft.getCollapsedHitBox().clear();
        craft.setHitBox(new SetHitBox());

        final BlockData blockData = Material.AIR.createBlockData();
        final WorldHandler handler = Movecraft.getInstance().getWorldHandler();
        for (MovecraftLocation movecraftLocation : hitBox) {
            handler.setBlockFast(movecraftLocation.toBukkit(world), blockData);
        }

        return CraftManager.getInstance().tryRelease(craft, CraftReleaseEvent.Reason.FORCE, true);
    }

}
