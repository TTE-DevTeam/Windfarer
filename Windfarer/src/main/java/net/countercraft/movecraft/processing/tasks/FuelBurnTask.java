package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.async.FuelBurnRunnable;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.property.NamespacedKeyToDoubleProperty;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.events.FuelBurnEvent;
import net.countercraft.movecraft.features.fuel.FuelDataTags;
import net.countercraft.movecraft.features.fuel.FuelUtil;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.effects.Effect;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static net.countercraft.movecraft.features.fuel.FuelDataTags.IS_FUELED;

// Problematic methods (highest to lowest9:
//   - updateFurnaces (getBlockState(), BlockState.update())
//   - burnFuel()

public class FuelBurnTask implements Supplier<Effect> {

    private final Craft craft;
    private final double fuelBurnRate;

    public FuelBurnTask(Craft craft, double fuelBurnRate) {
        this.craft = craft;
        this.fuelBurnRate = fuelBurnRate;
    }

    @Override
    public Effect get() {
        final long startTime = System.currentTimeMillis();
        Movecraft.getInstance().getLogger().info(String.format("Starting fuel burn task for craft <%s>...", craft.getUUID()));
        // TODO: new craft status concept! Stop the craft from moving while we do something
        // TODO: Add API for additional fuel sources and how to use them
        this.craft.setProcessing(true);

        boolean hasFuel = false;
        double fuelBurnRate = this.fuelBurnRate;
        Effect consumeFuelEffect = Effect.NONE;
        Effect fuelCraftEffect = Effect.NONE;

        // If the specified rate is 0 and we are here, then we burn fuel, but not currently!
        if (fuelBurnRate <= 0.0D) {
            craft.setBurningFuel(0.0);
        }
        // Fuel item burning
        // We currently have somethign that we are burning
        else if (craft.getBurningFuel() >= fuelBurnRate) {
            hasFuel = true;

            double burningFuel = craft.getBurningFuel();
            final double finalBurningFuel = burningFuel;
            final double finalFuelBurnRate = fuelBurnRate;
            // call event
            final FuelBurnEvent event = WorldManager.INSTANCE.executeMain(() -> {
                final FuelBurnEvent fuelBurnEvent = new FuelBurnEvent(craft, finalBurningFuel, finalFuelBurnRate);
                Bukkit.getPluginManager().callEvent(fuelBurnEvent);
                return fuelBurnEvent;
            });
            if (event.getBurningFuel() != burningFuel)
                burningFuel = event.getBurningFuel();
            if (event.getFuelBurnRate() != fuelBurnRate)
                fuelBurnRate = event.getFuelBurnRate();
            craft.setBurningFuel(burningFuel - fuelBurnRate);
        }
        // Find a new fuel item to burn, save to tag, remove item from furnace, throw event
        // We burnt the item we had, if we had any. Search for something new to burn
        else {
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
                ArrayList<ForkJoinTask<Void>> workers = new ArrayList<>();
                final NamespacedKeyToDoubleProperty fuelTypes = this.craft.getCraftProperties().get(PropertyKeys.FUEL_TYPES);
                final double finalBurnRate = fuelBurnRate;
                final AtomicBoolean validatedFound = new AtomicBoolean(false);
                final AtomicReference<WorkerData> resultReference = new AtomicReference<>(null);
                furnaces.forEach(location -> workers.add(ForkJoinTask.adapt(new Worker(craft, location, fuelTypes, finalBurnRate, validatedFound, resultReference))));

                // Invokes all, the workers check towards the AtomicBoolean if there still is something to do
                ForkJoinTask.invokeAll(workers);

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
        }

        // TODO: Reset the furnace trackedlocations after a while

        craft.setProcessing(false);

        List<Effect> additionalSteps = new ArrayList<>(2);
        additionalSteps.add(consumeFuelEffect);
        additionalSteps.add(fuelCraftEffect);
        additionalSteps.add(new SinkOutOfFuelCraftsAndApplyIsFueled(craft, hasFuel));
        // Update burner effect at last
        additionalSteps.add(() -> {
            boolean fueled = craft.getDataTag(IS_FUELED);
            WorldManager.INSTANCE.submit(new UpdateFuelBurnersTask(craft, fueled));
        });
        Movecraft.getInstance().getLogger().info(String.format("Finished fuel burn task for craft <%s>! Time taken: %dms", craft.getUUID(), System.currentTimeMillis() - startTime));
        return new Effect.AndEffect(additionalSteps);
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
            // Only sink it here if it is not moving. If it is moving, it is sunk via the task itself!
            // We are moving, that means our result could be faulty
            if (!craft.isNotProcessing() && !fueled) {
                if (Settings.Debug) {
                    Movecraft.getInstance().getLogger().info("Craft <" + craft.getUUID().toString() +"> technically cant burn any more fuel but is currently busy, we will try again later!");
                }
                return;
            }

            // We were fueld, but now we are no longer fueled
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
    private record Worker(
            @NotNull Craft craft,
            @NotNull TrackedLocation location,
            @NotNull NamespacedKeyToDoubleProperty fuelTypes,
            @NotNull double fuelBurnRateIn,
            AtomicBoolean validatedFound,
            AtomicReference<WorkerData> resultReference) implements Callable<Void> {

        @Override
        // Return null if we do not want this furnace to be the active burner
        public Void call() {
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

    }

}
