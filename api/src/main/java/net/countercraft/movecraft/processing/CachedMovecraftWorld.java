package net.countercraft.movecraft.processing;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.support.AsyncChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A per-world cache of thread-safe chunk accessors.
 *
 * Chunk accessors are created on the server thread and are protected by a plugin
 * chunk ticket for as long as their cache entry remains relevant.
 */
public final class CachedMovecraftWorld implements MovecraftWorld {

    private static final ConcurrentMap<UUID, CachedMovecraftWorld> WORLDS = new ConcurrentHashMap<>();

    private static final long CHUNK_IDLE_NANOS = TimeUnit.SECONDS.toNanos(45);

    public static MovecraftWorld of(@NotNull World world) {
        return WORLDS.compute(world.getUID(), (uuid, existing) -> {
            if (existing == null || existing.closed || existing.world != world) {
                return new CachedMovecraftWorld(world);
            }
            return existing;
        });
    }

    /**
     * Purges only expired chunk entries. World caches remain isolated and are
     * removed explicitly when their worlds unload.
     */
    public static void purgeExpired() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Chunk cache purging must run on the main thread.");
        }

        long now = System.nanoTime();
        for (CachedMovecraftWorld cachedWorld : WORLDS.values()) {
            cachedWorld.purgeExpired(now);
        }
    }

    /**
     * Kept for source compatibility. Purging no longer clears every world.
     */
    @Deprecated
    public static void purge() {
        purgeExpired();
    }

    public static void invalidateChunk(@NotNull World world, int chunkX, int chunkZ) {
        requireMainThread();
        CachedMovecraftWorld cachedWorld = WORLDS.get(world.getUID());
        if (cachedWorld != null && cachedWorld.world == world) {
            cachedWorld.invalidateChunk(new ChunkLocation(chunkX, chunkZ));
        }
    }

    public static void invalidateWorld(@NotNull World world) {
        requireMainThread();
        CachedMovecraftWorld cachedWorld = WORLDS.remove(world.getUID());
        if (cachedWorld != null) {
            cachedWorld.close();
        }
    }

    /**
     * Releases every cache entry and plugin chunk ticket. Called during plugin disable.
     */
    public static void invalidateAll() {
        requireMainThread();
        for (CachedMovecraftWorld cachedWorld : WORLDS.values()) {
            cachedWorld.close();
        }
        WORLDS.clear();

        Plugin plugin = WorldManager.INSTANCE.getPlugin();
        if (plugin != null) {
            for (World world : Bukkit.getWorlds()) {
                try {
                    world.removePluginChunkTickets(plugin);
                } catch (IllegalStateException ignored) {
                    // The server may already be shutting down this world.
                }
            }
        }
    }

    private final ConcurrentMap<ChunkLocation, ChunkSlot> chunks = new ConcurrentHashMap<>();
    private final World world;
    private final AtomicReference<WorldBorder> border = new AtomicReference<>();
    private volatile boolean closed;

    private CachedMovecraftWorld(@NotNull World world) {
        this.world = world;
    }

    @NotNull
    @Override
    public Material getMaterial(@NotNull MovecraftLocation location) {
        MovecraftLocation sectionLocation = toSectionLocation(location);
        return getChunk(location).getType(sectionLocation);
    }

    @NotNull
    @Override
    public BlockData getData(@NotNull MovecraftLocation location) {
        MovecraftLocation sectionLocation = toSectionLocation(location);
        return getChunk(location).getData(sectionLocation);
    }

    @NotNull
    @Override
    public BlockState getState(@NotNull MovecraftLocation location) {
        MovecraftLocation sectionLocation = toSectionLocation(location);
        return getChunk(location).getState(sectionLocation);
    }

    @NotNull
    @Override
    public UUID getWorldUUID() {
        return world.getUID();
    }

    @NotNull
    @Override
    public String getName() {
        return world.getName();
    }

    @NotNull
    @Override
    public WorldBorder getWorldBorder() {
        WorldBorder cached = border.get();
        if (cached != null) {
            return cached;
        }
        return border.updateAndGet(existing -> existing != null
                ? existing
                : WorldManager.INSTANCE.executeMain(world::getWorldBorder));
    }

    @Override
    public int hashCode() {
        return world.getUID().hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CachedMovecraftWorld cached
                && cached.world.getUID().equals(world.getUID());
    }

    /**
     * Returns a current chunk accessor. Blocking workers use their existing
     * main-thread request queue; other threads wait for Paper's async load.
     */
    @NotNull
    private AsyncChunk<?> getChunk(@NotNull MovecraftLocation location) {
        ChunkLocation chunkLocation = ChunkLocation.from(location);

        while (!closed) {
            ChunkSlot slot = chunks.computeIfAbsent(chunkLocation, ignored -> new ChunkSlot());

            synchronized (slot) {
                if (slot.entry != null) {
                    slot.entry.touch();
                    return slot.entry.chunk;
                }
            }

            WorldManagerWorker worker = WorldManager.INSTANCE.getCurrentWorker();
            if (Bukkit.isPrimaryThread()) {
                long generation = generationOf(slot);
                try {
                    return createEntryOnMain(chunkLocation, slot, generation, null).chunk;
                } catch (CancellationException ignored) {
                    continue;
                }
            }

            if (worker != null && worker.isBlocking()) {
                long generation = generationOf(slot);
                try {
                    return WorldManager.INSTANCE.executeMain(
                            () -> createEntryOnMain(chunkLocation, slot, generation, null).chunk);
                } catch (CancellationException ignored) {
                    continue;
                }
            }

            try {
                ChunkEntry entry = startAsyncLoad(chunkLocation, slot).join();
                entry.touch();
                return entry.chunk;
            } catch (CancellationException exception) {
                if (closed) {
                    throw new IllegalStateException("World cache has been invalidated: " + world.getName(), exception);
                }
            } catch (CompletionException exception) {
                if (exception.getCause() instanceof CancellationException && !closed) {
                    continue;
                }
                throw exception;
            }
        }

        throw new IllegalStateException("World cache has been invalidated: " + world.getName());
    }

    private long generationOf(ChunkSlot slot) {
        synchronized (slot) {
            return slot.generation;
        }
    }

    private CompletableFuture<ChunkEntry> startAsyncLoad(ChunkLocation location, ChunkSlot slot) {
        final CompletableFuture<ChunkEntry> result;
        final long generation;

        synchronized (slot) {
            if (closed || chunks.get(location) != slot) {
                return failedFuture(new CancellationException("Chunk cache entry was invalidated."));
            }
            if (slot.entry != null) {
                slot.entry.touch();
                return CompletableFuture.completedFuture(slot.entry);
            }
            if (slot.pending != null) {
                return slot.pending;
            }

            result = new CompletableFuture<>();
            generation = slot.generation;
            slot.pending = result;
        }

        try {
            world.getChunkAtAsyncUrgently(location.x, location.z)
                    .thenCompose(loadedChunk -> WorldManager.INSTANCE.executeMainAsync(
                            () -> createEntryOnMain(location, slot, generation, result)))
                    .whenComplete((entry, error) -> {
                        synchronized (slot) {
                            if (slot.pending == result) {
                                slot.pending = null;
                            }
                        }
                        if (error != null) {
                            result.completeExceptionally(unwrap(error));
                        } else {
                            result.complete(entry);
                        }
                    });
        } catch (Throwable error) {
            synchronized (slot) {
                if (slot.pending == result) {
                    slot.pending = null;
                }
            }
            result.completeExceptionally(error);
        }

        return result;
    }

    /** Must only be called on the server thread. */
    private ChunkEntry createEntryOnMain(
            ChunkLocation location,
            ChunkSlot slot,
            long expectedGeneration,
            CompletableFuture<ChunkEntry> expectedPending
    ) {
        requireMainThread();

        synchronized (slot) {
            if (closed || chunks.get(location) != slot || slot.generation != expectedGeneration
                    || (expectedPending != null && slot.pending != expectedPending)) {
                throw new CancellationException("Chunk load completed after its cache entry was invalidated.");
            }

            if (slot.entry != null) {
                slot.entry.touch();
                return slot.entry;
            }

            Plugin plugin = WorldManager.INSTANCE.getPlugin();
            if (plugin == null || !plugin.isEnabled()) {
                throw new IllegalStateException("Windfarer must be enabled before chunk tickets can be acquired.");
            }

            boolean ticketRequested = false;
            try {
                // A plugin ticket loads the chunk if necessary and prevents it from unloading.
                world.addPluginChunkTicket(location.x, location.z, plugin);
                ticketRequested = true;
                slot.ticketHeld = true;

                Chunk bukkitChunk = world.getChunkAt(location.x, location.z);
                ChunkEntry entry = new ChunkEntry(AsyncChunk.of(bukkitChunk));
                slot.entry = entry;

                // A blocking access may win the race against an async load already in progress.
                if (expectedPending == null && slot.pending != null) {
                    CompletableFuture<ChunkEntry> pending = slot.pending;
                    slot.pending = null;
                    pending.complete(entry);
                }
                return entry;
            } catch (Throwable error) {
                if (ticketRequested) {
                    slot.ticketHeld = false;
                    try {
                        world.removePluginChunkTicket(location.x, location.z, plugin);
                    } catch (IllegalStateException ignored) {
                        // The world may already be shutting down.
                    }
                }
                if (error instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (error instanceof Error fatalError) {
                    throw fatalError;
                }
                throw new CompletionException(error);
            }
        }
    }

    private void invalidateChunk(ChunkLocation location) {
        ChunkSlot slot = chunks.remove(location);
        if (slot == null) {
            return;
        }

        synchronized (slot) {
            slot.generation++;
            slot.entry = null;
            if (slot.pending != null) {
                slot.pending.completeExceptionally(new CancellationException("Chunk unloaded while it was being loaded."));
                slot.pending = null;
            }
            removeTicket(location, slot);
        }
    }

    private void purgeExpired(long now) {
        for (var mapEntry : chunks.entrySet()) {
            ChunkLocation location = mapEntry.getKey();
            ChunkSlot slot = mapEntry.getValue();

            synchronized (slot) {
                if (slot.entry == null || slot.pending != null
                        || now - slot.entry.lastAccessNanos < CHUNK_IDLE_NANOS) {
                    continue;
                }

                slot.generation++;
                slot.entry = null;
                if (chunks.remove(location, slot)) {
                    removeTicket(location, slot);
                }
            }
        }
    }

    private void close() {
        closed = true;
        for (var mapEntry : chunks.entrySet()) {
            ChunkLocation location = mapEntry.getKey();
            ChunkSlot slot = mapEntry.getValue();
            synchronized (slot) {
                chunks.remove(location, slot);
                slot.generation++;
                slot.entry = null;
                if (slot.pending != null) {
                    slot.pending.completeExceptionally(new CancellationException("World cache was invalidated."));
                    slot.pending = null;
                }
                removeTicket(location, slot);
            }
        }
        chunks.clear();
    }

    private void removeTicket(ChunkLocation location, ChunkSlot slot) {
        if (!slot.ticketHeld) {
            return;
        }
        slot.ticketHeld = false;

        Plugin plugin = WorldManager.INSTANCE.getPlugin();
        if (plugin == null) {
            return;
        }
        try {
            world.removePluginChunkTicket(location.x, location.z, plugin);
        } catch (IllegalStateException ignored) {
            // The world may already be shutting down.
        }
    }

    private static MovecraftLocation toSectionLocation(@NotNull MovecraftLocation location) {
        return new MovecraftLocation(location.getX() & 0x0f, location.getY(), location.getZ() & 0x0f);
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Chunk ticket operations must run on the main thread.");
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private static final class ChunkSlot {
        private long generation;
        private boolean ticketHeld;
        private ChunkEntry entry;
        private CompletableFuture<ChunkEntry> pending;
    }

    private static final class ChunkEntry {
        private final AsyncChunk<?> chunk;
        private volatile long lastAccessNanos;

        private ChunkEntry(AsyncChunk<?> chunk) {
            this.chunk = chunk;
            touch();
        }

        private void touch() {
            lastAccessNanos = System.nanoTime();
        }
    }

    private static final class ChunkLocation {
        private final int x;
        private final int z;

        private ChunkLocation(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private static ChunkLocation from(MovecraftLocation location) {
            return new ChunkLocation(location.getX() >> 4, location.getZ() >> 4);
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ChunkLocation that)) {
                return false;
            }
            return x == that.x && z == that.z;
        }
    }
}
