package net.countercraft.movecraft.events;

import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.effects.Effect;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class CraftGatherAdditionalDetectionStepsEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<BiFunction<Supplier<Effect>, Craft, Supplier<Effect>>> additionalSteps;

    public CraftGatherAdditionalDetectionStepsEvent(final Set<BiFunction<Supplier<Effect>, Craft, Supplier<Effect>>> additionalSteps ) {
        super();
        this.additionalSteps = additionalSteps;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @SuppressWarnings("unused")
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public boolean addStep(final BiFunction<Supplier<Effect>, Craft, Supplier<Effect>> step) {
        return this.additionalSteps.add(step);
    }

}
