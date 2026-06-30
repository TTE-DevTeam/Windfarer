package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.features.fuel.CraftFurnaceUtil;
import net.countercraft.movecraft.features.fuel.FuelUtil;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Supplier;

import static net.countercraft.movecraft.features.fuel.FuelDataTags.CURRENT_FUEL_ITEM;
import static net.countercraft.movecraft.features.fuel.FuelDataTags.FUEL_PERCENTAGE;

public class UpdateFuelBurnersTask implements Supplier<Effect> {

    private final Craft craft;
    private final boolean burnersActive;

    public UpdateFuelBurnersTask(Craft craft, boolean burnersActive) {
        this.craft = craft;
        this.burnersActive = burnersActive;
    }

    @Override
    public Effect get() {
        final long startTime = System.currentTimeMillis();
        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info(String.format("Starting fuel burner update task for craft <%s>...", craft.getUUID()));
        // TODO: Add API for additional fuel sources and how full they are
        // Step 0): Determine if we can modify furnaces or not
        // Step 1): Collect the data about every furnace on our craft that still exists, use the tracked locations for that
        // Step 2): For every fuel source, determine how full it is
        // Step 3): Special case furnaces: set the lit and progress state of the furnace => Do this in a effect and collec tit
        // Step 4): Calculate, how full the craft's total fuel supply is and apply it onto the craft

        final boolean furnaceNMSAvailable = Movecraft.getInstance().getNMSHelper() != null;

        final Set<TrackedLocation> furnaceLocations = FuelUtil.getFuelBurners(craft);
        if (furnaceLocations.size() > 0) {
            ArrayList<ForkJoinTask<BurnerWorkerData>> workers = new ArrayList<>();
            final Queue<MovecraftLocation> burnersToUpdate = new ConcurrentLinkedQueue<>();
            furnaceLocations.forEach(trackedLocation -> workers.add(ForkJoinTask.adapt(new BurnerWorker(craft.getMovecraftWorld(), trackedLocation, craft, burnersToUpdate))));
            Optional<BurnerWorkerData> workResult = ForkJoinTask
                    .invokeAll(workers)
                    .stream()
                    .map(ForkJoinTask::join)
                    .reduce(BurnerWorkerData::add);

            if (workResult.isPresent()) {
                BurnerWorkerData burnerWorkerData = workResult.get();
                // TODO: If we lose furnaces, we no longer count them here, do we want that?
                // TODO: THis is going to be temporary only anyway...
                craft.setDataTag(FUEL_PERCENTAGE, burnerWorkerData.cumulativeFuelLevel() / burnerWorkerData.validBurnerCount());
            }
        }

        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info(String.format("Finished fuel burner update task for craft <%s>! Time taken: %dms", craft.getUUID(), System.currentTimeMillis() - startTime));
        if (furnaceNMSAvailable) {
            return makeBurnerProgressEffect(furnaceLocations);
        } else {
            return null;
        }
    }

    protected Effect makeBurnerProgressEffect(Set<TrackedLocation> furnaceLocations) {
        // NO burners? Nothing to do!
        if (furnaceLocations.isEmpty()) {
            return null;
        }
        final double burnPercentage = craft.getBurningFuel() / craft.getMaxBurningFuel();
        final ItemStack fuelItem = craft.getDataTag(CURRENT_FUEL_ITEM);
        int burnTime = 0;
        int totalBurnTime = 0;
        final World worldWorkaround = Bukkit.getWorld(craft.getMovecraftWorld().getWorldUUID());
        if (fuelItem != null && !fuelItem.isEmpty() && Movecraft.getInstance().getNMSHelper().isFuel(fuelItem, worldWorkaround)) {
            totalBurnTime = (Movecraft.getInstance().getNMSHelper().getBurnDuration(fuelItem, worldWorkaround));
            double burnDuration = totalBurnTime;
            burnTime = (int) (burnDuration * burnPercentage);
        }

        List<Effect> effects = new ArrayList<>(furnaceLocations.size());
        for (TrackedLocation trackedLocation : furnaceLocations) {
            Effect newEffect = new UpdateBurnerTimesEffect(this.craft, trackedLocation, burnTime, totalBurnTime, this.burnersActive);
            effects.add(newEffect);
        }
        return new Effect.AndEffect(effects);
    }

    record UpdateBurnerTimesEffect(
            Craft craft,
            TrackedLocation burner,
            int burnTime,
            int totalBurnTime,
            boolean isActive
    ) implements Effect {

        @Override
        public void run() {
            final Location location = burner.getAbsoluteLocation().toBukkit(craft.getWorld());
            final BlockData furnace = craft.getWorld().getBlockData(location);
            final BlockState state = craft.getWorld().getBlockState(location);

            if (state instanceof org.bukkit.block.Furnace furnace1) {
                if (isActive) {
                    Movecraft.getInstance().getNMSHelper().setFurnaceBurnTime(burnTime, totalBurnTime + 1, furnace1);
                } else {
                    Movecraft.getInstance().getNMSHelper().setFurnaceBurnTime(0, 0, furnace1);
                }
            }
            if (furnace instanceof Furnace furnaceState) {
                furnaceState.setLit(isActive);
                state.setBlockData(furnaceState);
                state.update();
            }
        }
    }

    record BurnerWorkerData(
            double cumulativeFuelLevel,
            int validBurnerCount
    ) {
        public BurnerWorkerData add(BurnerWorkerData other) {
            return new BurnerWorkerData(this.cumulativeFuelLevel + other.cumulativeFuelLevel, this.validBurnerCount + other.validBurnerCount);
        }
    }

    record BurnerWorker(
            MovecraftWorld world,
            TrackedLocation burnerLocation,
            Craft craft,
            Queue<MovecraftLocation> burnersToUpdate
    ) implements Callable<BurnerWorkerData> {

        @Override
        public BurnerWorkerData call() throws Exception {
            final MovecraftLocation location = this.burnerLocation.getAbsoluteLocation();
            final BlockState state = world.getState(location);

            double fuelLevel = 0.0D;
            int burnerCount = 0;
            if (Tags.FURNACES.contains(world.getMaterial(location))) {
                if (state instanceof InventoryHolder inventoryHolder) {
                    if (inventoryHolder.getInventory() instanceof FurnaceInventory burnerInventory) {
                        burnerCount = 1;
                        fuelLevel += CraftFurnaceUtil.getFurnaceFuelLevel(burnerInventory, craft.getCraftProperties());

                        // Special logic for furnace visualization
                        if (craft.getCraftProperties().get(PropertyKeys.FURNACE_FUEL_VISUALIZATION) ) {
                            burnersToUpdate.add(location);
                        }
                    }
                }
            }

            return new BurnerWorkerData(fuelLevel, burnerCount);
        }
    }
}
