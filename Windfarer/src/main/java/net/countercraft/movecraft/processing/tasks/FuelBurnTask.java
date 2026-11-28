package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.async.AbstractWorker;
import net.countercraft.movecraft.async.FuelBurnRunnable;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.state.CraftState;
import net.countercraft.movecraft.craft.state.CraftStateHandle;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.property.NamespacedKeyToDoubleProperty;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.events.FuelBurnEvent;
import net.countercraft.movecraft.features.fuel.FuelDataTags;
import net.countercraft.movecraft.features.fuel.FuelUtil;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.WorldManagerExecutors;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.logging.Level;

import static net.countercraft.movecraft.features.fuel.FuelDataTags.IS_FUELED;

public class FuelBurnTask extends UpdateFuelBurnersTask implements Supplier<Effect> {

    private final double fuelBurnRate;

    public FuelBurnTask(Craft craft, double fuelBurnRate) {
        super(craft, false);
        this.fuelBurnRate = fuelBurnRate;
    }

    @Override
    public Effect get() {
        final long startTime = System.currentTimeMillis();
        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info(String.format("Starting fuel burn task for craft <%s>...", craft.getUUID()));
        // TODO: Add API for additional fuel sources and how to use them
        final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);

        try {
            if (!CraftManager.getInstance().isStillAlive(this.craft)) {
                FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
                return Effect.NONE;
            }

            // The craft must not move while we do something. Wait up to 5s for it without blocking a thread.
            // The calculation itself is a regular task of the WorldManager again, so it runs on a worker like all others.
            final CompletableFuture<Void> reservation = craft.getStateTracker()
                    .reserveStateAndWait(CraftState.FUEL_BURN, 5, TimeUnit.SECONDS)
                    .handle((handle, throwable) -> {
                        if (throwable != null) {
                            FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
                            if (unwrap(throwable) instanceof TimeoutException) {
                                if (CraftManager.getInstance().isStillAlive(this.craft)) {
                                    Movecraft.getInstance().getLogger().warning(String.format("Fuel-burn task for craft <%s> timed out as craft was/is busy!", craft.getUUID()));
                                }
                            } else {
                                Movecraft.getInstance().getLogger().log(Level.SEVERE, "Failed to reserve state for fuel burn of craft " + craft.getUUID(), unwrap(throwable));
                            }
                            return null;
                        }

                        try {
                            if (!CraftManager.getInstance().isStillAlive(this.craft)) {
                                handle.release();
                                FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
                                return null;
                            }
                            // The task takes over the ownership of the state
                            WorldManager.INSTANCE.submit(new Calculation(handle, startTime), WorldManagerWorkers.NORMAL_PRIORITY);
                        } catch (Throwable submitError) {
                            handle.release();
                            FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
                            Movecraft.getInstance().getLogger().log(Level.SEVERE, "Failed to start fuel burn calculation of craft " + craft.getUUID(), submitError);
                        }
                        return null;
                    });

            // Keep WorldManager alive/aware of this operation after get() returns. The follow-up task is
            // submitted before this completes.
            worker.trackAsyncWork(reservation);
            return Effect.NONE;
        } catch (RuntimeException | Error throwable) {
            FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
            throw throwable;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * The calculation as a task of its own, started as soon as the craft state could be set.
     * Owns the state until the effect it returns has run.
     */
    private final class Calculation implements Supplier<Effect> {
        private final CraftStateHandle stateHandle;
        private final long startTime;

        private Calculation(CraftStateHandle stateHandle, long startTime) {
            this.stateHandle = stateHandle;
            this.startTime = startTime;
        }

        @Override
        public Effect get() {
            // calculate() releases the state and the in progress mark itself if it fails
            return calculate(this.stateHandle, this.startTime);
        }
    }

    /**
     * Searches and prepares the fuel to burn. Owns the given state until the returned effect has run (the effect
     * releases it), or until this method fails.
     */
    private Effect calculate(final CraftStateHandle stateHandle, final long startTime) {
        boolean completionOwnsState = false;
        final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);
        try {

            boolean hasFuel = false;
            double fuelBurnRate = this.fuelBurnRate;
            List<Effect> additionalSteps = new ArrayList<>(2);

            // If the specified rate is 0 and we are here, then we burn fuel, but not currently!
            // TODO: Sure? This will break fuel consumption!
            // TODO: Properly treat crafts without passive fuel burning! Those need to calculate their values!
            // We also pass in a burn rate of 0, if we are not moving for example
            if (fuelBurnRate <= 0.0D) {
                craft.setBurningFuel(0.0);
            }
            // Fuel item burning
            // We currently have somethign that we are burning
            else if (craft.getBurningFuel() >= fuelBurnRate) {
                hasFuel = true;
                additionalSteps.add(new ReduceBurningFuelEffect(craft, fuelBurnRate));
            }
            // Find a new fuel item to burn, save to tag, remove item from furnace, throw event
            // We burnt the item we had, if we had any. Search for something new to burn
            else {
                Effect consumeFuelEffect = null;
                Effect fuelCraftEffect = null;
                // TODO: Refactor into a expandable list of callable functions
                // Step 0): Initialize variables for burn process
                ItemStack fuelItem = null;
                double burnTime = 0;
                // After that, find our source for a new fuel item
                // Step 1): Check for fuel in the burners
                Set<TrackedLocation> furnaces = FuelUtil.getFuelBurners(this.craft);
                if (!furnaces.isEmpty()) {
                    // Access furnace inventories and determine the next active burner
                    // Also collect the burners in a list to update them later on
//                ArrayList<ForkJoinTask<Void>> workers = new ArrayList<>();
                    final NamespacedKeyToDoubleProperty fuelTypes = this.craft.getCraftProperties().get(PropertyKeys.FUEL_TYPES);
                    final double finalBurnRate = fuelBurnRate;
                    final AtomicBoolean validatedFound = new AtomicBoolean(false);
                    final AtomicReference<WorkerData> resultReference = new AtomicReference<>(null);
//                furnaces.forEach(location -> workers.add(ForkJoinTask.adapt(new Worker(worker, craft, location, fuelTypes, finalBurnRate, validatedFound, resultReference))));

                    // Invokes all, the workers check towards the AtomicBoolean if there still is something to do
//                ForkJoinTask.invokeAll(workers);
                    for (TrackedLocation location : furnaces) {
                        Worker workerTmp = new Worker(worker, craft, location, fuelTypes, finalBurnRate, validatedFound, resultReference);
                        try {
                            workerTmp.call();
                        } catch (Exception e) {
                            e.printStackTrace();
                            continue;
                        }
                    }


                    Optional<WorkerData> result = Optional.ofNullable(resultReference.get());
                    if(!result.isEmpty()){
                        fuelItem = result.get().fuelItemStack;
                    }
                    hasFuel = fuelItem != null;
                    if (hasFuel) {
                        int stackSize = fuelItem.getAmount();
                        int consumeQty = 1;
                        burnTime = result.get().burnTime();
                        if (fuelBurnRate > burnTime) {
                            consumeQty = (int) (fuelBurnRate / burnTime);
                            consumeQty = Math.min(consumeQty, stackSize);
                            burnTime *= consumeQty;
                        }
                        fuelItem = fuelItem.asQuantity(consumeQty);

                        consumeFuelEffect = new ConsumeFuelItemInBurner(
                                this.craft,
                                result.get().location().getAbsoluteLocation(),
                                consumeQty
                        );
                    }
                }

                // Step 2): Check fuel tanks
                if (!hasFuel) {
                    // TODO: Run logic to search fuel in fuel tanks
                }

                // Step 3): Check solid fuel
                if (!hasFuel) {
                    // TODO: Implement
                }

                // Step 4): If we found any fuel, build the effect
                hasFuel = hasFuel && fuelItem != null && burnTime > 0.0D;
                if (hasFuel) {
                    fuelCraftEffect = new ApplyCraftFuel(
                            this.craft,
                            fuelItem.clone(),
                            burnTime
                    );
                }

                if (consumeFuelEffect != null) {
                    additionalSteps.add(consumeFuelEffect);
                }
                if (fuelCraftEffect != null) {
                    additionalSteps.add(fuelCraftEffect);
                }
            }

            // TODO: Reset the furnace trackedlocations after a while

            additionalSteps.add(new SinkOutOfFuelCraftsAndApplyIsFueled(craft, hasFuel));

            // Reuse the burner update logic without invoking UpdateFuelBurnersTask#get(),
            // which is an independently submitted task and must not release our lock.
            this.burnersActive = hasFuel;
            additionalSteps.add(createBurnerUpdateEffect());

            if (Settings.Debug)
                Movecraft.getInstance().getLogger().info(String.format("Finished fuel burn task for craft <%s>! Time taken: %dms", craft.getUUID(), System.currentTimeMillis() - startTime));

            // The state remains held until the main-thread effects have been applied. This
            // prevents movement from starting between calculation and fuel/world updates.
            Effect completionEffect = new FinishFuelTask(craft, stateHandle, new Effect.AndEffect(additionalSteps));
            completionOwnsState = true;
            return completionEffect;
        } finally {
            if (!completionOwnsState) {
                stateHandle.release();
                FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
            }
        }

    }

    private record FinishFuelTask(Craft craft, CraftStateHandle stateHandle, Effect delegate) implements Effect {

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                stateHandle.release();
                FuelBurnRunnable.craftsInProgress.remove(craft.getUUID());
            }
        }
    }

    private record ReduceBurningFuelEffect(
            Craft craft,
            double fuelBurnRate
    ) implements Effect {

        @Override
        public void run() {
            // call event
            final FuelBurnEvent fuelBurnEvent = new FuelBurnEvent(craft, craft.getBurningFuel(), fuelBurnRate);
            Bukkit.getPluginManager().callEvent(fuelBurnEvent);
            craft.setBurningFuel(fuelBurnEvent.getBurningFuel() - fuelBurnEvent.getFuelBurnRate());
        }
    }

    private record ApplyCraftFuel(
            Craft craft,
            ItemStack fuelItem,
            double burnTime
    ) implements Effect {

        @Override
        public void run() {
            craft.setDataTag(FuelDataTags.CURRENT_FUEL_ITEM, fuelItem());
            craft.setBurningFuel(craft.getBurningFuel() + burnTime());
            craft.setMaxBurningFuel(craft.getBurningFuel());
        }
    }

    private record SinkOutOfFuelCraftsAndApplyIsFueled(
            Craft craft,
            boolean fueled
    ) implements Effect {

        @Override
        public void run() {
            // FuelBurnTask holds its craft state until this effect has run, so no
            // movement can be applied between calculation and sink and invalidate the result.
            // We were fueled, but now we are no longer fueled.
            if (craft.getDataTag(IS_FUELED)) {
                if (craft.getCraftProperties().get(PropertyKeys.SINK_WHEN_OUT_OF_FUEL) && !fueled) {
                    if (Settings.Debug) {
                        Movecraft.getInstance().getLogger().info("Scuttling craft <" + craft.getUUID().toString() +"> at <" + craft.getHitBox().getMidPoint().toString() + "> as it ran out of fuel!");
                    }
                    craft.setCruising(false, CraftStopCruiseEvent.Reason.CRAFT_SUNK);
                    CraftManager.getInstance().sink(craft, CraftSinkEvent.SIMPLE_SINK_REASONS.OUT_OF_FUEL);
                }
            }
            craft.setDataTag(IS_FUELED, fueled);
        }
    }

    private record ConsumeFuelItemInBurner(
            Craft craft,
            MovecraftLocation burnerLocation,
            int comsumeQty
    ) implements Effect {

        @Override
        public void run() {
            final World world = craft.getWorld();
            BlockState blockState = world.getBlockState(burnerLocation().toBukkit(world));
            if (Tags.FURNACES.contains(blockState.getType())) {
                if (blockState instanceof InventoryHolder inventoryHolder) {
                    if (inventoryHolder.getInventory() instanceof FurnaceInventory furnaceInventory) {
                        ItemStack fuelItemStack = furnaceInventory.getFuel();
                        if (fuelItemStack == null || fuelItemStack.isEmpty()) {
                            return;
                        }
                        final int stackSize = fuelItemStack.getAmount();
                        // TODO: Rewrite to support different stack sizes than 1 for buckets!
                        if (Tags.BUCKETS.contains(fuelItemStack.getType())) {
                            fuelItemStack.setType(Material.BUCKET);
                        }
                        else if (comsumeQty == stackSize) {
                            furnaceInventory.remove(fuelItemStack);
                        } else {
                            fuelItemStack.setAmount(stackSize - comsumeQty);
                        }
                    }
                }
            }
        }
    }

    // TODO: Find better name
    private record WorkerData(
        @Nullable FurnaceInventory inventory,
        @Nullable TrackedLocation location,
        double burnTime,
        double effectiveBurnRate,
        @Nullable ItemStack fuelItemStack
    ) {
        public static WorkerData empty() {
            return new WorkerData(null, null, 0, 0, null);
        }

        public WorkerData add(WorkerData other) {
            if (this.inventory == null || this.location == null) {
                return new WorkerData(other.inventory, other.location, other.burnTime, other.effectiveBurnRate, other.fuelItemStack);
            } else {
                return new WorkerData(this.inventory, this.location, this.burnTime, this.effectiveBurnRate, this.fuelItemStack);
            }
        }
    }

    // TODO: Find better name
    private static final class Worker extends AbstractWorker<Void> {
        private final @NotNull Craft craft;
        private final @NotNull TrackedLocation location;
        private final @NotNull NamespacedKeyToDoubleProperty fuelTypes;
        private final @NotNull double fuelBurnRateIn;
        private final AtomicBoolean validatedFound;
        private final AtomicReference<WorkerData> resultReference;

        private Worker(
                @NotNull WorldManagerWorker worker,
                @NotNull Craft craft,
                @NotNull TrackedLocation location,
                @NotNull NamespacedKeyToDoubleProperty fuelTypes,
                @NotNull double fuelBurnRateIn,
                AtomicBoolean validatedFound,
                AtomicReference<WorkerData> resultReference
        ) {
            super(worker);
            this.craft = craft;
            this.location = location;
            this.fuelTypes = fuelTypes;
            this.fuelBurnRateIn = fuelBurnRateIn;
            this.validatedFound = validatedFound;
            this.resultReference = resultReference;
        }

        @Override
        // Return null if we do not want this furnace to be the active burner
        public Void callInternally() {
            if (validatedFound.get()) {
                return null;
            }
            final MovecraftWorld movecraftWorld = craft.getMovecraftWorld();
            final MovecraftLocation movecraftLocation = location.getAbsoluteLocation();
            final BlockState blockState = movecraftWorld.getState(movecraftLocation);

            if (!Tags.FURNACES.contains(blockState.getType())) {
                return null;
            }
            FurnaceInventory furnaceInventory = null;
            if (blockState instanceof InventoryHolder inventoryHolder) {
                if (inventoryHolder.getInventory() instanceof FurnaceInventory) {
                    furnaceInventory = (FurnaceInventory) inventoryHolder.getInventory();
                }
            }
            if (furnaceInventory == null) {
                return null;
            }

            ItemStack fuelItemStack = furnaceInventory.getFuel();
            if (fuelItemStack == null || fuelItemStack.isEmpty()) {
                return null;
            }

            NamespacedKey itemID = fuelItemStack.getType().getKey();
            if (!fuelTypes.contains(itemID)) {
                return null;
            }
            double burnTime = craft.getCraftProperties().get(PropertyKeys.FUEL_TYPES).get(itemID);
            // TODO: Implement special effect API for top slot
            final FuelBurnEvent fuelBurnEvent = WorldManager.INSTANCE.executeMain(() -> {
                FuelBurnEvent event = new FuelBurnEvent(craft, burnTime, fuelBurnRateIn);
                Bukkit.getServer().getPluginManager().callEvent(event);
                return event;
            });
            if (fuelBurnEvent.getBurningFuel() <= 0) {
                return null;
            }
            resultReference.set(new WorkerData(
                    furnaceInventory,
                    location,
                    fuelBurnEvent.getBurningFuel(),
                    fuelBurnEvent.getFuelBurnRate(),
                    fuelItemStack
            ));
            validatedFound.getAndSet(true);
            return null;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Worker) obj;
            return Objects.equals(this.craft, that.craft) &&
                    Objects.equals(this.location, that.location) &&
                    Objects.equals(this.fuelTypes, that.fuelTypes) &&
                    Double.doubleToLongBits(this.fuelBurnRateIn) == Double.doubleToLongBits(that.fuelBurnRateIn) &&
                    Objects.equals(this.validatedFound, that.validatedFound) &&
                    Objects.equals(this.resultReference, that.resultReference);
        }

        @Override
        public int hashCode() {
            return Objects.hash(craft, location, fuelTypes, fuelBurnRateIn, validatedFound, resultReference);
        }

        @Override
        public String toString() {
            return "Worker[" +
                    "craft=" + craft + ", " +
                    "location=" + location + ", " +
                    "fuelTypes=" + fuelTypes + ", " +
                    "fuelBurnRateIn=" + fuelBurnRateIn + ", " +
                    "validatedFound=" + validatedFound + ", " +
                    "resultReference=" + resultReference + ']';
        }


        }

}
