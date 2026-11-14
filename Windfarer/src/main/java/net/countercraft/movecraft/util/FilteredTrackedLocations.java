package net.countercraft.movecraft.util;

import com.google.common.collect.Sets;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.functions.TriadicPredicate;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// Utility class to warp around cached trackedlocations accessible via DataTagKey
public class FilteredTrackedLocations implements Iterable<TrackedLocation> {

    protected final NamespacedKey listKey;
    protected final NamespacedKey lastUpdateKey;
    protected final long lifetime;
    protected long lastUpdate = System.currentTimeMillis();
    protected final WeakReference<Craft> craftWeakReference;
    protected final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> testPredicate;
    private CompletableFuture<Set<TrackedLocation>> inFlight;

    public FilteredTrackedLocations(final Craft craft, NamespacedKey listKey, long lifetime, TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> testPredicate) {
        this.listKey = listKey;
        this.lifetime = lifetime;
        this.lastUpdate = System.currentTimeMillis() - this.lifetime - 1;
        this.craftWeakReference = new WeakReference<>(craft);
        this.testPredicate = testPredicate;
        this.lastUpdateKey = new NamespacedKey(listKey.namespace(), listKey.getKey() + "/last_update_timestamp");
    }

    /**
     * Asynchronously obtains the cached locations, computing them only once when
     * the cache is missing or expired. Concurrent callers share the same in-flight
     * calculation instead of scheduling duplicate hitbox scans.
     */
    public CompletableFuture<Set<TrackedLocation>> getSetAsync() {
        final Craft craft = this.craftWeakReference.get();
        if (craft == null) {
            return CompletableFuture.completedFuture(Sets.newConcurrentHashSet());
        }

        synchronized (this) {
            if (this.lifetime > 0 && System.currentTimeMillis() - lastUpdate > this.lifetime) {
                craft.getTrackedLocations().remove(this.listKey);
            }

            Set<TrackedLocation> cached = craft.getTrackedLocations().getOrDefault(this.listKey, null);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            if (inFlight != null) {
                return inFlight;
            }

            lastUpdate = System.currentTimeMillis();
            CompletableFuture<Set<TrackedLocation>> calculation = BlockCollectionUtil
                    .getLocationsAsync(craft, this.testPredicate)
                    .thenApply(locations -> {
                        Set<TrackedLocation> result = Sets.newConcurrentHashSet();
                        for (MovecraftLocation location : locations) {
                            result.add(new TrackedLocation(craft, location));
                        }
                        craft.getTrackedLocations().put(this.listKey, result);
                        return result;
                    });

            inFlight = calculation;
            calculation.whenComplete((result, throwable) -> {
                synchronized (FilteredTrackedLocations.this) {
                    if (inFlight == calculation) {
                        inFlight = null;
                    }
                }
            });
            return calculation;
        }
    }

    protected Set<TrackedLocation> getSet() {
        final Craft craft = this.craftWeakReference.get();
        if (craft == null) {
            return Sets.newConcurrentHashSet();
        }
        // Remove the tracked location list if it is too old
        if (this.lifetime > 0) {
            if (System.currentTimeMillis() - lastUpdate > this.lifetime) {
                craft.getTrackedLocations().remove(this.listKey);
            }
        }
        // Now, access it and return it
        Set<TrackedLocation> result = craft.getTrackedLocations().getOrDefault(this.listKey, null);
        if (result == null) {
            result = this.computeList(craft);
            craft.getTrackedLocations().put(this.listKey, result);
        }
        return result;
    }

    protected Set<TrackedLocation> computeList(final Craft craft) {
        lastUpdate = System.currentTimeMillis();
        final Set<TrackedLocation> result = Sets.newConcurrentHashSet();
        Set<MovecraftLocation> banners = BlockCollectionUtil.getLocations(craft, this.testPredicate);
        banners.forEach(loc -> result.add(new TrackedLocation(craft, loc)));
        return result;
    }

    @Override
    public @NotNull Iterator<TrackedLocation> iterator() {
        return getSet().iterator();
    }
}
