package net.countercraft.movecraft.util.hitboxes;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;
import net.countercraft.movecraft.MovecraftLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

// Created by OhNoey on APDev
/**
 * Splits a hitbox into chunk columns (16x16 blocks in x/z, full height) so they can be processed in parallel.
 * <p>
 * The hitbox is traversed exactly once when {@link #iterator()} is called and every block is put into the bucket of
 * its chunk column. Compared to intersecting the whole hitbox with a solid box per chunk column (O(blocks * slices)),
 * this is O(blocks).
 * <ul>
 *     <li>The slices are a partition of the hitbox: every block is part of exactly one slice.</li>
 *     <li>Only non-empty chunk columns are returned. They are ordered by chunk z, then chunk x.</li>
 *     <li>An empty hitbox yields no slices (it does not throw).</li>
 *     <li>The slices are immutable snapshots, they can be iterated repeatedly and from any thread, even when the
 *     hitbox is modified afterwards. The traversal of the hitbox itself is only as thread-safe as the hitbox's own
 *     iterator ({@link SynchronizedBitmapHitBox} iterates over a snapshot).</li>
 * </ul>
 * A block is stored as one {@code int} per bucket: 4 bits local x, 4 bits local z and the 12 bit y that
 * {@link MovecraftLocation#pack()} uses as well, which means y must be in [{@value #MIN_Y}, {@value #MAX_Y}].
 */
public class HitBoxSlicer implements Iterable<Iterable<MovecraftLocation>> {
    // TODO: Support different slice dimensions!

    /** Same 12 bit signed range as {@link MovecraftLocation#pack()}. */
    public static final int MIN_Y = -2048;
    public static final int MAX_Y = 2047;

    /** Order by chunk z, then chunk x. */
    private static final LongComparator KEY_ORDER = (a, b) -> {
        final int byZ = Integer.compare(chunkZ(a), chunkZ(b));
        return byZ != 0 ? byZ : Integer.compare(chunkX(a), chunkX(b));
    };

    private final HitBox hitbox;

    public HitBoxSlicer(HitBox hitbox) {
        this.hitbox = hitbox;
    }

    @NotNull
    @Override
    public Iterator<Iterable<MovecraftLocation>> iterator() {
        final Long2ObjectOpenHashMap<IntArrayList> buckets = new Long2ObjectOpenHashMap<>();
        for (MovecraftLocation location : hitbox) {
            final int x = location.getX();
            final int y = location.getY();
            final int z = location.getZ();
            if (y < MIN_Y || y > MAX_Y) {
                throw new IllegalStateException("Y coordinate " + y + " of " + location + " is outside of [" + MIN_Y + ", " + MAX_Y + "]");
            }
            // Arithmetic shift: rounds towards negative infinity, which is what we need for negative coordinates
            final long key = chunkKey(x >> 4, z >> 4);
            IntArrayList bucket = buckets.get(key);
            if (bucket == null) {
                bucket = new IntArrayList();
                buckets.put(key, bucket);
            }
            bucket.add(encode(x & 15, y, z & 15));
        }
        if (buckets.isEmpty()) {
            return Collections.emptyIterator();
        }

        final long[] keys = buckets.keySet().toLongArray();
        LongArrays.quickSort(keys, KEY_ORDER);
        final List<Iterable<MovecraftLocation>> slices = new ArrayList<>(keys.length);
        for (long key : keys) {
            final IntArrayList bucket = buckets.get(key);
            bucket.trim();
            slices.add(new Slice(chunkX(key) << 4, chunkZ(key) << 4, bucket));
        }
        return slices.iterator();
    }

    /** The low half has to be masked, otherwise a negative chunk x would overwrite the chunk z half. */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) {
        return (int) key;
    }

    private static int chunkZ(long key) {
        return (int) (key >> 32);
    }

    /** Layout: bits 0-3 local x, bits 4-7 local z, bits 8-19 y (12 bit, two's complement). */
    private static int encode(int localX, int y, int localZ) {
        return ((y & 0xFFF) << 8) | (localZ << 4) | localX;
    }

    private static final class Slice implements Iterable<MovecraftLocation> {
        private final int baseX;
        private final int baseZ;
        private final IntArrayList encoded;

        private Slice(int baseX, int baseZ, IntArrayList encoded) {
            this.baseX = baseX;
            this.baseZ = baseZ;
            this.encoded = encoded;
        }

        @NotNull
        @Override
        public Iterator<MovecraftLocation> iterator() {
            return new Iterator<>() {
                private int index = 0;

                @Override
                public boolean hasNext() {
                    return index < encoded.size();
                }

                @Override
                public MovecraftLocation next() {
                    if (index >= encoded.size()) {
                        throw new NoSuchElementException();
                    }
                    final int value = encoded.getInt(index++);
                    return new MovecraftLocation(
                            baseX + (value & 15),
                            // Sign-extends the 12 bit y
                            (value << 12) >> 20,
                            baseZ + ((value >> 4) & 15)
                    );
                }
            };
        }

        @Override
        public String toString() {
            return "Slice[chunkX=" + (baseX >> 4) + ", chunkZ=" + (baseZ >> 4) + ", size=" + encoded.size() + "]";
        }
    }

}
