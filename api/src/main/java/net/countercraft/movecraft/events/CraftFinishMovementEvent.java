package net.countercraft.movecraft.events;

import net.countercraft.movecraft.MovecraftRotation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CraftFinishMovementEvent extends CraftEvent {

    private final MovecraftRotation rotation;

    public UUID getOldWorld() {
        return oldWorld;
    }

    private final UUID oldWorld;
    private final int dx, dy, dz;

    public MovecraftRotation getRotation() {
        return rotation;
    }

    public int getDx() {
        return dx;
    }

    public int getDy() {
        return dy;
    }

    public int getDz() {
        return dz;
    }

    public HitBox getOldHitBox() {
        return oldHitBox;
    }

    public HitBox getNewHitBox() {
        return newHitBox;
    }

    private final HitBox oldHitBox;
    private final HitBox newHitBox;

    @NotNull private static final HandlerList HANDLERS = new HandlerList();

    public CraftFinishMovementEvent(final Craft craft, MovecraftRotation rotation, UUID oldWorld, int dx, int dy, int dz, HitBox oldHitBox, HitBox newHitBox) {
        super(craft);
        this.rotation = rotation;
        this.oldWorld = oldWorld;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.oldHitBox = oldHitBox;
        this.newHitBox = newHitBox;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

}
