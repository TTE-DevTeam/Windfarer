package net.countercraft.movecraft.mapUpdater.update;

import net.countercraft.movecraft.MovecraftRotation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.events.CraftFinishMovementEvent;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.UUID;

public class FinishMovementEventCommand extends UpdateCommand{

    private final Craft craft;
    private final MovecraftRotation rotation;
    private final UUID oldWorldID;
    private final int dx, dy, dz;
    private final HitBox oldHitBox, newHitBox;

    public FinishMovementEventCommand(Craft craft, MovecraftRotation rotation, UUID oldWorld, int dx, int dy, int dz, HitBox oldHitBox, HitBox newHitBox) {
        this.craft = craft;
        this.rotation = rotation;
        this.oldWorldID = oldWorld;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.oldHitBox = oldHitBox;
        this.newHitBox = newHitBox;
    }

    @Override
    public void doUpdate() {
        Bukkit.getPluginManager().callEvent(new CraftFinishMovementEvent(this.craft, this.rotation, this.oldWorldID, this.dx, this.dy, this.dz, this.oldHitBox, this.newHitBox));
    }
}
