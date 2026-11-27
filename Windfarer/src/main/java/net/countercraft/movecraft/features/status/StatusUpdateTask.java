package net.countercraft.movecraft.features.status;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.async.AbstractWorker;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.state.CraftState;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.RequiredBlockEntry;
import net.countercraft.movecraft.features.status.events.CraftStatusUpdateEvent;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
import net.countercraft.movecraft.processing.WorldManagerExecutors;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.util.Counter;
import net.countercraft.movecraft.util.NamespacedIDUtil;
import net.countercraft.movecraft.util.Tags;
import net.countercraft.movecraft.util.hitboxes.BitmapHitBox;
import net.countercraft.movecraft.util.hitboxes.HitBoxSlicer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;

// Credits for parallel Status go to @oh-noey
public final class StatusUpdateTask implements Supplier<Effect> {
    private final Craft craft;

    public StatusUpdateTask(@NotNull Craft craft) {
        this.craft = craft;
    }

    @Override
    public @NotNull Effect get() {
        if (Settings.Debug) {
            Movecraft.getInstance().getLogger().info(
                    String.format("Starting status update task for craft <%s>...", craft.getUUID())
            );
        }

        // StatusManager sets this before queueing. CraftManager.add() also schedules an
        // immediate status update, so set it here as well for that entry point.
        craft.setDataTag(StatusManager.STATUS_CHECK_ACTIVE, true);

        final long startTime = System.currentTimeMillis();
        final AtomicReference<CraftStateHandle> stateHandle = new AtomicReference<>();
        final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(
                WorldManagerWorkers.LOW_PRIORITY
        );

        try {
            // Both the state reservation and the expensive work are asynchronous. In particular,
            // a busy craft no longer makes a NORMAL_PRIORITY thread sleep/retry for up to 5s.
            CompletableFuture<Void> completion = reserveStateAsync(stateHandle)
                    .thenCompose(acquired -> {
                        if (!acquired) {
                            if (CraftManager.getInstance().isStillAlive(craft)) {
                                Movecraft.getInstance().getLogger().warning(
                                        String.format("Status task for craft <%s> timed out as craft was/is busy!", craft.getUUID())
                                );
                            }
                            return CompletableFuture.completedFuture(Effect.NONE);
                        }
                        if (!CraftManager.getInstance().isStillAlive(craft)) {
                            return CompletableFuture.completedFuture(Effect.NONE);
                        }

                        final MovecraftWorld world = craft.getMovecraftWorld();
                        return calculateStatusAsync(world, worker)
                                .thenApply(result -> result
                                        .map(this::applyStatusData)
                                        .orElse(Effect.NONE)
                                );
                    })
                    .handle((effect, throwable) -> {
                        try {
                            if (throwable != null) {
                                Movecraft.getInstance().getLogger().log(
                                        Level.SEVERE,
                                        "Failed to calculate status for craft " + craft.getUUID(),
                                        unwrap(throwable)
                                );
                            } else if (effect != null && effect != Effect.NONE) {
                                // Status events are Bukkit events and must remain on the main thread.
                                worker.submitEffectToWorldChanges(effect);
                            }
                        } catch (Throwable completionError) {
                            Movecraft.getInstance().getLogger().log(
                                    Level.SEVERE,
                                    "Failed to finalize status update for craft " + craft.getUUID(),
                                    completionError
                            );
                        } finally {
                            finishStatusCheck(stateHandle);

                            if (Settings.Debug) {
                                Movecraft.getInstance().getLogger().info(
                                        String.format(
                                                "Finished status update task for craft <%s>! Time taken: %dms",
                                                craft.getUUID(),
                                                System.currentTimeMillis() - startTime
                                        )
                                );
                            }
                        }
                        return null;
                    });

            // Keep WorldManager alive/aware of this operation after get() returns.
            // The completion stage above posts its main-thread callback before completing.
            worker.trackAsyncWork(completion);
            return Effect.NONE;
        } catch (RuntimeException | Error throwable) {
            finishStatusCheck(stateHandle);
            throw throwable;
        }
    }

    /**
     * Reserves {@link CraftState#STATUS_UPDATE} on the craft, waiting up to 5s without blocking a thread.
     * The handle is stored in {@code handleOut} as soon as it is granted, so it is always released in {@link #finishStatusCheck}.
     *
     * @return a future of whether the state could be set
     */
    private CompletableFuture<Boolean> reserveStateAsync(AtomicReference<CraftStateHandle> handleOut) {
        if (!CraftManager.getInstance().isStillAlive(craft)) {
            return CompletableFuture.completedFuture(false);
        }
        // handleAsync: continue on the work stealing pool, never on the thread which released the blocking state (e.g. main thread)
        return craft.getStateTracker()
                .reserveStateAndWait(CraftState.STATUS_UPDATE, 5, TimeUnit.SECONDS)
                .handleAsync((handle, throwable) -> {
                    if (throwable != null) {
                        if (unwrap(throwable) instanceof TimeoutException) {
                            return false;
                        }
                        throw new CompletionException(throwable);
                    }
                    handleOut.set(handle);
                    return true;
                }, WorldManagerExecutors.WORK_STEALING_POOL);
    }

    private CompletableFuture<Optional<StatusWorkerData>> calculateStatusAsync(
            @NotNull MovecraftWorld world,
            @NotNull WorldManagerWorker worker
    ) {
        // Slicing and hitbox snapshotting can be substantial for large crafts. Do it on
        // the work-stealing pool too, rather than keeping the normal-priority parent thread.
        return CompletableFuture
                .supplyAsync(() -> {
                    BitmapHitBox hitBoxSnapshot = new BitmapHitBox(craft.getHitBox());
                    List<Iterable<MovecraftLocation>> slices = new ArrayList<>();
                    new HitBoxSlicer(hitBoxSnapshot).forEach(slices::add);
                    return slices;
                }, WorldManagerExecutors.WORK_STEALING_POOL)
                .thenCompose(slices -> {
                    if (slices.isEmpty()) {
                        return CompletableFuture.completedFuture(Optional.empty());
                    }

                    List<CompletableFuture<StatusWorkerData>> sliceTasks = new ArrayList<>(slices.size());
                    for (Iterable<MovecraftLocation> slice : slices) {
                        sliceTasks.add(CompletableFuture.supplyAsync(() -> {
                            try {
                                return new StatusWorker(worker, world, slice).call();
                            } catch (Exception exception) {
                                throw new CompletionException(exception);
                            }
                        }, WorldManagerExecutors.WORK_STEALING_POOL));
                    }

                    // allOf is a completion barrier, not a blocking wait. thenApply runs
                    // once every slice has finished; no WorldManager thread calls join().
                    return CompletableFuture
                            .allOf(sliceTasks.toArray(CompletableFuture<?>[]::new))
                            .thenApply(ignored -> {
                                StatusWorkerData combined = null;
                                for (CompletableFuture<StatusWorkerData> sliceTask : sliceTasks) {
                                    StatusWorkerData next = sliceTask.getNow(null);
                                    if (next != null) {
                                        combined = combined == null ? next : combined.add(next);
                                    }
                                }
                                return Optional.ofNullable(combined);
                            });
                });
    }

    public static void fillMoveBlockAndFlyBlockDatatags(final Counter<NamespacedKey> blockCounter, final int nonNegligibleBlocks, final int nonNegligibleSolidBlocks, final Craft craft) {
        Counter<RequiredBlockEntry> flyblocks = new Counter<>();
        Counter<RequiredBlockEntry> moveblocks = new Counter<>();

        // Pre-fill the moveblocks counter to avoid ignoring moveblocks.
        for (RequiredBlockEntry entry : craft.getCraftProperties().get(PropertyKeys.MOVE_BLOCKS)) {
            moveblocks.add(entry, 0);
        }

        for (NamespacedKey material : blockCounter.getKeySet()) {
            for (RequiredBlockEntry entry : craft.getCraftProperties().get(PropertyKeys.FLY_BLOCKS)) {
                if (entry == null) {
                    continue;
                }
                if (entry.contains(material)) {
                    flyblocks.add(entry, blockCounter.get(material));
                }
            }

            for (RequiredBlockEntry entry : craft.getCraftProperties().get(PropertyKeys.MOVE_BLOCKS)) {
                if (entry == null) {
                    continue;
                }
                if (entry.contains(material)) {
                    moveblocks.add(entry, blockCounter.get(material));
                }
            }
        }

        craft.setDataTag(Craft.BLOCKS, blockCounter);
        craft.setDataTag(Craft.FLYBLOCKS, flyblocks);
        craft.setDataTag(Craft.MOVEBLOCKS, moveblocks);
        craft.setDataTag(Craft.NON_NEGLIGIBLE_BLOCKS, nonNegligibleBlocks);
        craft.setDataTag(Craft.NON_NEGLIGIBLE_SOLID_BLOCKS, nonNegligibleSolidBlocks);
    }

    private Effect applyStatusData(@NotNull StatusWorkerData result) {
        fillMoveBlockAndFlyBlockDatatags(result.counter(), result.nonNegligibleBlocks(), result.nonNegligibleSolidBlocks(), craft);

        return () -> Bukkit.getPluginManager().callEvent(new CraftStatusUpdateEvent(craft));
    }

    private void finishStatusCheck(AtomicReference<CraftStateHandle> stateHandle) {
        craft.setDataTag(StatusManager.STATUS_CHECK_ACTIVE, false);
        CraftStateHandle handle = stateHandle.getAndSet(null);
        if (handle != null) {
            craft.setDataTag(StatusManager.LAST_STATUS_CHECK, System.currentTimeMillis());
            handle.release();
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static boolean isNonNegligibleBlock(final Material material) {
        return material != Material.FIRE && !material.isAir();
    }

    protected record StatusWorkerData(
            Counter<NamespacedKey> counter,
            int nonNegligibleBlocks,
            int nonNegligibleSolidBlocks
    ) {
        public StatusWorkerData add(StatusWorkerData other) {
            Counter<NamespacedKey> combined = new Counter<>(this.counter);
            combined.add(other.counter);
            return new StatusWorkerData(
                    combined,
                    this.nonNegligibleBlocks + other.nonNegligibleBlocks,
                    this.nonNegligibleSolidBlocks + other.nonNegligibleSolidBlocks
            );
        }
    }

    protected static final class StatusWorker extends AbstractWorker<StatusWorkerData> {
        private final @NotNull MovecraftWorld world;
        private final @NotNull Iterable<MovecraftLocation> slice;

        protected StatusWorker(
                @NotNull WorldManagerWorker worker,
                @NotNull MovecraftWorld world,
                @NotNull Iterable<MovecraftLocation> slice
        ) {
            super(worker);
            this.world = world;
            this.slice = slice;
        }

        @Override
        public StatusWorkerData callInternally() {
            final Counter<NamespacedKey> materials = new Counter<>();
            int nonNegligibleBlocks = 0;
            int nonNegligibleSolidBlocks = 0;

            for (MovecraftLocation location : this.slice) {
                BlockData data = this.world.getData(location);
                Material type = data.getMaterial();
                NamespacedKey namespacedKey = NamespacedIDUtil.getBlockID(data);
                materials.add(namespacedKey);

                if (isNonNegligibleBlock(type)) {
                    nonNegligibleBlocks++;
                    if (!Tags.FLUID.contains(type)) {
                        nonNegligibleSolidBlocks++;
                    }
                }
            }
            return new StatusWorkerData(materials, nonNegligibleBlocks, nonNegligibleSolidBlocks);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (StatusWorker) obj;
            return Objects.equals(this.world, that.world) && Objects.equals(this.slice, that.slice);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, slice);
        }

        @Override
        public String toString() {
            return "StatusWorker[" +
                    "world=" + world + ", " +
                    "slice=" + slice +
                    ']';
        }
    }
}
