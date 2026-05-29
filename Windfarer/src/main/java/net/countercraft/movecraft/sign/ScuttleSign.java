package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.events.CraftScuttleEvent;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;

import static net.countercraft.movecraft.util.ChatUtils.MOVECRAFT_COMMAND_PREFIX;

public class ScuttleSign extends AbstractCraftSign {

    public ScuttleSign() {
        super("movecraft.commands.scuttle.others", true);
    }

    @Override
    protected void onCraftIsBusy(Entity interactor, Craft craft) {

    }

    @Override
    protected void onCraftNotFound(Entity interactor, SignListener.SignWrapper sign) {
        interactor.sendMessage(MOVECRAFT_COMMAND_PREFIX
                + I18nSupport.getInternationalisedString("You must be piloting a craft"));
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        return true;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        return false;
    }

    @Override
    protected boolean canPlayerUseSignOn(Entity interactor, Craft craft) {
        if(craft instanceof SinkingCraft) {
            interactor.sendMessage(MOVECRAFT_COMMAND_PREFIX
                    + I18nSupport.getInternationalisedString("Scuttle - Craft Already Sinking"));
            return false;
        }
        if(!interactor.hasPermission("movecraft." + craft.getCraftProperties().getName().toLowerCase()
                + ".scuttle")) {
            interactor.sendMessage(MOVECRAFT_COMMAND_PREFIX
                    + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return false;
        }
        // Checks if the given player is the owner/pilot of the given craft
        // If it isnt a piloted craft, it returns true
        if (super.canPlayerUseSignOn(interactor, craft) && (craft instanceof PilotedCraft)) {
            return true;            
        }
        // Check for "can scuttle others" permission
        if (this.permissionString != null || !this.permissionString.isBlank()) {
            if (!interactor.hasPermission(this.permissionString)) {
                interactor.sendMessage(MOVECRAFT_COMMAND_PREFIX
                        + I18nSupport.getInternationalisedString("You must be piloting a craft"));
            }
        }
        return true;
    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, Craft craft, Entity interactor) {
        CraftScuttleEvent e = new CraftScuttleEvent(craft, interactor);
        Bukkit.getServer().getPluginManager().callEvent(e);
        if(e.isCancelled())
            return false;

        craft.setCruising(false, CraftStopCruiseEvent.Reason.CRAFT_SUNK);
        CraftManager.getInstance().sink(craft, CraftSinkEvent.SIMPLE_SINK_REASONS.SCUTTLE);
        interactor.sendMessage(MOVECRAFT_COMMAND_PREFIX
                + I18nSupport.getInternationalisedString("Scuttle - Scuttle Activated"));
        return true;
    }
}
