package net.countercraft.movecraft.processing.effects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@FunctionalInterface
public interface Effect {
    /**
     * A no-op effect for use in systems where a non-null effect is needed
     */
    Effect NONE = new Effect() {
        @Override
        public void run() {
            // No-op
        }

        @Override
        public boolean isAsync() {
            return true;
        }

        @Override
        public @NotNull Effect andThen(@Nullable Effect chain){
            return chain == null ? this : chain;
        }
    };

    void run();

    default boolean isAsync(){
        return false;
    }

    default @NotNull Effect andThen(@Nullable List<Effect> chain){
        chain.add(0, this);
        return new AndEffect(chain);
    }
    default @NotNull Effect andThen(@Nullable Effect chain){
        return new AndEffect(this, chain);
    }

    // TODO: Customization flags
    public interface MultiEffect extends Iterable<Effect> {
        Iterator<Effect> iterator();
    }

    class AndEffect implements Effect {
        protected final List<Effect> effects = new ArrayList<>();

        public AndEffect(Effect... effects){
            for (Effect effect : effects) {
                andThen(effect);
            }
        }
        public AndEffect(List<Effect> effects){
            for (Effect effect : effects) {
                andThen(effect);
            }
        }

        @Override
        public void run() {
            effects.forEach(Effect::run);
        }

        @Override
        public @NotNull Effect andThen(@Nullable Effect chain) {
            if(this == chain){
                // copy if chaining to self to prevent concurrent modification
                effects.addAll(effects.stream().toList());

                return this;
            } else if(chain instanceof AndEffect andChain){
                // Merge other AndChain instances
                effects.addAll(andChain.effects);

                return this;
            } else if(chain == NONE || chain == null){
                // Skip NONE
                return this;
            }

            // Otherwise add to current chain
            effects.add(chain);

            return this;
        }
    }
}
