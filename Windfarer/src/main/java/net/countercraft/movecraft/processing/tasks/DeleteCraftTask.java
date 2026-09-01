package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.processing.CachedMovecraftWorld;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.effects.SetBlockEffect;
import net.countercraft.movecraft.util.hitboxes.BitmapHitBox;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import org.bukkit.World;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;

import java.util.Iterator;
import java.util.function.Supplier;

public class DeleteCraftTask implements Supplier<Effect> {

    protected HitBox hitBox;
    protected World world;

    public DeleteCraftTask(final HitBox hitBox, World world) {
        this.hitBox = new BitmapHitBox(hitBox);
        this.world = world;
    }

    @Override
    public Effect get() {
        EffectChain result = new EffectChain();

        final BlockData airBlock = BlockType.AIR.createBlockData();
        final MovecraftWorld movecraftWorld = CachedMovecraftWorld.of(this.world);
        //BlockCollectionUtil.getLocations(null, this.hitBox, movecraftWorld, this::validatePosition, (loc, world, craft) -> result.add(new SetBlockEffect(world, loc, airBlock)));
        for (MovecraftLocation locTmp : this.hitBox) {
            result.add(new SetBlockEffect(movecraftWorld, locTmp, airBlock);
        }

        return result;
    }

    // private @NotNull Result validatePosition(@NotNull MovecraftLocation location, @NotNull MovecraftWorld movecraftWorld, @NotNull Craft craft) {
    //     return Result.of(!movecraftWorld.getMaterial(location).isAir());
    // }

    class EffectChain extends Effect.AndEffect implements Effect.MultiEffect {

        public void add(Effect effect) {
            this.effects.add(effect);
        }

        @Override
        public Iterator<Effect> iterator() {
            return this.effects.iterator();
        }
    }
}
