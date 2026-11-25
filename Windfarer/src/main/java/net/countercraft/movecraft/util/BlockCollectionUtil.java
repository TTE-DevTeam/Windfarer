package net.countercraft.movecraft.util;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.async.AbstractWorker;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.WorldManagerExecutors;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
import net.countercraft.movecraft.processing.functions.TriadicPredicate;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.util.functions.TriConsumer;
import net.countercraft.movecraft.util.hitboxes.BitmapHitBox;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.countercraft.movecraft.util.hitboxes.HitBoxSlicer;
import net.countercraft.movecraft.util.hitboxes.SynchronizedBitmapHitBox;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class BlockCollectionUtil {

    private BlockCollectionUtil() {
    }

    /**
     * Asynchronously collects all positions in the craft's hitbox which satisfy
     * {@code checkPredicate}. The returned future completes after all slices have
     * finished. It does not block the calling WorldManager worker.
     *
     * <p>The caller is responsible for tracking the returned future with
     * {@link WorldManagerWorker#trackAsyncWork(CompletableFuture)} when it is part
     * of a WorldManager task whose lifetime must extend beyond {@code get()}.</p>
     */
    @NotNull
    public static CompletableFuture<Set<MovecraftLocation>> getLocationsAsync(
            @NotNull Craft craft,
            @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate
    ) {
        return getLocationsAsync(craft, craft.getHitBox(), craft.getMovecraftWorld(), checkPredicate);
    }

    @NotNull
    public static CompletableFuture<Set<MovecraftLocation>> getLocationsAsync(
            @Nullable Craft craft,
            @Nullable HitBox hitBox,
            @NotNull MovecraftWorld movecraftWorld,
            @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate
    ) {
        return getLocationsAsync(craft, hitBox, movecraftWorld, checkPredicate, (location, world, owner) -> { });
    }

    @NotNull
    public static CompletableFuture<Set<MovecraftLocation>> getLocationsAsync(
            @NotNull Craft craft,
            @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate,
            @NotNull TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer
    ) {
        return getLocationsAsync(craft, craft.getHitBox(), craft.getMovecraftWorld(), checkPredicate, consumer);
    }

    @NotNull
    public static CompletableFuture<Set<MovecraftLocation>> getLocationsAsync(
            @Nullable Craft craft,
            @Nullable HitBox hitBox,
            @NotNull MovecraftWorld movecraftWorld,
            @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate,
            @NotNull TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer
    ) {
        if (hitBox == null) {
            return CompletableFuture.<Set<MovecraftLocation>>completedFuture(new HashSet<>());
        }

        final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);

        // Snapshot and slice away from the WorldManager pool. SynchronizedBitmapHitBox
        // can provide an atomic snapshot; other HitBox implementations must not be
        // mutated while this asynchronous snapshot is being taken.
        return CompletableFuture
                .supplyAsync(() -> snapshot(hitBox), WorldManagerExecutors.WORK_STEALING_POOL)
                .thenCompose(snapshot -> {
                    if (snapshot.isEmpty()) {
                        return CompletableFuture.<Set<MovecraftLocation>>completedFuture(new HashSet<>());
                    }

                    final List<Iterable<MovecraftLocation>> slices = new ArrayList<>();
                    new HitBoxSlicer(snapshot).forEach(slices::add);
                    if (slices.isEmpty()) {
                        return CompletableFuture.<Set<MovecraftLocation>>completedFuture(new HashSet<>());
                    }

                    final List<CompletableFuture<Set<MovecraftLocation>>> sliceTasks = new ArrayList<>(slices.size());
                    for (Iterable<MovecraftLocation> slice : slices) {
                        sliceTasks.add(CompletableFuture.supplyAsync(() -> {
                            try {
                                return new Worker(worker, craft, movecraftWorld, slice, checkPredicate, consumer).call();
                            } catch (Exception exception) {
                                throw new CompletionException(exception);
                            }
                        }, WorldManagerExecutors.WORK_STEALING_POOL));
                    }

                    return CompletableFuture
                            .allOf(sliceTasks.toArray(CompletableFuture<?>[]::new))
                            .thenApply(ignored -> {
                                Set<MovecraftLocation> result = new HashSet<>();
                                for (CompletableFuture<Set<MovecraftLocation>> sliceTask : sliceTasks) {
                                    result.addAll(sliceTask.getNow(Set.of()));
                                }
                                return result;
                            });
                });
    }

    /**
     * Legacy synchronous API. Calls made from a WorldManager task run sequentially
     * on that already-asynchronous worker instead of submitting child tasks to the
     * same bounded pool and then joining them. External callers retain the previous
     * off-thread behaviour, but should migrate to {@link #getLocationsAsync}.
     */
    @Deprecated
    @Nullable
    public static Set<MovecraftLocation> getLocations(
            final Craft craft,
            final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate
    ) {
        return getLocations(craft, craft.getHitBox(), craft.getMovecraftWorld(), checkPredicate);
    }

    @Deprecated
    @Nullable
    public static Set<MovecraftLocation> getLocations(
            @Nullable Craft craft,
            final HitBox hitBox,
            final MovecraftWorld movecraftWorld,
            final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate
    ) {
        return getLocations(craft, hitBox, movecraftWorld, checkPredicate, (location, world, owner) -> { });
    }

    @Deprecated
    @Nullable
    public static Set<MovecraftLocation> getLocations(
            final Craft craft,
            final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate,
            TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer
    ) {
        return getLocations(craft, craft.getHitBox(), craft.getMovecraftWorld(), checkPredicate, consumer);
    }

    @Deprecated
    @Nullable
    public static Set<MovecraftLocation> getLocations(
            @Nullable Craft craft,
            final HitBox hitBox,
            final MovecraftWorld movecraftWorld,
            final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate,
            TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer
    ) {
        if (hitBox == null) {
            return new HashSet<>();
        }

        final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);
        if (net.countercraft.movecraft.processing.WorldManager.INSTANCE.getCurrentWorker() == null) {
            // No WorldManager worker is being occupied by this caller, so retain the
            // old off-thread behaviour while the API is being migrated.
            return getLocationsAsync(craft, hitBox, movecraftWorld, checkPredicate, consumer).join();
        }

        // Do not recursively submit work to the same WorldManager pool from one of
        // its own worker threads. This compatibility path trades some per-craft
        // parallelism for guaranteed progress until callers use the async API.
        BitmapHitBox snapshot = snapshot(hitBox);
        Set<MovecraftLocation> result = new HashSet<>();
        for (Iterable<MovecraftLocation> slice : new HitBoxSlicer(snapshot)) {
            result.addAll(new Worker(worker, craft, movecraftWorld, slice, checkPredicate, consumer).callInternally());
        }
        return result;
    }

    private static BitmapHitBox snapshot(HitBox hitBox) {
        if (hitBox instanceof SynchronizedBitmapHitBox synchronizedHitBox) {
            return synchronizedHitBox.snapshot();
        }
        return new BitmapHitBox(hitBox);
    }

    private static final class Worker extends AbstractWorker<Set<MovecraftLocation>> {
        private final @Nullable Craft craft;
        private final @NotNull MovecraftWorld world;
        private final @NotNull Iterable<MovecraftLocation> slice;
        private final @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> validationRule;
        private final @NotNull TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer;

        private Worker(
                @NotNull WorldManagerWorker worker,
                @Nullable Craft craft,
                @NotNull MovecraftWorld world,
                @NotNull Iterable<MovecraftLocation> slice,
                @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> validationRule,
                @NotNull TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer
        ) {
            super(worker);
            this.craft = craft;
            this.world = world;
            this.slice = slice;
            this.validationRule = validationRule;
            this.consumer = consumer;
        }

        @Override
        public Set<MovecraftLocation> callInternally() {
            Set<MovecraftLocation> locations = new HashSet<>();
            for (MovecraftLocation location : this.slice) {
                if (validationRule.validate(location, world, craft).isSucess()) {
                    locations.add(location);
                    consumer.accept(location, world, craft);
                }
            }
            return locations;
        }
    }
}
