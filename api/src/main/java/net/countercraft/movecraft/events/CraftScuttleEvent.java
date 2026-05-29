package net.countercraft.movecraft.events;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;


/**
 * Called whenever a craft is scuttled
 * @see Craft
 */
@SuppressWarnings("unused")
public class CraftScuttleEvent extends CraftEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private Entity cause;

    public CraftScuttleEvent(@NotNull Craft craft, @NotNull Entity cause) {
        super(craft);
        this.cause = cause;
    }

    public Player getCause() {
        if (this.cause instanceof Player player) {
            return player;
        } else {
            return null;
        }
    }

    public Entity getCauseEntity() {
        return this.cause;
    }

    @Override
    public boolean isCancelled() {
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}