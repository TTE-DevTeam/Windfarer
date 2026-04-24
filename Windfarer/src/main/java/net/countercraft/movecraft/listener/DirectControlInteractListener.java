package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.controller.directControl.DirectControlController;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.jetbrains.annotations.NotNull;

public class DirectControlInteractListener implements Listener {

    // Normal priority so it runs AFTER normal interaction controls
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        final PlayerCraft craft = CraftManager.getInstance().getCraftByHelmsMan(player);
        if (craft == null) {
            return;
        }
        final TypeSafeCraftType type = craft.getCraftProperties();

        if (craft.getPilotLocked() && type.get(PropertyKeys.CAN_DIRECT_CONTROL)) {
            final DirectControlController dcController = type.get(PropertyKeys.DIRECT_CONTROL_CONTROLLER);
            if (dcController != null) {
                if (dcController.onPlayerInteract(event, craft)) {
                    InteractListener.storeInteraction(craft, player);
                    event.setCancelled(true);
                }
            }
            return;
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(final PlayerDropItemEvent event) {
        Player p = event.getPlayer();
        PlayerCraft craft = CraftManager.getInstance().getCraftByHelmsMan(p);
        if (craft == null)
            return;

        TypeSafeCraftType type = craft.getCraftProperties();
        if (craft.getPilotLocked() && type.get(PropertyKeys.CAN_DIRECT_CONTROL)) {
            final DirectControlController dcController = type.get(PropertyKeys.DIRECT_CONTROL_CONTROLLER);
            if (dcController != null) {
                dcController.onPlayerDropItem(event, craft);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSwapItem(final PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        PlayerCraft craft = CraftManager.getInstance().getCraftByHelmsMan(p);
        if (craft == null)
            return;

        TypeSafeCraftType type = craft.getCraftProperties();
        if (craft.getPilotLocked() && type.get(PropertyKeys.CAN_DIRECT_CONTROL)) {
            final DirectControlController dcController = type.get(PropertyKeys.DIRECT_CONTROL_CONTROLLER);
            if (dcController != null) {
                dcController.onPlayerSwapItem(event, craft);
            }
        }
    }

}
