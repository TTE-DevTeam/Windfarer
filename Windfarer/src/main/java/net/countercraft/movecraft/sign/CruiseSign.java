package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;

public class CruiseSign extends AbstractCruiseSign {

    public CruiseSign(final String ident) {
        super("movecraft.cruisesign", true, ident,"ON", "OFF");
    }

    @Override
    protected void setCraftCruising(Entity interactor, CruiseDirection direction, Craft craft) {
        craft.setCruiseDirection(direction);
        craft.setLastCruiseUpdate(System.currentTimeMillis());
        craft.setCruising(true);
    }

    @Override
    protected CruiseDirection getCruiseDirection(SignListener.SignWrapper sign) {
        BlockFace face = sign.facing();
        // NOt necessary, CruiseDirection#fromBlockFace already handles this!
        //face = face.getOppositeFace();
        return CruiseDirection.fromBlockFace(face);
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        if (super.isSignValid(clickType, sign, interactor)) {
            switch(sign.facing()) {
                case NORTH:
                case EAST:
                case SOUTH:
                case WEST:
                    return true;
                default:
                    return false;
            }
        }
        return false;
    }

    @Override
    protected void onAfterStartingCruise(Craft craft, SignListener.SignWrapper signWrapper, Entity interactor) {
        super.onAfterStartingCruise(craft, signWrapper, interactor);
        // Left over artifact from manually launched torpedoes
        if (!craft.getCraftProperties().get(PropertyKeys.CAN_MOVE_ENTITIES)) {
            CraftManager.getInstance().addReleaseTask(craft);
        }
    }

    @Override
    protected boolean shouldShareSameToggleState(SignListener.SignWrapper sign, SignListener.SignWrapper other, Craft craft) {
        return super.shouldShareSameToggleState(sign, other, craft) && sign.facing() == other.facing();
    }

    @Override
    protected void onCraftIsBusy(Entity interactor, Craft craft) {
        // Ignore
    }

    @Override
    protected void onCraftNotFound(Entity interactor, SignListener.SignWrapper sign) {

    }

}
