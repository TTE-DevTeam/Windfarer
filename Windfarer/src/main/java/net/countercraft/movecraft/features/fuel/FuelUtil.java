package net.countercraft.movecraft.features.fuel;

import com.google.common.collect.Sets;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.processing.functions.TriadicPredicate;
import net.countercraft.movecraft.util.BlockCollectionUtil;
import net.countercraft.movecraft.util.NamespacedIDUtil;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.NamespacedKey;

import java.util.HashSet;
import java.util.Set;

import static net.countercraft.movecraft.features.fuel.FuelDataTags.FURNACES_KEY;
import static net.countercraft.movecraft.features.fuel.FuelDataTags.SOLID_FUEL_KEY;

public class FuelUtil {

    public static boolean doesBurnFuel(final Craft craft) {
        if (craft instanceof SinkingCraft) {
            return false;
        }
        // TODO: Squadrons are subcrafts too! So treat them properly
        if (craft instanceof SubCraft) {
            return false;
        }
        double fuelBurnRate = craft.getCraftProperties().get(PropertyKeys.FUEL_BURN_RATE, craft.getMovecraftWorld());
        return fuelBurnRate > 0.0D;
    }

    public static boolean onlyBurnsFuelOnMovement(final Craft craft) {
        return craft.getCraftProperties().get(PropertyKeys.ONLY_CONSUME_FUEL_ON_MOVEMENT);
    }

    // Can be used by addons to exclude specific positions from usage for solid fuel or burners
    public static NamespacedKey buildIllegalTrackingListKeyFor(final NamespacedKey trackedListId) {
        return new NamespacedKey(trackedListId.namespace(), "illegal/" + trackedListId.getKey());
    }

    public static Set<TrackedLocation> getBlocksAsync(final Craft craft, NamespacedKey trackedListId, final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate) {
        // Reset set if necessary
        Set<TrackedLocation> result = craft.getTrackedLocations().getOrDefault(trackedListId, null);
        Set<TrackedLocation> illegal = craft.getTrackedLocations().getOrDefault(buildIllegalTrackingListKeyFor(trackedListId), null);
        // TODO: Squadrons / Subcraft detection will interfere here! For now, do not refresh
        // DONE: Add another trackedlocation list that represents illegal positions for burners and blocks
        if (result == null /*|| this.craft.getDataTag(NEXT_FURNACE_CALCULATION) <= System.currentTimeMillis()*/) {
            result = Sets.newConcurrentHashSet();
        } else {
            return result;
        }

        Set<MovecraftLocation> candidates = BlockCollectionUtil.getLocations(craft, checkPredicate);

        Movecraft.getInstance().getLogger().info(String.format("Burners found in total: %d", candidates.size()));
        // Remove all tracked locations that are "here"
        // Remove all tracked locations checking if the tracked locations are the same => same vector and same craft
        if (illegal != null) {
            Movecraft.getInstance().getLogger().info(String.format("Illegal burners: %d", illegal.size()));
            //result.removeAll(illegal);

            // Somehow, TrackedLocations are not reliably checked earlier on, so we use this dirty hack :/
            Set<MovecraftLocation> illegals = new HashSet<>(illegal != null ? illegal.size() : 0);
            for (TrackedLocation trackedLocation : illegal) {
                illegals.add(trackedLocation.getAbsoluteLocation());
            }
            candidates.removeIf(illegals::contains);
            Movecraft.getInstance().getLogger().info(String.format("Burners left after removing illegal ones: %d", candidates.size()));
        }

        for (MovecraftLocation loc : candidates) {
            result.add(new TrackedLocation(craft, loc));
        }

        craft.getTrackedLocations().put(trackedListId, result);
        return result;
    }

    public static Set<TrackedLocation> getFuelBurners(final Craft craft) {
        return getBlocksAsync(craft, FURNACES_KEY, (l, w, c) -> {
            return Result.of(Tags.FURNACES.contains(w.getMaterial(l)));
        });
    }

    public static Set<TrackedLocation> getSolidFuelBlocks(final Craft craft) {
        final Set<NamespacedKey> blockSet = Sets.newConcurrentHashSet(craft.getCraftProperties().get(PropertyKeys.FUEL_TYPES).getContainedBlockIDs());
        return getBlocksAsync(craft, SOLID_FUEL_KEY, (l, w, c) -> {
            NamespacedKey blockId = NamespacedIDUtil.getBlockID(w.getData(l));
            return Result.of(blockSet.contains(blockId));
        });
    }

}
