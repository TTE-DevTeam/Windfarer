package net.countercraft.movecraft.processing.tasks.detection;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.sign.*;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;

import java.util.function.Supplier;

public class SignDetectionTask implements Supplier<Effect> {

    protected final Craft craft;

    public SignDetectionTask(Craft craft) {
        this.craft = craft;
    }

    @Override
    public Effect get() {
        return new SignDetectAction(this.craft);
    }

    class SignDetectAction implements Effect {

        protected final Craft craft;

        public SignDetectAction(Craft craft) {
            this.craft = craft;
        }

        @Override
        public void run() {
            final CraftSignManager signManager = CraftSignManager.of(this.craft);
            for (MovecraftLocation movecraftLocation : this.craft.getHitBox()) {
                final Material type = this.craft.getMovecraftWorld().getMaterial(movecraftLocation);
                if (!Tag.ALL_SIGNS.isTagged(type))
                    continue;

                final BlockState blockState = this.craft.getMovecraftWorld().getState(movecraftLocation);
                if (blockState instanceof Sign sign) {
                    for (SignListener.SignWrapper wrapper : SignListener.INSTANCE.getSignWrappers(sign)) {
                        AbstractMovecraftSign acs = MovecraftSignRegistry.INSTANCE.get(wrapper.getRaw(0));
                        if (acs != null) {
                            signManager.addSign(acs.getClass(), movecraftLocation);
                        }
                    }
                }
            }
        }

    }
}
