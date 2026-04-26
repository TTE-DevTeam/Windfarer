package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import org.bukkit.NamespacedKey;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CraftSignManager {

    static final CraftDataTagKey<CraftSignManager> SIGN_MANAGER_CRAFT_DATA_TAG_KEY = CraftDataTagRegistry.INSTANCE.registerTagKey(new NamespacedKey("windfarer", "craft-sign-manager"), CraftSignManager::new);

    private final WeakReference<Craft> owningCraft;
    private final Map<Class<? extends AbstractMovecraftSign>, WeakReference<Set<TrackedLocation>>> signLocationCache = new ConcurrentHashMap<>();

    public CraftSignManager(Craft owningCraft) {
        this.owningCraft = new WeakReference<>(owningCraft);
        this.init(owningCraft);
    }

    public static CraftSignManager of(final Craft craft) {
        return craft.getDataTag(SIGN_MANAGER_CRAFT_DATA_TAG_KEY);
    }

    public Set<MovecraftLocation> getSignsOfClass(Class<? extends AbstractMovecraftSign> signHandler) {
        Set<MovecraftLocation> result = new HashSet<>();
        WeakReference<Set<TrackedLocation>> value = this.signLocationCache.getOrDefault(signHandler, null);
        if (value != null && value.get() != null) {
            final Set<TrackedLocation> set = value.get();
            set.forEach(tl -> result.add(tl.getAbsoluteLocation()));
        }
        return result;
    }

    protected NamespacedKey getKey(Class<? extends AbstractMovecraftSign> signHandler) {
        return new NamespacedKey("windfarer", "craft-sign/" + signHandler.getName());
    }

    public void addSign(Class<? extends AbstractMovecraftSign> signHandler, MovecraftLocation sign) {
        final Craft craft = this.owningCraft.get();
        if (craft != null) {
            Set<TrackedLocation> trackedLocations = craft.getTrackedLocations().computeIfAbsent(getKey(signHandler), k -> new HashSet<>());
            trackedLocations.add(new TrackedLocation(craft, sign));
        }
    }

    public void removeSign(Class<? extends AbstractMovecraftSign> signHandler, MovecraftLocation sign) {
        final Craft craft = this.owningCraft.get();
        if (craft != null) {
            Set<TrackedLocation> trackedLocations = craft.getTrackedLocations().getOrDefault(getKey(signHandler), null);
            if (trackedLocations != null) {
                TrackedLocation tl = new TrackedLocation(craft, sign);
                trackedLocations.remove(tl);
            }
        }
    }

    public void refreshData() {
        final Craft craft = this.owningCraft.get();
        // TODO: This should never be the case....
        if (craft == null) {
            return;
        }
        // Clear the current sets
        this.signLocationCache.values().forEach(wr -> {
            Set<TrackedLocation> set = wr.get();
            if (set != null && !set.isEmpty()) {
                set.clear();
            }
        });
        this.init(craft);
    }

    protected void init(final Craft craft) {
        // Now, recompute the signs
        // TODO: Run this async by chance?
        SignListener.INSTANCE.executeForAllCraftSigns(craft, (handler, sign) -> {
            addSign(handler.getClass(), new MovecraftLocation(sign.block().getLocation()));
        });
    }

}
