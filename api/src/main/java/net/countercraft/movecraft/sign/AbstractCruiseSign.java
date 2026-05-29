package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.controller.directControl.HelmsManManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

/*
 * Base class for all cruise signs
 *
 * Has the relevant logic for the "state" suffix (on / off) as well as calling the relevant methods and setting the craft to cruising
 *
 */
public abstract class AbstractCruiseSign extends AbstractToggleSign {

    public AbstractCruiseSign(boolean ignoreCraftIsBusy, String ident, String suffixOn, String suffixOff) {
        super(ignoreCraftIsBusy, ident, suffixOn, suffixOff);
    }

    public AbstractCruiseSign(final String permission, boolean ignoreCraftIsBusy, String ident, String suffixOn, String suffixOff) {
        super(permission, ignoreCraftIsBusy, ident, suffixOn, suffixOff);
    }

    // Hook to do stuff that run after stopping to cruise
    protected void onAfterStoppingCruise(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor) {

    }

    // Hook to do stuff that run after starting to cruise
    protected void onAfterStartingCruise(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor) {

    }

    @Override
    protected void onAfterToggle(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor, boolean toggledToOn) {
        if (toggledToOn) {
            this.onAfterStartingCruise(craft, signWrapper, interactor);
        } else {
            this.onAfterStoppingCruise(craft, signWrapper, interactor);
        }
    }

    @Override
    protected boolean onBeforeToggle(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor, boolean willBeOn) {
        if (willBeOn) {
            CruiseDirection cruiseDirection = this.getCruiseDirection(signWrapper);
            this.setCraftCruising(interactor, cruiseDirection, craft);
        } else {
            craft.setCruising(false, CraftStopCruiseEvent.Reason.SIGN_INTERACTION);
        }
        return true;
    }

    // Should call the craft's relevant methods to start cruising
    protected abstract void setCraftCruising(Entity interactor, CruiseDirection direction, Craft craft);

    // TODO: Rework cruise direction to vectors => Vector defines the skip distance and the direction
    // Returns the direction in which the craft should cruise
    protected abstract CruiseDirection getCruiseDirection(SignListener.SignWrapper sign);

    @Override
    protected boolean canPlayerUseSignOn(Entity interactor, @Nullable Craft craft) {
        if (super.canPlayerUseSignOn(interactor, craft) || HelmsManManager.getHelmsMan(craft) == interactor) {
            return craft.getCraftProperties().get(PropertyKeys.CAN_CRUISE);
        }
        return false;
    }
}
