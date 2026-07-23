package net.countercraft.movecraft.util;

import com.google.common.collect.Sets;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.functions.TriadicPredicate;
import net.countercraft.movecraft.util.hitboxes.HitBoxSlicer;
import org.apache.logging.log4j.util.TriConsumer;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;

public class BlockCollectionUtil {

    /*
     * Returns a set of locations in the craft's hitbox that fulfill checkPredicate and that is calculted off-thread
     */
    @Nullable
    public static Set<MovecraftLocation> getLocations(final Craft craft, final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate) {
        return getLocations(craft, checkPredicate, (l, w, c) -> {});
    }
    @Nullable
    public static Set<MovecraftLocation> getLocations(final Craft craft, final TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> checkPredicate, TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer) {
        if (craft.getHitBox() == null) {
            return new HashSet<>();
        }
        final HitBox hitbox = new BitmapHitBox(craft.getHitBox());
        if (hitbox.isEmpty() {
            return new HashSet<>();
        }
        ArrayList<ForkJoinTask<WorkerData>> workers = new ArrayList<>();
        new HitBoxSlicer(hitbox).forEach(slice -> workers.add(ForkJoinTask.adapt(new Worker(craft, slice, checkPredicate, consumer, Sets.newConcurrentHashSet()))));

        Optional<WorkerData> workResult = ForkJoinTask
                .invokeAll(workers)
                .stream()
                .map(ForkJoinTask::join)
                .reduce(WorkerData::add);

        if(workResult.isEmpty()){
            return null;
        } else {
            return workResult.get().locations();
        }
    }

    private record WorkerData(
            Set<MovecraftLocation> locations
    ){

        public WorkerData add(WorkerData other) {
            final Set<MovecraftLocation> newSet = Collections.synchronizedSet(new HashSet<>(other.locations()));
            newSet.addAll(this.locations());
            return new WorkerData(
                    newSet
            );
        }
    }

    // Goes over a single slice and checks for air
    private record Worker(
            @NotNull Craft craft,
            @NotNull Iterable<MovecraftLocation> slice,
            @NotNull TriadicPredicate<MovecraftLocation, MovecraftWorld, Craft> validationRule,
            @NotNull TriConsumer<MovecraftLocation, MovecraftWorld, Craft> consumer,
            @NotNull Set<MovecraftLocation> locations
    ) implements Callable<WorkerData> {

        @Override
        public WorkerData call() {
            Set<MovecraftLocation> locations = Collections.synchronizedSet(new HashSet<>());
            final MovecraftWorld world = this.craft.getMovecraftWorld();

            for (MovecraftLocation l : this.slice) {
                if (validationRule().validate(l, world, this.craft).isSucess()) {
                    locations.add(l);
                    consumer().accept(l, world, this.craft);
                }
            }

            return new WorkerData(locations);
        }
    }

}
