package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import net.countercraft.movecraft.util.hitboxes.BitmapHitBox;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class CraftCache implements Runnable {

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

    // Wrapper around the timed scheduled task
    private BukkitTask bukkitTask;
    // Queue for udpates
    protected final Queue<CraftPositionUpdate> scheduledUpdates = new ConcurrentLinkedQueue<>();

    public static void onWorldUnload(final UUID world) {
        CraftCache cache = worldMap.get(world);
        if (cache != null) {
            cache.stopTask();
            worldMap.remove(world, cache);
        }
    }

    protected static CraftCache of(final World world) {
        return of(world.getUID());
    }
    protected static CraftCache of(final UUID worldUUID) {
        return worldMap.computeIfAbsent(worldUUID, k -> new CraftCache());
    }

    public static void onCraftFinishedMovement(final Craft craft) {
        of(craft.getWorld()).scheduleUpdate(CraftPositionUpdate.of(craft));
    }

    public static void onCraftLeftWorld(final Craft craft, final UUID oldWorld) {
        of(oldWorld).removeCraftFromChunkLists(craft);
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

    // TODO: Use Long2ObjectOpenHashMap
    protected final Map<ChunkPos, Set<UUID>> chunkMap = new ConcurrentHashMap<>();
    protected static final CraftDataTagKey<Set<WeakReference<Set<UUID>>>> positionCaches = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey(Movecraft.getInstance(), "chunkpos-references"), c -> Collections.synchronizedSet(new HashSet<>()));

    protected static Set<WeakReference<Set<UUID>>> getSetsOfCraft(final Craft craft) {
        return craft.getDataTag(positionCaches);
    }

    public static void removeCraft(final Craft craft) {
        worldMap.values().forEach(cc -> cc.removeCraftFromChunkLists(craft));
    }

    protected void removeCraftFromChunkLists(final Craft craft) {
        Set<WeakReference<Set<UUID>>> setsOfCraft = getSetsOfCraft(craft);
        if (!setsOfCraft.isEmpty()) {
            // First, remove all no longer existing lists
            // Then remove the references to this craft
            // CraftEntry can be compared to Craft; They are qual if the UUID is the same
            setsOfCraft.removeIf(ref -> {
                Set<UUID> uuidSet = ref.get();
                if (uuidSet != null) {
                    uuidSet.remove(craft.getUUID());
                }
                return true;
            });
        }
    }

    @Nullable
    protected Set<UUID> getEntriesAtChunk(MovecraftLocation blockCoordinate) {
        final ChunkPos chunkPos = ChunkPos.of(blockCoordinate);

        return this.chunkMap.getOrDefault(chunkPos, null);
    }

    // Returns all crafts that somehow contain this chunk in their hitbox; No guarantee on if the craft actually has a block there or not!
    protected Set<Craft> getCraftsAtChunkInternal(MovecraftLocation blockCoordinate) {
        Set<Craft> result = new HashSet<>();
        Set<UUID> list = this.getEntriesAtChunk(blockCoordinate);
        if (list != null) {
            for (UUID reference : list) {
                final Craft craft = Craft.getCraftByUUID(reference);
                if (craft == null)
                    continue;

                result.add(craft);
            }
        }

        return result;
    }

    // returns the first craft that contains this position
    protected Optional<Craft> getCraftAtInternal(MovecraftLocation blockCoordinate) {
        Set<UUID> craftsInChunk = this.getEntriesAtChunk(blockCoordinate);
        Craft result = null;
        // Access can happen ASYNCHRONOUSLY!
        if (craftsInChunk != null) {
            if (!craftsInChunk.isEmpty()) {
                for (UUID craftId : craftsInChunk) {
                    final Craft craft = Craft.getCraftByUUID(craftId);
                    if (craft == null)
                        continue;

                    final HitBox hitBox = new BitmapHitBox(craft.getHitBox());
                    if (hitBox.isEmpty())
                        continue;

                    if (hitBox.inBounds(blockCoordinate) && hitBox.contains(blockCoordinate)) {
                        result = craft;
                        break;
                    }
                }
            }
        }
        return Optional.ofNullable(result);
    }

    static final byte MAX_UPDATES_PER_TICK = 50;
    static final int CLEANUP_PERIOD_MS = 10000;
    protected long lastCleanup = System.currentTimeMillis();

    @Override
    public void run() {
        byte counter = 0;
        while (counter < MAX_UPDATES_PER_TICK) {
            final CraftPositionUpdate update = this.scheduledUpdates.poll();
            if (update == null)
                break;

            this.runUpdate(update);

            counter++;
        }

        // If there is time now, we run the cleanup
        final long now = System.currentTimeMillis();
        long delta = now - this.lastCleanup;
        if (delta > CLEANUP_PERIOD_MS) {
            this.lastCleanup = now;
            runCleanup();
        }

        // If we do not have anything left, stop the task
        if (this.scheduledUpdates.isEmpty()) {
            this.stopTask();
        }
    }

    protected void runCleanup() {
        this.chunkMap.entrySet().removeIf(e -> {
            // TODO: Replace that contains clause!
            e.getValue().removeIf(craftUUID -> {
                final Craft craftObject = Craft.getCraftByUUID(craftUUID);
                if (craftObject == null)
                    return true;

                return !CraftManager.getInstance().getCrafts().contains(craftObject);
            });
            return e.getValue().isEmpty();
        });
    }

    protected void runUpdate(final CraftPositionUpdate update) {
        final Craft craftObject = Craft.getCraftByUUID(update.craftUUID());
        if (craftObject != null) {
            removeCraftFromChunkLists(craftObject);
        }

        if (update.empty() || craftObject == null) {
            // In theory unnecessary, we need to test if we will need it again!
            //this.chunkMap.values().forEach(set -> set.remove(update.craftUUID()));
            return;
        }

        // Now, recalculate the chunks of that craft
        final int minChunkX = update.x1() >> 4;
        final int minChunkY = update.y1() >> 4;
        final int minChunkZ = update.z1() >> 4;
        final int maxChunkX = update.x2() >> 4;
        final int maxChunkY = update.y2() >> 4;
        final int maxChunkZ = update.z2() >> 4;

        Set<WeakReference<Set<UUID>>> setsOfCraft = getSetsOfCraft(craftObject);
        for (int iX = minChunkX; iX <= maxChunkX; iX++) {
            for (int iY = minChunkY; iY <= maxChunkY; iY++) {
                for (int iZ = minChunkZ; iZ <= maxChunkZ; iZ++) {
                    ChunkPos chunkPos = new ChunkPos(iX, iY, iZ);
                    Set<UUID> craftList = chunkMap.computeIfAbsent(chunkPos, k -> ConcurrentHashMap.newKeySet());
                    craftList.add(update.craftUUID());
                    setsOfCraft.add(new WeakReference<>(craftList));
                }
            }
        }
    }

    protected synchronized void stopTask() {
        if (this.bukkitTask != null) {
            this.bukkitTask.cancel();
            this.bukkitTask = null;
        }
    }

    protected synchronized void scheduleUpdate(final CraftPositionUpdate update) {
        if (update == null) {
            return;
        }

        // Remove already existing updates for this craft
        this.scheduledUpdates.remove(update);
        // Now schedule the new update
        this.scheduledUpdates.add(update);

        if (this.bukkitTask == null || this.bukkitTask.isCancelled()) {
            this.bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(Movecraft.getInstance(), this, 0, 1);
        }
    }

    // TODO: Change to convert (x, y) => long and (long) => x, y methods!
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

    protected record CraftPositionUpdate(UUID craftUUID, int x1, int x2, int y1, int y2, int z1, int z2, boolean empty) {
        public static CraftPositionUpdate of(Craft craft) {
            final UUID uuid = craft.getUUID();

            final HitBox hitBox = craft.getHitBox();
            if (hitBox == null || hitBox.isEmpty())
                return new CraftPositionUpdate(uuid, 0,0,0,0,0,0,true);

            return new CraftPositionUpdate(uuid, hitBox.getMinX(), hitBox.getMaxX(), hitBox.getMinY(), hitBox.getMaxY(), hitBox.getMinZ(), hitBox.getMaxZ(), false);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj instanceof CraftPositionUpdate other) {
                return this.craftUUID().equals(other.craftUUID());
            }
            return false;
        }
    }

}
