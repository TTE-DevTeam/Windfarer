package net.countercraft.movecraft.events;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.RequiredBlockEntry;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class CraftSinkEvent extends CraftEvent implements Cancellable {

    public SinkReason getReason() {
        return this.sinkReason;
    }

    public interface SinkReason {

        public String getName();

        public default boolean isSimple() {
            return this instanceof SIMPLE_SINK_REASONS;
        }

    }

    public enum SIMPLE_SINK_REASONS implements SinkReason {
        SCUTTLE,
        FORCE,
        CRUISE_LIFETIME,
        OWNER_DEATH,
        DISPLACEMENT_LOSS,
        UNKNOWN,
        OUT_OF_FUEL;

        @Override
        public String getName() {
            return this.name();
        }
    }

    public record SinkReasonConstraint(RequiredBlockEntry brokenConstraint) implements SinkReason {

        @Override
        public String getName() {
            return "CONSTRAINT";
        }

    }

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled = false;
    private final SinkReason sinkReason;

    public CraftSinkEvent(@NotNull Craft craft) {
        super(craft);
        this.sinkReason = SIMPLE_SINK_REASONS.FORCE;
    }

    public CraftSinkEvent(@NotNull Craft craft, @NotNull SinkReason sinkReason) {
        super(craft);
        this.sinkReason = sinkReason;
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
        return this.cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
