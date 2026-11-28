package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class AbstractSubcraftSign extends AbstractCraftSign {

    // TODO: Replace by writing to the signs nbt data
    /** The parent is locked for this long at most while a subcraft is detected */
    protected static final long DETECTION_LOCK_TIMEOUT_TICKS = 100;
    /** A use of a sign is finished after this long at the latest (detection, movement and release together) */
    protected static final long OPERATION_BACKSTOP_TICKS = 600;
    protected static final Set<MovecraftLocation> IN_USE = Collections.synchronizedSet(new HashSet<>());

    protected final Function<String, @Nullable TypeSafeCraftType> craftTypeRetrievalFunction;

    protected final Supplier<Plugin> pluginInstance;

    public AbstractSubcraftSign(Function<String, @Nullable TypeSafeCraftType> craftTypeRetrievalFunction, final Supplier<Plugin> plugin) {
        this(null, craftTypeRetrievalFunction, plugin);
    }

    public AbstractSubcraftSign(final String permission, Function<String, @Nullable TypeSafeCraftType> craftTypeRetrievalFunction, final Supplier<Plugin> plugin) {
        super(permission, false);
        this.craftTypeRetrievalFunction = craftTypeRetrievalFunction;
        this.pluginInstance = plugin;
    }

    @Override
    public boolean processSignClick(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        if (!this.isSignValid(clickType, sign, interactor)) {
            return false;
        }
        if (!this.canPlayerUseSign(clickType, sign, interactor)) {
            return false;
        }
        Craft craft = this.getCraft(sign);

        if (craft instanceof PlayerCraft pc) {
            if (pc.getStateTracker().isMovingOrChangingShape() && !this.ignoreCraftIsBusy) {
                this.onCraftIsBusy(interactor, craft);
                return false;
            }
        }

        return internalProcessSign(clickType, sign, interactor, craft);
    }

    @Override
    protected boolean internalProcessSign(Action clickType, SignListener.SignWrapper sign, Entity interactor, Craft craft) {
        if (craft != null) {
            // TODO: Add property to crafts that they can use subcrafts?
            if (!this.canPlayerUseSignOn(interactor, craft)) {
                return false;
            }
        }
        return this.internalProcessSignWithCraft(clickType, sign, craft, interactor);
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        if (!this.isSignValid(Action.PHYSICAL, sign, event.getPlayer())) {
            for (int i = 0; i < sign.lines().size(); i++) {
                sign.line(i, Component.empty());
            }
            return false;
        }
        this.applyDefaultText(sign);
        return true;
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        String[] headerSplit = sign.getRaw(0).split(" ");
        if (headerSplit.length != 2) {
            return false;
        }
        // TODO: Change to enums?
        String action = headerSplit[headerSplit.length - 1];
        if (!this.isActionAllowed(action)) {
            return false;
        }
        return this.getCraftType(sign) != null;
    }

    @Override
    protected boolean canPlayerUseSign(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        if (!super.canPlayerUseSign(clickType, sign, interactor)) {
            return false;
        }
        TypeSafeCraftType craftType = this.getCraftType(sign);
        if (craftType != null) {
            if (!craftType.get(PropertyKeys.CAN_BE_SUBCRAFT)) {
                // TODO: Print message to player
                return false;
            }

            final String permissionNode = craftType.get(PropertyKeys.PERMISSION_NODE_PILOT);
            return (permissionNode.isBlank() || interactor.hasPermission(permissionNode)) && this.canPlayerUseSignForCraftType(clickType, sign, interactor, craftType);
        }
        return false;
    }

    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, @Nullable Craft craft, Entity interactor) {
        TypeSafeCraftType subcraftType = this.getCraftType(sign);

        final Location signLoc = sign.block().getLocation();
        final MovecraftLocation startPoint = new MovecraftLocation(signLoc.getBlockX(), signLoc.getBlockY(), signLoc.getBlockZ());

        // The parent must not move or change while the subcraft is detected
        final ParentDetectionLock parentLock = craft == null ? null : ParentDetectionLock.tryAcquire(craft);
        if (craft != null && parentLock == null) {
            this.onCraftIsBusy(interactor, craft);
            return false;
        }

        if (!IN_USE.add(startPoint)) {
            if (parentLock != null) {
                parentLock.release();
            }
            this.onActionAlreadyInProgress(interactor);
            return true;
        }

        final SubcraftSignOperation operation = new SubcraftSignOperation(IN_USE, startPoint, parentLock);

        this.applyDefaultText(sign);

        final World world = sign.block().getWorld();

        // Safety nets only, nothing may stay locked forever if something ends in a way nobody notices.
        // Both do nothing if everything was finished regularly.
        if (parentLock != null) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Does nothing if the lock was handed over to the subcraft
                    parentLock.release();
                }
            }.runTaskLater(this.pluginInstance.get(), DETECTION_LOCK_TIMEOUT_TICKS);
        }
        new BukkitRunnable() {
            @Override
            public void run() {
                operation.finish();
            }
        }.runTaskLater(this.pluginInstance.get(), OPERATION_BACKSTOP_TICKS);

        try {
            this.runDetectTask(clickType, subcraftType, craft, world, interactor, startPoint, operation);
        } catch (RuntimeException | Error throwable) {
            operation.finish();
            throw throwable;
        }

        return true;
    }

    protected void applyDefaultText(SignListener.SignWrapper sign) {
        if (sign.getRaw(2).isBlank() && sign.getRaw(3).isBlank()) {
            Component l3 = this.getDefaultTextFor(2);
            Component l4 = this.getDefaultTextFor(3);
            if (l3 != null) {
                sign.line(2, l3);
            }
            if (l4 != null) {
                sign.line(3, l4);
            }
        }
    }

    @Nullable
    protected TypeSafeCraftType getCraftType(SignListener.SignWrapper wrapper) {
        String ident = wrapper.getRaw(1);
        if (ident.trim().isBlank()) {
            return null;
        }
        return this.craftTypeRetrievalFunction.apply(ident);
    }

    @Override
    public boolean shouldCancelEvent(boolean processingSuccessful, @Nullable Action type, boolean sneaking, EventType eventType) {
        boolean resultSuper = super.shouldCancelEvent(processingSuccessful, type, sneaking, eventType);
        if (!resultSuper) {
            return eventType == EventType.SIGN_CLICK_ON_CRAFT || eventType == EventType.SIGN_CLICK;
        }
        return resultSuper;
    }

    /**
     * Detects and handles the subcraft.
     *
     * @param operation this use of the sign. It has to be finished ({@link SubcraftSignOperation#finish()}) when everything is
     *                  over: when the detection fails or the action is aborted at once, otherwise when the subcraft was
     *                  released again after its movement. Its parent lock has to be handed over to the subcraft.
     */
    protected abstract void runDetectTask(Action clickType, TypeSafeCraftType subcraftType, Craft parentCraft, World world, Entity interactor, MovecraftLocation startPoint, @NotNull SubcraftSignOperation operation);
    protected abstract boolean isActionAllowed(final String action);
    protected abstract void onActionAlreadyInProgress(Entity interactor);
    protected abstract Component getDefaultTextFor(int line);
    protected abstract boolean canPlayerUseSignForCraftType(Action clickType, SignListener.SignWrapper sign, Entity interactor, TypeSafeCraftType subCraftType);

    @Override
    protected boolean canPlayerUseSignOn(Entity interactor, @Nullable Craft craft) {
        if (super.canPlayerUseSignOn(interactor, craft)) {
            return true;
        }
        return craft.getHitBox().inBounds(interactor.getLocation().getX(), interactor.getLocation().getY(), interactor.getLocation().getZ());
    }

    @Override
    public TextColor highlightColor() {
        return NamedTextColor.GOLD;
    }

}
