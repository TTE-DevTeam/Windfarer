package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.MovecraftRotation;
import net.countercraft.movecraft.craft.*;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.effects.Effect;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.Pair;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public class SubcraftRotateSign extends AbstractSubcraftSign {

    public SubcraftRotateSign(Function<String, @Nullable TypeSafeCraftType> craftTypeRetrievalFunction, Supplier<Plugin> plugin) {
        super(craftTypeRetrievalFunction, plugin);
    }

    @Override
    protected void runDetectTask(Action clickType, TypeSafeCraftType subcraftType, Craft parentcraft, World world, Entity interactor, MovecraftLocation startPoint, @NotNull SubcraftSignOperation operation) {
        final MovecraftRotation rotation = MovecraftRotation.fromAction(clickType);
        if (rotation == MovecraftRotation.NONE) {
            operation.finish();
            return;
        }

        // TODO: Migrate to use controller
        CraftManager.getInstance().detectSubcraft(
                startPoint,
                subcraftType, (type, w, p, parents) -> {
                    if (parents.size() > 1)
                        return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedString(
                                "Detection - Failed - Already commanding a craft")), null);
                    if (parents.size() < 1)
                        return new Pair<>(Result.succeed(), new SubcraftRotateCraft(type, w, p));

                    Craft parent = parents.iterator().next();
                    return new Pair<>(Result.succeed(), new SubCraftImpl(type, w, parent));
                },
                world, interactor, interactor,
                subcraft -> () -> {
                    Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(subcraft, CraftPilotEvent.Reason.SUB_CRAFT));
                    if (subcraft instanceof SubCraft) { // Subtract craft from the parent
                        if (!SubcraftParentLock.acquire((SubCraft) subcraft, interactor, operation.getParentLock())) {
                            operation.finish();
                            return;
                        }
                        ((SubCraft) subcraft).cutOutOfParent();
                    }

                    // The subcraft is released as the consequence of its rotation, however that ends
                    SubcraftParentLock.releaseAfterMovement(subcraft, operation);
                    subcraft.rotate(rotation, startPoint, true);
                },
                // Nothing was detected => everything is over
                operation::finish
        );
    }

    @Override
    protected boolean isActionAllowed(String action) {
        return action.equalsIgnoreCase("ROTATE");
    }

    @Override
    protected void onActionAlreadyInProgress(Entity interactor) {
        interactor.sendMessage(I18nSupport.getInternationalisedString("Rotation - Already Rotating"));
    }

    static final Component DEFAULT_LINE_3 = Component.text("_\\ / _");
    static final Component DEFAULT_LINE_4 = Component.text("/ \\");

    @Override
    protected Component getDefaultTextFor(int line) {
        switch (line) {
            case 2:
                return DEFAULT_LINE_3;
            case 3:
                return DEFAULT_LINE_4;
            default:
                return null;
        }
    }

    @Override
    protected boolean canPlayerUseSignForCraftType(Action clickType, SignListener.SignWrapper sign, Entity interactor, TypeSafeCraftType subcraftType) {
        final String permissionNode = subcraftType.get(PropertyKeys.PERMISSION_NODE_ROTATE);
        if (!permissionNode.isBlank() && subcraftType.get(PropertyKeys.REQUIRE_PERM_FOR_ASSEMBLY, interactor.getWorld()) && !interactor.hasPermission(permissionNode)) {
            interactor.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return false;
        }
        return true;
    }

    @Override
    protected void onCraftIsBusy(Entity interactor, Craft craft) {
        interactor.sendMessage(I18nSupport.getInternationalisedString("Detection - Parent Craft is busy"));
    }

    @Override
    protected void onCraftNotFound(Entity interactor, SignListener.SignWrapper sign) {
        // Ignored
    }
}
