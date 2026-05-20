package net.countercraft.movecraft.util.hitboxes;

import com.google.common.collect.Iterators;
import net.countercraft.movecraft.MovecraftLocation;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

// Created by OhNoey on APDev
public class HitBoxSlicer implements Iterable<Iterable<MovecraftLocation>> {

    private final HitBox hitbox;

    public HitBoxSlicer(HitBox hitbox){
        this.hitbox = hitbox;
    }

    @NotNull
    @Override
    public Iterator<Iterable<MovecraftLocation>> iterator() {
        // TODO: Support different slice dimensions!
        // Converts max and min bounds to chunk coordinates
        // IMPORTANT: we must bitshift for correct conversion! It is faster and also what vanilla uses internally!
        final int chunkMinX = hitbox.getMinX() >> 4;
        final int chunkMaxX = hitbox.getMaxX() >> 4;
        final int chunkMinZ = hitbox.getMinZ() >> 4;
        final int chunkMaxZ = hitbox.getMaxZ() >> 4;

        var chunkIterator = new SolidHitBox(
                new MovecraftLocation(chunkMinX, 0, chunkMinZ),
                new MovecraftLocation(chunkMaxX, 0, chunkMaxZ)
        );

        var minY = hitbox.getMinY();
        var maxY = hitbox.getMaxY();

        return Iterators.transform(chunkIterator.iterator(), location -> new Slice(hitbox, location.scalarMultiply(16), minY, maxY));
    }

    private static final class Slice implements Iterable<MovecraftLocation>{
        private final SolidHitBox bounds;
        private final HitBox oracle;

        public Slice(HitBox basis, MovecraftLocation start, int minY, int maxY){
            bounds = new SolidHitBox(
                    // Shift up to minY
                    start.hadamardProduct(1,0,1).translate(0,minY,0),
                    // Shift to other end of the chunk at top-y
                    start.translate(15, maxY, 15));
            oracle = basis;
        }

        @NotNull
        @Override
        public Iterator<MovecraftLocation> iterator() {
            return oracle.intersection(bounds).iterator();
        }
    }

}
