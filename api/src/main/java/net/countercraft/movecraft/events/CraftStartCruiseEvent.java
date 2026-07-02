package net.countercraft.movecraft.events;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CraftStartCruiseEvent extends CraftEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public CraftStartCruiseEvent(@NotNull Craft craft) {
        super(craft);
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
