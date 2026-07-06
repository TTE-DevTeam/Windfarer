package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CraftCache {

    // Cache WeakMap<World<Map<ChunkPos<List<WeakReference<Craft>>>>>>
    // Stores a reference to all crafts per chunk
    // Updating has to happen via one AsyncTask that works down a queue of updates
    // Whenever a craft finished a movement operation, its old locations need to be removed and then recalculated
    // For that, the craft holds a reference to the last chunks it was in (SolidHitbox on chunk coordinate level + world reference)
    // Whenever a craft is to be removed or released (=> Hook in CraftManager), it needs to be removed from all lists as well
    // In theory, the oldLoc + currentLoc from FinishedMovement is already enough for us to calculate the difference

    // TODO: Implement MUTEX based Craft state with push() and pop() functions
    // TODO: Implement function that determines if we should add a craft or not
    // TODO: Implement getClosestCraftTo method

    protected static Map<UUID, CraftCache> worldMap = new ConcurrentHashMap<>();

    public static void onWorldUnload(final UUID world) {
        worldMap.remove(world);
    }

    protected static CraftCache of(final World world) {
        return of(world.getUID());
    }
    protected static CraftCache of(final UUID worldUUID) {
        return worldMap.computeIfAbsent(worldUUID, k -> new CraftCache());
    }

    public static void onCraftFinishedMovement(final Craft craft) {
        Bukkit.getScheduler().runTaskAsynchronously(Movecraft.getInstance(), new UpdateCraftPositionRunnable(craft, craft.getWorld().getUID()));
    }

    public static Set<Craft> getCraftsAtChunk(World world, MovecraftLocation blockCoordinate) {
        return getCraftsAtChunk(world.getUID(), blockCoordinate);
    }
    public static Set<Craft> getCraftsAtChunk(UUID worldUUID, MovecraftLocation blockCoordinate) {
        return of(worldUUID).getCraftsAtChunkInternal(blockCoordinate);
    }
    public static Optional<Craft> getCraftAt(World world, MovecraftLocation blockCoordinate) {
        return getCraftAt(world.getUID(), blockCoordinate);
    }
    public static Optional<Craft> getCraftAt(UUID worldUUID, MovecraftLocation blockCoordinate) {
        return of(worldUUID).getCraftAtInternal(blockCoordinate);
    }

    protected CraftCache() {

    }

    protected final Map<ChunkPos, List<WeakReference<Craft>>> chunkMap = new ConcurrentHashMap<>();
    protected static final CraftDataTagKey<Set<WeakReference<List<WeakReference<Craft>>>>> positionCaches = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey(Movecraft.getInstance(), "chunkpos-references"), c -> Collections.synchronizedSet(new HashSet<>()));

    protected void cleanup() {
        this.chunkMap.entrySet().removeIf(e -> {
            // TODO: Replace that contains clause!
            e.getValue().removeIf(ref -> ref.get() == null || !CraftManager.getInstance().getCrafts().contains(ref.get()));
            return e.getValue().isEmpty();
        });
    }

    protected static Set<WeakReference<List<WeakReference<Craft>>>> getSetsOfCraft(final Craft craft) {
        return craft.getDataTag(positionCaches);
    }

    public static void removeCraft(final Craft craft) {
        worldMap.values().forEach(cc -> cc.removeCraftInternal(craft));
    }

    protected void removeCraftInternal(final Craft craft) {
        final WeakReference<Craft> reference = new WeakReference<>(craft);
        for (List<WeakReference<Craft>> list : this.chunkMap.values()) {
            list.remove(reference);
        }
        this.cleanup();
    }

    // Returns all crafts that somehow contain this chunk in their hitbox; No guarantee on if the craft actually has a block there or not!
    protected Set<Craft> getCraftsAtChunkInternal(MovecraftLocation blockCoordinate) {
        final ChunkPos chunkPos = ChunkPos.of(blockCoordinate);
        Set<Craft> result = new HashSet<>();

        // Very important: Cleanup first!
        this.cleanup();

        List<WeakReference<Craft>> list = this.chunkMap.getOrDefault(chunkPos, null);
        if (list != null) {
            for (WeakReference<Craft> reference : list) {
                Craft deReferenced = reference.get();
                if (deReferenced != null) {
                    result.add(deReferenced);
                }
            }
        }

        return result;
    }

    // returns the first craft that contains this position
    protected Optional<Craft> getCraftAtInternal(MovecraftLocation blockCoordinate) {
        Set<Craft> craftsInChunk = this.getCraftsAtChunkInternal(blockCoordinate);
        Craft result = null;
        if (!craftsInChunk.isEmpty()) {
            for (Craft craft : craftsInChunk) {
                if (craft.getHitBox().inBounds(blockCoordinate) && craft.getHitBox().contains(blockCoordinate)) {
                    result = craft;
                    break;
                }
            }
        }
        return Optional.ofNullable(result);
    }

    protected void onCraftFinishedMovementInternal(final Craft craft) {
        Set<WeakReference<List<WeakReference<Craft>>>> setsOfCraft = getSetsOfCraft(craft);
        if (!setsOfCraft.isEmpty()) {
            // First, remove all no longer existing lists
            setsOfCraft.removeIf(ref -> ref.get() == null);
            // Then remove the references to this craft
            setsOfCraft.forEach(ref -> ref.get().remove(new WeakReference<>(craft)));
            setsOfCraft.clear();
        }
        // Now, recalculate the chunks of that craft
        final int minChunkX = craft.getHitBox().getMinX() >> 4;
        final int minChunkY = craft.getHitBox().getMinY() >> 4;
        final int minChunkZ = craft.getHitBox().getMinZ() >> 4;
        final int maxChunkX = craft.getHitBox().getMaxX() >> 4;
        final int maxChunkY = craft.getHitBox().getMaxY() >> 4;
        final int maxChunkZ = craft.getHitBox().getMaxZ() >> 4;

        final WeakReference<Craft> craftWeakReference = new WeakReference<>(craft);
        for (int iX = minChunkX; iX <= maxChunkX; iX++) {
            for (int iY = minChunkY; iY <= maxChunkY; iY++) {
                for (int iZ = minChunkZ; iZ <= maxChunkZ; iZ++) {
                    ChunkPos chunkPos = new ChunkPos(iX, iY, iZ);
                    List<WeakReference<Craft>> craftList = chunkMap.computeIfAbsent(chunkPos, k -> Collections.synchronizedList(new ArrayList<>()));
                    craftList.add(craftWeakReference);
                    setsOfCraft.add(new WeakReference<>(craftList));
                }
            }
        }

        // Finally, run cache cleanup
        this.cleanup();
    }

    record ChunkPos(int chunkX, int chunkY, int chunkZ) {
        public static ChunkPos of(MovecraftLocation location) {
            return new ChunkPos(location.getX() >> 4, location.getY() >> 4, location.getZ() >> 4);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ChunkPos other) {
                return chunkX() == other.chunkX() && chunkY() == other.chunkY() && chunkZ() == other.chunkZ();
            }
            return false;
        }

        public int hashCode() {
            return 131 * 131 * chunkX + 131 * chunkZ + chunkY;
        }

    }

    protected record UpdateCraftPositionRunnable(Craft craft, UUID worldUUID) implements Runnable {

        @Override
        public void run() {
            of(worldUUID).onCraftFinishedMovementInternal(craft);
        }
    }

}
