package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.FilteredTrackedLocations;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// DONE: Add cache for banners
// DONE: Write generalized variant for caching blocks via trackedlocations with lifetime
public class UpdateBannersTask implements Supplier<Effect>, Effect {

    protected final World world;
    protected final List<MovecraftLocation> updateLocations = new ArrayList<>();
    protected final Craft craft;

    private static final NamespacedKey BANNER_LOCATIONS_KEY = new NamespacedKey("windfarer", "tracked_locations/banners");
    protected static final CraftDataTagKey<FilteredTrackedLocations> BANNER_LOCATIONS = CraftDataTagRegistry.INSTANCE.registerTagKey(BANNER_LOCATIONS_KEY, c -> new FilteredTrackedLocations(c, BANNER_LOCATIONS_KEY, 20000, UpdateBannersTask::isBannerBlock));

    public UpdateBannersTask(Craft craft) {
        this.world = craft.getWorld();
        this.craft = craft;
    }

    @Override
    public Effect get() {
        final long startTime = System.currentTimeMillis();
        Movecraft.getInstance().getLogger().info(String.format("Starting banner update task for craft <%s>...", craft.getUUID()));
        FilteredTrackedLocations banners = this.craft.getDataTag(BANNER_LOCATIONS);
        for (TrackedLocation trackedLocation : banners) {
            this.updateLocations.add(trackedLocation.getAbsoluteLocation());
        }
        Movecraft.getInstance().getLogger().info(String.format("Finished banner update task for craft <%s>! Time taken: %dms", craft.getUUID(), System.currentTimeMillis() - startTime));
        return this;
    }

    protected static Result isBannerBlock(MovecraftLocation location, MovecraftWorld world, Craft craftTmp) {
        Material material = world.getMaterial(location);
        if (Tags.BANNERS.contains(material)) {
            return Result.fail();
        }
        BlockState state = world.getState(location);
        if (state instanceof Banner banner) {
            if (banner.getPatterns().size() > 0) {
                return Result.succeed();
            }
        }
        return Result.fail();
    }

    @Override
    public void run() {
        //boolean processing = !this.craft.isNotProcessing();
        //this.craft.setProcessing(true);
        for (MovecraftLocation location : this.updateLocations) {
            Block block = location.toBukkit(this.world).getBlock();
            BlockState state = block.getState();
            if (state instanceof Banner banner) {
                banner.update(false, false);
            }
        }
        //this.craft.setProcessing(processing);
    }
}
