package net.countercraft.movecraft.listener;

import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.events.TypesReloadedEvent;
import net.countercraft.movecraft.sign.CraftPilotSign;
import net.countercraft.movecraft.sign.MovecraftSignRegistry;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CraftTypeListener implements Listener {

    @EventHandler
    public void onReload(TypesReloadedEvent event) {
        MovecraftSignRegistry.INSTANCE.registerCraftPilotSigns(CraftManager.getInstance().getTypesafeCraftTypes(), CraftPilotSign::new);
    }

}
