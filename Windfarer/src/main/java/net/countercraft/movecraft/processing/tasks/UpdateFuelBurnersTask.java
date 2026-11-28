package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.features.fuel.CraftFurnaceUtil;
import net.countercraft.movecraft.features.fuel.FuelUtil;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Furnace;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static net.countercraft.movecraft.features.fuel.FuelDataTags.FUEL_PERCENTAGE;

/**
 * Updates fuel statistics and the lit state of the craft's tracked furnaces.
 *
 * This class intentionally does not reserve or release any craft state. It is
 * also used after a craft has been released, when the remaining task is only to turn its
 * furnaces off. FuelBurnTask holds its state for its complete calculation/effect lifecycle
 * and calls {@link #createBurnerUpdateEffect()} directly.
 */
public class UpdateFuelBurnersTask implements Supplier<Effect> {

    protected final Craft craft;
    protected boolean burnersActive;

    public UpdateFuelBurnersTask(Craft craft, boolean burnersActive) {
        this.craft = craft;
        this.burnersActive = burnersActive;
    }

    @Override
    public Effect get() {
        return createBurnerUpdateEffect();
    }

    /**
     * Builds the main-thread effect for the burner block states and refreshes the fuel
     * percentage used by status signs. This method is safe to call from FuelBurnTask while
     * that task holds its craft state.
     */
    protected Effect createBurnerUpdateEffect() {
        final MovecraftWorld movecraftWorld = craft.getMovecraftWorld();
        final Set<TrackedLocation> furnaceLocations = FuelUtil.getFuelBurners(craft);

        double cumulativeFuelLevel = 0.0D;
        int validBurnerCount = 0;
        for (TrackedLocation trackedLocation : furnaceLocations) {
            final MovecraftLocation location = trackedLocation.getAbsoluteLocation();
            if (!Tags.FURNACES.contains(movecraftWorld.getMaterial(location))) {
                continue;
            }

            final BlockState state = movecraftWorld.getState(location);
            if (state instanceof InventoryHolder inventoryHolder
                    && inventoryHolder.getInventory() instanceof FurnaceInventory furnaceInventory) {
                cumulativeFuelLevel += CraftFurnaceUtil.getFurnaceFuelLevel(
                        furnaceInventory,
                        craft.getCraftProperties()
                );
                validBurnerCount++;
            }
        }

        final double fuelPercentage = validBurnerCount == 0
                ? 0.0D
                : cumulativeFuelLevel / validBurnerCount;
        if (Double.compare(craft.getDataTag(FUEL_PERCENTAGE), fuelPercentage) != 0) {
            craft.setDataTag(FUEL_PERCENTAGE, fuelPercentage);
        }

        if (furnaceLocations.isEmpty()
                || !craft.getCraftProperties().get(PropertyKeys.FURNACE_FUEL_VISUALIZATION)) {
            return Effect.NONE;
        }
        return new UpdateBurnerLitStateEffect(movecraftWorld.getWorldUUID(), Set.copyOf(furnaceLocations), burnersActive);
    }

    /**
     * Changes only the lit property. In particular, do not rewrite burn-time/progress on
     * every fuel tick: that NMS state is not needed for the on/off visualization.
     */
    private record UpdateBurnerLitStateEffect(
            UUID worldUUID,
            Set<TrackedLocation> burners,
            boolean active
    ) implements Effect {

        @Override
        public void run() {
            final World world = Bukkit.getWorld(worldUUID());
            // Strange, but ok
            if (world == null) {
                return;
            }
            for (TrackedLocation trackedLocation : burners) {
                final Block block = trackedLocation.getAbsoluteLocation().toBukkit(world).getBlock();
                final BlockData blockData = block.getBlockData();
                if (!(blockData instanceof Furnace furnaceData) || furnaceData.isLit() == active) {
                    continue;
                }

                furnaceData.setLit(active);
                block.setBlockData(furnaceData, false);
            }
        }
    }
}
