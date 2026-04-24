package net.countercraft.movecraft.events;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.effects.Effect;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class CraftGatherAdditionalDetectionStepsEvent extends CraftEvent {

    private static final HandlerList HANDLERS = new HandlerList();
    private final Set<Supplier<Effect>> additionalSteps;

    public CraftGatherAdditionalDetectionStepsEvent(@NotNull Craft craft, final Set<Supplier<Effect>> additionalSteps ) {
        super(craft);
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

    public boolean addStep(final Supplier<Effect> step) {
        return this.additionalSteps.add(step);
    }

}
