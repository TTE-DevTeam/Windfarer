package net.countercraft.movecraft.processing.tasks;

import net.countercraft.movecraft.async.MovementStates;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.WorldManagerWorkers;
import net.countercraft.movecraft.processing.worker.WorldManagerWorker;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.FilteredTrackedLocations;
import net.countercraft.movecraft.util.Tags;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.function.Supplier;

// DONE: Add cache for banners
// DONE: Write generalized variant for caching blocks via trackedlocations with lifetime
public class UpdateBannersTask implements Supplier<Effect>, Effect {

    protected final World world;
    protected final List<MovecraftLocation> updateLocations = new ArrayList<>();
    protected final Craft craft;

    // The post movement step of a movement: runs while the craft still holds the states of that movement,
    // which are released as soon as the banners have been updated (or the update failed)
    protected final MovementStates states;

    private static final NamespacedKey BANNER_LOCATIONS_KEY = new NamespacedKey("windfarer", "tracked_locations/banners");
    protected static final CraftDataTagKey<FilteredTrackedLocations> BANNER_LOCATIONS = CraftDataTagRegistry.INSTANCE.registerTagKey(BANNER_LOCATIONS_KEY, c -> new FilteredTrackedLocations(c, BANNER_LOCATIONS_KEY, 20000, UpdateBannersTask::isBannerBlock));

    /**
     * @param states the states of the movement this is the post movement step of. They are released by the task.
     */
    public static @NotNull UpdateBannersTask createTask(final Craft craft, final MovementStates states) {
        return new UpdateBannersTask(craft, states);
    }

    protected UpdateBannersTask(Craft craft, MovementStates states) {
        this.world = craft.getWorld();
        this.craft = craft;
        this.states = states;
    }

    @Override
    public Effect get() {
        try {
            return this.collectBanners();
        } catch (RuntimeException | Error throwable) {
            this.states.release();
            throw throwable;
        }
    }

    private Effect collectBanners() {
        final long startTime = System.currentTimeMillis();
        if (Settings.Debug)
            Movecraft.getInstance().getLogger().info(String.format("Starting banner update task for craft <%s>...", craft.getUUID()));

        final WorldManagerWorker worker = WorldManagerWorker.getThreadLocalOrDefault(WorldManagerWorkers.LOW_PRIORITY);
        final FilteredTrackedLocations banners = this.craft.getDataTag(BANNER_LOCATIONS);
        final CompletableFuture<Void> completion = banners.getSetAsync()
                .thenAccept(locations -> {
                    for (TrackedLocation trackedLocation : locations) {
                        this.updateLocations.add(trackedLocation.getAbsoluteLocation());
                    }
                    if (Settings.Debug) {
                        Movecraft.getInstance().getLogger().info(String.format(
                                "Finished banner update task for craft <%s>! Time taken: %dms",
                                craft.getUUID(), System.currentTimeMillis() - startTime
                        ));
                    }
                    // The actual Bukkit block updates remain an Effect and are run by
                    // WorldManager on the primary thread.
                    worker.submitEffectToWorldChanges(this);
                })
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        this.states.release();
                        Movecraft.getInstance().getLogger().log(
                                Level.SEVERE,
                                "Failed to collect banner locations for craft " + craft.getUUID(),
                                throwable
                        );
                    }
                });

        worker.trackAsyncWork(completion);
        return Effect.NONE;
    }

    protected static Result isBannerBlock(MovecraftLocation location, MovecraftWorld world, Craft craftTmp) {
        Material material = world.getMaterial(location);
        if (!Tags.BANNERS.contains(material)) {
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
        try {
            for (MovecraftLocation location : this.updateLocations) {
                Block block = location.toBukkit(this.world).getBlock();
                BlockState state = block.getState();
                if (state instanceof Banner banner) {
                    banner.update(false, false);
                }
            }
        } finally {
            this.states.release();
        }
    }
}
