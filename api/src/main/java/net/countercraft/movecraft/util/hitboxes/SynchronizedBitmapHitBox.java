package net.countercraft.movecraft.util.hitboxes;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.exception.EmptyHitBoxException;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.roaringbitmap.longlong.LongIterator;
import org.roaringbitmap.longlong.Roaring64NavigableMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe counterpart of {@link BitmapHitBox}.
 * <p>
 * Guarantees:
 * <ul>
 *     <li>Every single method call is atomic. Reads run in parallel, writes are exclusive
 *     (a {@link ReentrantReadWriteLock} guards all state).</li>
 *     <li>Sequences of calls are <b>not</b> atomic (e.g. {@code getMinX()} followed by {@code getMaxX()}).
 *     Use {@link #getBounds()} for a consistent view of all six bounds and {@link #snapshot()} for a
 *     consistent copy of the whole hitbox.</li>
 *     <li>{@link #iterator()} iterates over a point-in-time copy. It never throws a
 *     {@code ConcurrentModificationException} and never blocks writers while it is being consumed.
 *     It does not support {@code remove()}.</li>
 *     <li>Collections / hitboxes passed as arguments are copied <i>before</i> this instance's lock is taken.
 *     Two locks are therefore never held at the same time, which rules out lock-ordering deadlocks
 *     (e.g. {@code a.addAll(b)} racing with {@code b.addAll(a)}) and makes {@code a.addAll(a)} safe.</li>
 *     <li>{@code difference}, {@code intersection}, {@code union} and {@code symmetricDifference}
 *     return new, independent instances of this class.</li>
 * </ul>
 * Like any object with non-final state, an instance must be safely published to other threads
 * (final field, executor hand-off, volatile, ...).
 */
public final class SynchronizedBitmapHitBox implements MutableHitBox {

    /** Mirrors {@link BitmapHitBox#getMinYAt(int, int)}: returned when the column contains no block. */
    private static final int NO_LOCAL_MIN_Y = -1;

    /**
     * Immutable, internally consistent view of the bounding box.
     */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    // --- everything below is guarded by 'lock' ---------------------------------------------------------------
    // The default state (size == 0, no dirty bounds) is the valid "empty" state, min/max are only meaningful
    // while size > 0.
    private final Roaring64NavigableMap backing;
    private final Long2IntMap localMinY = new Long2IntOpenHashMap();
    /**
     * Tracked manually: Roaring64NavigableMap caches cardinalities lazily (a hidden write), which would not be
     * safe under the shared read lock.
     */
    private int size = 0;
    private boolean boundsDirty = false;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    // ---------------------------------------------------------------------------------------------------------

    public SynchronizedBitmapHitBox() {
        backing = new Roaring64NavigableMap(true);
    }

    public SynchronizedBitmapHitBox(@NotNull HitBox hitBox) {
        this();
        addAll(hitBox);
    }

    public SynchronizedBitmapHitBox(@NotNull Collection<? extends MovecraftLocation> locations) {
        this();
        addAll(locations);
    }

    /**
     * Takes ownership of {@code backing}, the caller must not touch it afterwards.
     */
    private SynchronizedBitmapHitBox(@NotNull Roaring64NavigableMap backing) {
        this.backing = backing;
        this.size = Math.toIntExact(backing.getLongCardinality());
        this.boundsDirty = this.size > 0;
    }

    // ================================================================================================ mutation

    @Override
    public boolean add(@NotNull MovecraftLocation location) {
        long packed = location.pack();
        writeLock.lock();
        try {
            if (backing.contains(packed)) {
                return false;
            }
            if (!boundsDirty) {
                expandBounds(location.getX(), location.getY(), location.getZ());
            }
            backing.addLong(packed);
            size++;
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean addAll(@NotNull Collection<? extends MovecraftLocation> collection) {
        return addAllPacked(bitmapOf(collection));
    }

    @Override
    public boolean addAll(@NotNull HitBox hitBox) {
        return addAllPacked(snapshotOf(hitBox));
    }

    @Override
    public boolean remove(@NotNull MovecraftLocation location) {
        long packed = location.pack();
        writeLock.lock();
        try {
            return removeUnlocked(packed);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean removeAll(@NotNull Collection<? extends MovecraftLocation> collection) {
        return removeAllPacked(bitmapOf(collection));
    }

    @Override
    public boolean removeAll(@NotNull HitBox hitBox) {
        return removeAllPacked(snapshotOf(hitBox));
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            backing.clear();
            localMinY.clear();
            size = 0;
            boundsDirty = false;
        } finally {
            writeLock.unlock();
        }
    }

    // ================================================================================================ queries

    @Override
    public int size() {
        readLock.lock();
        try {
            return size;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean isEmpty() {
        readLock.lock();
        try {
            return size == 0;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean contains(@NotNull MovecraftLocation location) {
        long packed = location.pack();
        readLock.lock();
        try {
            return backing.contains(packed);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean contains(int x, int y, int z) {
        long packed = MovecraftLocation.pack(x, y, z);
        readLock.lock();
        try {
            return backing.contains(packed);
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public boolean containsAll(Collection<? extends MovecraftLocation> collection) {
        Roaring64NavigableMap wanted = bitmapOf(collection);
        readLock.lock();
        try {
            LongIterator it = wanted.getLongIterator();
            while (it.hasNext()) {
                if (!backing.contains(it.next())) {
                    return false;
                }
            }
            return true;
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Iterates over a point-in-time copy of this hitbox.
     */
    @NotNull
    @Override
    public Iterator<MovecraftLocation> iterator() {
        final LongIterator it = copyBacking().getLongIterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public MovecraftLocation next() {
                return MovecraftLocation.unpack(it.next());
            }
        };
    }

    /**
     * Returns a consistent, non-thread-safe copy of this hitbox.
     */
    @NotNull
    public BitmapHitBox snapshot() {
        return new BitmapHitBox(this);
    }

    // ================================================================================================ bounds

    /**
     * Returns all six bounds from one single point in time.
     *
     * @throws EmptyHitBoxException if the hitbox is empty
     */
    @NotNull
    public Bounds getBounds() {
        return bounds();
    }

    @Override
    public int getMinX() {
        return bounds().minX();
    }

    @Override
    public int getMinY() {
        return bounds().minY();
    }

    @Override
    public int getMinZ() {
        return bounds().minZ();
    }

    @Override
    public int getMaxX() {
        return bounds().maxX();
    }

    @Override
    public int getMaxY() {
        return bounds().maxY();
    }

    @Override
    public int getMaxZ() {
        return bounds().maxZ();
    }

    @Override
    public int getMinYAt(int x, int z) {
        long key = columnKey(x, z);
        readLock.lock();
        try {
            if (size == 0) {
                throw new EmptyHitBoxException();
            }
            if (!boundsDirty) {
                return localMinY.getOrDefault(key, NO_LOCAL_MIN_Y);
            }
        } finally {
            readLock.unlock();
        }
        writeLock.lock();
        try {
            if (size == 0) {
                throw new EmptyHitBoxException();
            }
            if (boundsDirty) {
                validateBounds();
            }
            return localMinY.getOrDefault(key, NO_LOCAL_MIN_Y);
        } finally {
            writeLock.unlock();
        }
    }

    // The following defaults of HitBox combine several getters. They are overridden so that they
    // work on one consistent Bounds instance.

    @Override
    public int getXLength() {
        Bounds b = currentBounds();
        return b == null ? 0 : Math.abs(b.maxX() - b.minX() + 1);
    }

    @Override
    public int getYLength() {
        Bounds b = currentBounds();
        return b == null ? 0 : Math.abs(b.maxY() - b.minY() + 1);
    }

    @Override
    public int getZLength() {
        Bounds b = currentBounds();
        return b == null ? 0 : Math.abs(b.maxZ() - b.minZ() + 1);
    }

    @NotNull
    @Override
    public MovecraftLocation getMidPoint() throws EmptyHitBoxException {
        Bounds b = bounds();
        return new MovecraftLocation(average(b.maxX(), b.minX()), average(b.maxY(), b.minY()), average(b.maxZ(), b.minZ()));
    }

    @Override
    public boolean inBounds(double x, double y, double z) {
        Bounds b = currentBounds();
        if (b == null) {
            return false;
        }
        return x >= b.minX() && x <= b.maxX() &&
                y >= b.minY() && y <= b.maxY() &&
                z >= b.minZ() && z <= b.maxZ();
    }

    @NotNull
    @Override
    public SolidHitBox boundingHitBox() {
        Bounds b = bounds();
        return new SolidHitBox(new MovecraftLocation(b.minX(), b.minY(), b.minZ()),
                new MovecraftLocation(b.maxX(), b.maxY(), b.maxZ()));
    }

    @Override
    public BoundingBox asBoundingBox() {
        Bounds b = bounds();
        return BoundingBox.of(new Vector(b.minX(), b.minY(), b.minZ()), new Vector(b.maxX(), b.maxY(), b.maxZ()));
    }

    // ================================================================================================ set operations

    @NotNull
    @Override
    public SynchronizedBitmapHitBox difference(HitBox other) {
        Roaring64NavigableMap otherBitmap = snapshotOf(other);
        Roaring64NavigableMap result = copyBacking();
        result.andNot(otherBitmap);
        return new SynchronizedBitmapHitBox(result);
    }

    @NotNull
    @Override
    public SynchronizedBitmapHitBox intersection(HitBox other) {
        Roaring64NavigableMap otherBitmap = snapshotOf(other);
        Roaring64NavigableMap result = copyBacking();
        result.and(otherBitmap);
        return new SynchronizedBitmapHitBox(result);
    }

    @NotNull
    @Override
    public SynchronizedBitmapHitBox union(HitBox other) {
        Roaring64NavigableMap otherBitmap = snapshotOf(other);
        Roaring64NavigableMap result = copyBacking();
        result.or(otherBitmap);
        return new SynchronizedBitmapHitBox(result);
    }

    @NotNull
    @Override
    public SynchronizedBitmapHitBox symmetricDifference(HitBox other) {
        Roaring64NavigableMap otherBitmap = snapshotOf(other);
        Roaring64NavigableMap result = copyBacking();
        result.xor(otherBitmap);
        return new SynchronizedBitmapHitBox(result);
    }

    // ================================================================================================ internals

    /**
     * Copy of the backing bitmap, taken under the read lock.
     */
    private Roaring64NavigableMap copyBacking() {
        Roaring64NavigableMap copy = new Roaring64NavigableMap(true);
        readLock.lock();
        try {
            copy.or(backing);
        } finally {
            readLock.unlock();
        }
        return copy;
    }

    /**
     * Private bitmap with the content of {@code hitBox}. Never called with a lock of this instance held.
     */
    private static Roaring64NavigableMap snapshotOf(@NotNull HitBox hitBox) {
        if (hitBox instanceof SynchronizedBitmapHitBox other) {
            return other.copyBacking();
        }
        return bitmapOf(hitBox);
    }

    private static Roaring64NavigableMap bitmapOf(@NotNull Iterable<? extends MovecraftLocation> locations) {
        Roaring64NavigableMap out = new Roaring64NavigableMap(true);
        for (MovecraftLocation location : locations) {
            out.addLong(location.pack());
        }
        return out;
    }

    /**
     * Adds all entries of {@code toAdd}, which must be private to the caller.
     */
    private boolean addAllPacked(Roaring64NavigableMap toAdd) {
        writeLock.lock();
        try {
            if (toAdd.isEmpty()) {
                return false;
            }
            if (boundsDirty) {
                // Bounds are recomputed lazily anyway, so the fast bulk path is enough.
                int before = size;
                backing.or(toAdd);
                size = Math.toIntExact(backing.getLongCardinality());
                return size != before;
            }
            boolean changed = false;
            LongIterator it = toAdd.getLongIterator();
            while (it.hasNext()) {
                long packed = it.next();
                if (backing.contains(packed)) {
                    continue;
                }
                MovecraftLocation location = MovecraftLocation.unpack(packed);
                expandBounds(location.getX(), location.getY(), location.getZ());
                backing.addLong(packed);
                size++;
                changed = true;
            }
            return changed;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Removes all entries of {@code toRemove}, which must be private to the caller.
     */
    private boolean removeAllPacked(Roaring64NavigableMap toRemove) {
        writeLock.lock();
        try {
            boolean changed = false;
            LongIterator it = toRemove.getLongIterator();
            while (it.hasNext()) {
                changed |= removeUnlocked(it.next());
            }
            return changed;
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Write lock must be held.
     */
    private boolean removeUnlocked(long packed) {
        if (!backing.contains(packed)) {
            return false;
        }
        if (!boundsDirty && touchesBounds(MovecraftLocation.unpack(packed))) {
            boundsDirty = true;
        }
        backing.removeLong(packed);
        size--;
        if (size == 0) {
            localMinY.clear();
            boundsDirty = false;
        }
        return true;
    }

    /**
     * Whether removing {@code location} could change a cached bound. Write lock must be held.
     */
    private boolean touchesBounds(MovecraftLocation location) {
        return location.getX() == minX ||
                location.getY() == minY ||
                location.getZ() == minZ ||
                location.getX() == maxX ||
                location.getY() == maxY ||
                location.getZ() == maxZ ||
                localMinY.getOrDefault(columnKey(location.getX(), location.getZ()), NO_LOCAL_MIN_Y) == location.getY();
    }

    /**
     * Must be called <i>before</i> {@code size} is incremented. Write lock must be held.
     */
    private void expandBounds(int x, int y, int z) {
        if (size == 0) {
            minX = maxX = x;
            minY = maxY = y;
            minZ = maxZ = z;
        } else {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        long key = columnKey(x, z);
        if (y < localMinY.getOrDefault(key, Integer.MAX_VALUE)) {
            localMinY.put(key, y);
        }
    }

    /**
     * Recomputes all bounds from scratch. Write lock must be held and {@code size > 0}.
     */
    private void validateBounds() {
        int nMinX = Integer.MAX_VALUE, nMinY = Integer.MAX_VALUE, nMinZ = Integer.MAX_VALUE;
        int nMaxX = Integer.MIN_VALUE, nMaxY = Integer.MIN_VALUE, nMaxZ = Integer.MIN_VALUE;
        localMinY.clear();
        LongIterator it = backing.getLongIterator();
        while (it.hasNext()) {
            MovecraftLocation location = MovecraftLocation.unpack(it.next());
            int x = location.getX();
            int y = location.getY();
            int z = location.getZ();
            nMinX = Math.min(nMinX, x);
            nMinY = Math.min(nMinY, y);
            nMinZ = Math.min(nMinZ, z);
            nMaxX = Math.max(nMaxX, x);
            nMaxY = Math.max(nMaxY, y);
            nMaxZ = Math.max(nMaxZ, z);
            long key = columnKey(x, z);
            if (y < localMinY.getOrDefault(key, Integer.MAX_VALUE)) {
                localMinY.put(key, y);
            }
        }
        minX = nMinX;
        minY = nMinY;
        minZ = nMinZ;
        maxX = nMaxX;
        maxY = nMaxY;
        maxZ = nMaxZ;
        boundsDirty = false;
    }

    /**
     * Consistent bounds, or {@code null} if empty. Validates lazily: the common case only needs the read lock,
     * a dirty cache is rebuilt under the write lock.
     */
    @Nullable
    private Bounds currentBounds() {
        readLock.lock();
        try {
            if (size == 0) {
                return null;
            }
            if (!boundsDirty) {
                return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
            }
        } finally {
            readLock.unlock();
        }
        writeLock.lock();
        try {
            if (size == 0) {
                return null;
            }
            if (boundsDirty) {
                validateBounds();
            }
            return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
        } finally {
            writeLock.unlock();
        }
    }

    @NotNull
    private Bounds bounds() {
        Bounds bounds = currentBounds();
        if (bounds == null) {
            throw new EmptyHitBoxException();
        }
        return bounds;
    }

    private static long columnKey(int x, int z) {
        // z must be masked, otherwise a negative z sign-extends and overwrites the x half of the key
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Same formula as {@link HitBox#getMidPoint()} (private there).
     */
    private static int average(int high, int low) {
        return (high & low) + (high ^ low) / 2;
    }
}
