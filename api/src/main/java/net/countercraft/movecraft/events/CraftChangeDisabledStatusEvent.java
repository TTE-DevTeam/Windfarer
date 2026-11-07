package net.countercraft.movecraft.events;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CraftChangeDisabledStatusEvent extends CraftEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final boolean oldDisabledState;
    private final boolean newDisabledState;

    private boolean cancelled = false;

    public CraftChangeDisabledStatusEvent(@NotNull Craft craft, boolean oldDisabled, boolean newDisabled) {
        super(craft);
        this.oldDisabledState = oldDisabled;
        this.newDisabledState = newDisabled;
    }

    public boolean wasDisabled() {
        return oldDisabledState;
    }

    public boolean willBeDisabled() {
        return newDisabledState;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}
