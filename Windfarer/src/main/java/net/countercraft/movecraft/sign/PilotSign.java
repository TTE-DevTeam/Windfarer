package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;

import javax.annotation.Nullable;

// TODO: Replace PilotSignValidator with this?
public class PilotSign extends AbstractMovecraftSign {

    public PilotSign() {
        super(null);
    }

    // Pilot signs are pretty much always valid
    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        return true;
    }

    @Override
    protected boolean internalProcessSign(Action clickType, SignListener.SignWrapper sign, Entity interactor, @Nullable Craft craft) {
        // Nothing to do here
        return true;
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        boolean foundSome = false;
        for (int i = 1; i < sign.lines().size(); i++) {
            String data = null;
            try {
                data = sign.getRaw(i);
            } catch (IndexOutOfBoundsException ioob) {
                // Ignore
            }
            if (data != null) {
                foundSome = !data.isBlank();
                if (foundSome) {
                    break;
                }
            }
        }
        if (!foundSome) {
            sign.line(1, event.getPlayer().name());
        }
        return true;
    }
}
