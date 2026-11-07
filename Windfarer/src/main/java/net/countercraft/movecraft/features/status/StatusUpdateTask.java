package net.countercraft.movecraft.features.status;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.async.AbstractWorker;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.RequiredBlockEntry;
import net.countercraft.movecraft.craft.type.property.NamespacedKeyToDoubleProperty;
import net.countercraft.movecraft.features.status.events.CraftStatusUpdateEvent;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
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
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

// Credits for parallel Status go to @oh-noey
// TODO: Sometimes rarely the craft gets stuck in ... itself when turning and then it's hitbox gets corrupted which then leads to issues in the StatusUpdateTask
// https://github.com/APDevTeam/Movecraft/blob/oh-noey/parallel-status/Movecraft/src/main/java/net/countercraft/movecraft/features/status/StatusManager.java
public final class StatusUpdateTask implements Supplier<Effect> {
    private final Craft craft;
    private final NamespacedKeyToDoubleProperty fuelTypes;

    public StatusUpdateTask(@NotNull Craft craft) {
        this.craft = craft;

        fuelTypes = craft.getCraftProperties().get(PropertyKeys.FUEL_TYPES);
    }

    @Override
    public @NotNull Effect get() {
        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info(String.format("Starting status update task for craft <%s>...", craft.getUUID()));

        boolean started = false;
        try {
            // Stop the craft from attempting anything funny
            final long startTime = System.currentTimeMillis();
            while (!craft.setProcessingActiveIfAvailable()) {
                // Wait
                if (System.currentTimeMillis() - startTime > 5000) {
                    Movecraft.getInstance().getLogger().warning(String.format("Status task for craft <%s> timed out as craft was/is busy!", craft.getUUID()));
                    return Effect.NONE;
                }

                // The craft is busy, so let's give it some more time to process
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Effect.NONE;
                }
            }
            started = true;

            final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);
            ArrayList<CompletableFuture<StatusWorkerData>> workers = new ArrayList<>();
            final MovecraftWorld world = this.craft.getMovecraftWorld();
            final BitmapHitBox hitBox = new BitmapHitBox(craft.getHitBox());
            new HitBoxSlicer(hitBox)
                    .forEach(
                            slice ->
                                    workers.add(
                                            worker.submit(new StatusWorker(worker, world, slice))
                                            //ForkJoinTask.adapt(new StatusWorker(worker, world, slice))
                                    )
                    );

//            Optional<StatusWorkerData> workResult = ForkJoinTask
//                    .invokeAll(workers)
//                    .stream()
//                    .map(ForkJoinTask::join)
//                    .reduce(StatusWorkerData::add);
            Optional<StatusWorkerData> workResult =
                    workers.stream()
                            .map(CompletableFuture::join)
                            .reduce(StatusWorkerData::add);

            if (workResult.isEmpty()) {
                return Effect.NONE;
            }
            final Counter<NamespacedKey> materials = workResult.get().counter();
            final int nonNegligibleBlocks = workResult.get().nonNegligibleBlocks();
            final int nonNegligibleSolidBlocks = workResult.get().nonNegligibleSolidBlocks();

            Counter<RequiredBlockEntry> flyblocks = new Counter<>();
            Counter<RequiredBlockEntry> moveblocks = new Counter<>();

            // Pre-fill the moveblocks counter to avoid ignoring moveblocks
            for (RequiredBlockEntry entry : craft.getCraftProperties().get(PropertyKeys.MOVE_BLOCKS)) {
                moveblocks.add(entry, 0);
            }

            for (NamespacedKey material : materials.getKeySet()) {
                for (RequiredBlockEntry entry : craft.getCraftProperties().get(PropertyKeys.FLY_BLOCKS)) {
                    if (entry == null)
                        continue;
                    if (entry.contains(material)) {
                        flyblocks.add(entry, materials.get(material));
                    }
                }

                for (RequiredBlockEntry entry : craft.getCraftProperties().get(PropertyKeys.MOVE_BLOCKS)) {
                    // DONE: For whatever reason, this can be null?!
                    if (entry == null)
                        continue;
                    if (entry.contains(material)) {
                        moveblocks.add(entry, materials.get(material));
                    }
                }
            }

            craft.setDataTag(Craft.BLOCKS, materials);
            craft.setDataTag(Craft.FLYBLOCKS, flyblocks);
            craft.setDataTag(Craft.MOVEBLOCKS, moveblocks);
            craft.setDataTag(Craft.NON_NEGLIGIBLE_BLOCKS, nonNegligibleBlocks);
            craft.setDataTag(Craft.NON_NEGLIGIBLE_SOLID_BLOCKS, nonNegligibleSolidBlocks);
            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info(String.format("Finished status update task for craft <%s>! Time taken: %dms", craft.getUUID(), System.currentTimeMillis() - startTime));
            return () -> Bukkit.getPluginManager().callEvent(new CraftStatusUpdateEvent(craft));
        } finally {
            craft.setDataTag(StatusManager.STATUS_CHECK_ACTIVE, false);
            if (started) {
                craft.setDataTag(StatusManager.LAST_STATUS_CHECK, System.currentTimeMillis());
            }
            craft.setProcessing(false);
        }
    }

    // Credit: @ohnoey
    protected record StatusWorkerData(
            Counter<NamespacedKey> counter,
            int nonNegligibleBlocks,
            int nonNegligibleSolidBlocks
    ) {

        public StatusWorkerData add(StatusWorkerData other) {
            final Counter counter = new Counter(this.counter);
            counter.add(other.counter);
            return new StatusWorkerData(counter, this.nonNegligibleBlocks + other.nonNegligibleBlocks, this.nonNegligibleSolidBlocks + other.nonNegligibleSolidBlocks);
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

            for (MovecraftLocation l : this.slice) {
                BlockData data = this.world.getData(l);
                Material type = data.getMaterial();
                NamespacedKey namespacedKey = NamespacedIDUtil.getBlockID(data);
                materials.add(namespacedKey);

                if (type != Material.FIRE && !type.isAir()) {
                    nonNegligibleBlocks++;
                }
                if (type != Material.FIRE && !type.isAir() && !Tags.FLUID.contains(type)) {
                    nonNegligibleSolidBlocks++;
                }
            }
            return new StatusWorkerData(materials, nonNegligibleBlocks, nonNegligibleSolidBlocks);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (StatusWorker) obj;
            return
                    Objects.equals(this.world, that.world) &&
                    Objects.equals(this.slice, that.slice);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, slice);
        }

        @Override
        public String toString() {
            return "StatusWorker[" +
                    "world=" + world + ", " +
                    "slice=" + slice + ']';
        }

        }

}
