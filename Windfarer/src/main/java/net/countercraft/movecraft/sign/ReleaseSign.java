package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.jetbrains.annotations.Nullable;

public class ReleaseSign extends AbstractMovecraftSign {

    public ReleaseSign() {
        super(null);
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        return true;
    }

    @Override
    protected boolean internalProcessSign(Action clickType, SignListener.SignWrapper sign, Entity interactor, @Nullable Craft craft) {
        if (craft == null || (craft instanceof PilotedCraft pc && pc.getPilotEntity() != interactor)) {
            craft = CraftManager.getInstance().getCraftByEntity(interactor);
        }
        if (craft != null) {
            CraftManager.getInstance().release(craft, CraftReleaseEvent.Reason.PLAYER, false);
        }
        return true;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        return false;
    }

}
