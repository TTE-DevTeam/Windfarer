package net.countercraft.movecraft.craft;

import org.bukkit.entity.Player;

public interface PlayerCraft extends PilotedCraft {

    boolean getPilotLocked();

    Player getPilotPlayer();

    void setPilotLocked(boolean pilotLocked);

    double getPilotLockedX();

    void setPilotLockedX(double pilotLockedX);

    double getPilotLockedY();

    void setPilotLockedY(double pilotLockedY);

    double getPilotLockedZ();

    void setPilotLockedZ(double pilotLockedZ);
}
