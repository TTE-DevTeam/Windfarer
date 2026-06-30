package net.countercraft.movecraft.features.status;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.RequiredBlockEntry;
import net.countercraft.movecraft.craft.type.property.NamespacedKeyToDoubleProperty;
import net.countercraft.movecraft.features.status.events.CraftStatusUpdateEvent;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.BlockCollectionUtil;
import net.countercraft.movecraft.util.Counter;
import net.countercraft.movecraft.util.NamespacedIDUtil;
import net.countercraft.movecraft.util.Tags;
import net.countercraft.movecraft.util.hitboxes.HitBoxSlicer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

// Credits for parallel Status go to @oh-noey
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
        final long startTime = System.currentTimeMillis();
        Movecraft.getInstance().getLogger().info(String.format("Starting status update task for craft <%s>...", craft.getUUID()));
        ArrayList<ForkJoinTask<StatusWorkerData>> workers = new ArrayList<>();
        final MovecraftWorld world = this.craft.getMovecraftWorld();
        new HitBoxSlicer(craft.getHitBox()).forEach(slice -> workers.add(ForkJoinTask.adapt(new StatusWorker(world, slice))));

        Optional<StatusWorkerData> workResult = ForkJoinTask
                .invokeAll(workers)
                .stream()
                .map(ForkJoinTask::join)
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
        craft.setDataTag(StatusManager.LAST_STATUS_CHECK, System.currentTimeMillis());
        Movecraft.getInstance().getLogger().info(String.format("Finished status update task for craft <%s>! Time taken: %dms", craft.getUUID(), System.currentTimeMillis() - startTime));
        return () -> Bukkit.getPluginManager().callEvent(new CraftStatusUpdateEvent(craft));
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

    protected record StatusWorker(
            @NotNull MovecraftWorld world,
            @NotNull Iterable<MovecraftLocation> slice
    ) implements Callable<StatusWorkerData> {

        @Override
        public StatusWorkerData call() throws Exception {
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
    }

}
