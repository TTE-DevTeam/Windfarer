package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.TrackedLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.datatag.CraftDataTagKey;
import net.countercraft.movecraft.craft.datatag.CraftDataTagRegistry;
import net.countercraft.movecraft.util.functions.TriFunction;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.jetbrains.annotations.NotNull;

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
        final Set<Class<? extends AbstractMovecraftSign>> keySet = new HashSet<>();
        for (Class<? extends AbstractMovecraftSign> clazz : signLocationCache.keySet()) {
            if (clazz == signHandler || signHandler.isAssignableFrom(clazz)) {
                keySet.add(clazz);
            }
        }

        Set<MovecraftLocation> result = new HashSet<>();
        if (keySet.isEmpty()) {
            return result;
        }
        for (Class<? extends AbstractMovecraftSign> clazz : keySet) {
            WeakReference<Set<TrackedLocation>> value = this.signLocationCache.getOrDefault(clazz, null);
            if (value != null && value.get() != null) {
                final Set<TrackedLocation> set = value.get();
                set.forEach(tl -> result.add(tl.getAbsoluteLocation()));
            }
        }
        return result;
    }

    protected NamespacedKey getKey(Class<? extends AbstractMovecraftSign> signHandler) {
        return new NamespacedKey("windfarer", "craft-sign/" + signHandler.getName().toLowerCase());
    }

    public void addSign(Class<? extends AbstractMovecraftSign> signHandler, MovecraftLocation sign) {
        final Craft craft = this.owningCraft.get();
        if (craft != null) {
            Set<TrackedLocation> trackedLocations = craft.getTrackedLocations().computeIfAbsent(getKey(signHandler), k -> new HashSet<>());
            this.signLocationCache.put(signHandler, new WeakReference<>(trackedLocations));
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
            if (trackedLocations.isEmpty()) {
                this.signLocationCache.remove(signHandler);
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

    public Set<MovecraftLocation> getAllSigns() {
        Set<MovecraftLocation> result = new HashSet<>();
        for (@NotNull AbstractMovecraftSign signHandler : MovecraftSignRegistry.INSTANCE.getAllValues()) {
            if (signHandler instanceof AbstractCraftSign acs) {
                result.addAll(this.getSignsOfClass(acs.getClass()));
            }
        }
        return result;
    }

    public Set<Class<? extends AbstractMovecraftSign>> getSignTypes() {
        return new HashSet<>(this.signLocationCache.keySet());
    }


    public static <T extends AbstractCraftSign> void executeForSignsOfType(final Class<T> signClass, Craft craft, final TriFunction<T, SignListener.SignWrapper, Craft, Boolean> functionToRun) {
        final CraftSignManager craftSignManager = CraftSignManager.of(craft);
        if (craftSignManager != null) {
            for (final MovecraftLocation mLoc : craftSignManager.getSignsOfClass(signClass)) {
                final Block b = mLoc.toBukkit(craft.getWorld()).getBlock();
                if (!(b.getState() instanceof Sign)) {
                    continue;
                }
                final Sign s = (Sign) b.getState();
                boolean update = false;
                SignListener.SignWrapper[] wrappers = SignListener.INSTANCE.getSignWrappers(s, true);
                for (SignListener.SignWrapper wrapperTmp : wrappers) {
                    final AbstractCraftSign signHandler = MovecraftSignRegistry.INSTANCE.getCraftSign(wrapperTmp.line(0));
                    if (signHandler == null)
                        continue;

                    if (signHandler.getClass().isAssignableFrom(signClass)) {
                        final T typed = (T) signHandler;
                        update = functionToRun.apply(typed, wrapperTmp, craft) || update;
                    }
                }
                if (update)
                    s.update();
            }
        }
    }

}
