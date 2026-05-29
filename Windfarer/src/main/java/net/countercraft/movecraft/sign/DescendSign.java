package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import org.bukkit.entity.Entity;

// TODO: Unify with AscendSign to use a common "VerticalCruiseSign" class
public class DescendSign extends AbstractCruiseSign {

    public DescendSign(final String ident) {
        super(true, ident, "ON", "OFF");
    }

    @Override
    protected void setCraftCruising(Entity interactor, CruiseDirection direction, Craft craft) {
        craft.setCruiseDirection(direction);
        craft.setLastCruiseUpdate(System.currentTimeMillis());
        craft.setCruising(true);
    }

    @Override
    protected CruiseDirection getCruiseDirection(SignListener.SignWrapper sign) {
        return CruiseDirection.DOWN;
    }

    @Override
    protected void onCraftIsBusy(Entity interactor, Craft craft) {
        // Ignore
    }

    @Override
    protected void onCraftNotFound(Entity interactor, SignListener.SignWrapper sign) {

    }

    @Override
    protected void onAfterStoppingCruise(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor) {
        if (!craft.getCraftProperties().get(PropertyKeys.CAN_MOVE_ENTITIES)) {
            CraftManager.getInstance().addReleaseTask(craft);
        }
    }
}
