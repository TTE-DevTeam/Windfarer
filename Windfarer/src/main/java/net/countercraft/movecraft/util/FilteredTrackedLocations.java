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

// Utility class to warp around cached trackedlocations accessible via DataTagKey
public class FilteredTrackedLocations implements Iterable<TrackedLocation> {

    protected final NamespacedKey listKey;
    protected final NamespacedKey lastUpdateKey;
    protected final long lifetime;
    protected long lastUpdate = System.currentTimeMillis();
    protected final WeakReference<Craft> craftWeakReference;
    protected final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> testPredicate;

    public FilteredTrackedLocations(final Craft craft, NamespacedKey listKey, long lifetime, TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> testPredicate) {
        this.listKey = listKey;
        this.lifetime = lifetime;
        this.lastUpdate = System.currentTimeMillis() - this.lifetime - 1;
        this.craftWeakReference = new WeakReference<>(craft);
        this.testPredicate = testPredicate;
        this.lastUpdateKey = new NamespacedKey(listKey.namespace(), listKey.getKey() + "/last_update_timestamp");
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
