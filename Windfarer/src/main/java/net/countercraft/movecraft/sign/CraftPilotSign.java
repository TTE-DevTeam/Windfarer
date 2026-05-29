package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.*;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

//TODO: This is not very pretty...
public class CraftPilotSign extends AbstractCraftPilotSign {

    static final Set<MovecraftLocation> PILOTING = Collections.synchronizedSet(new HashSet<>());

    public CraftPilotSign(TypeSafeCraftType craftType) {
        super(craftType);
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity interactor) {
        String header = sign.getRaw(0).trim();
        TypeSafeCraftType craftType = CraftManager.getInstance().getCraftTypeByName(header);
        if (craftType != this.craftType) {
            return false;
        }
        if (craftType.get(PropertyKeys.REQUIRE_PERM_FOR_ASSEMBLY, interactor.getWorld()) && !interactor.hasPermission("movecraft." + header + ".pilot")) {
            interactor.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected boolean internalProcessSign(Action clickType, SignListener.SignWrapper sign, Entity interactor, @Nullable Craft craft) {
        if (this.craftType.get(PropertyKeys.MUST_BE_SUBCRAFT) && craft == null) {
            return false;
        }
        World world = sign.block().getWorld();
        if (craft != null) {
            world = craft.getWorld();
        }
        Location loc = sign.block().getLocation();
        MovecraftLocation startPoint = new MovecraftLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        if (PILOTING.contains(startPoint)) {
            // Always return true
            return true;
        }

        runDetectTask(startPoint, interactor, sign, craft, world);

        return true;
    }

    protected void runDetectTask(MovecraftLocation startPoint, Entity interactor, final SignListener.SignWrapper signWrapper, Craft parentCraft, World world) {
        if (PILOTING.add(startPoint)) {
            final boolean isCruiseOnPilot = this.craftType.get(PropertyKeys.CRUISE_ON_PILOT);

            CraftManager.getInstance().detect(
                    startPoint,
                    craftType, (type, w, p, parents) -> {
                        // Assert instructions are not available normally, also this is checked in beforehand sort of
                        assert p != null; // Note: This only passes in a non-null player.
                        Craft result = null;
                        if (type.get(PropertyKeys.CRUISE_ON_PILOT)) {
                            if (parents.size() > 1)
                                return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedString(
                                        "Detection - Failed - Already commanding a craft")), null);
                            if (parents.size() == 1) {
                                Craft parent = parents.iterator().next();
                                result = new CruiseOnPilotSubCraft(type, world, p, parent);
                            }

                            result = new CruiseOnPilotCraft(type, world, p);
                        }
                        else {
                            if (parents.size() > 0)
                                return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedString(
                                        "Detection - Failed - Already commanding a craft")), null);

                            if (p instanceof Player player1) {
                                result = new PlayerCraftImpl(type, w, player1);
                            } else {
                                result = new PilotedCraftImpl(type, w, p);
                            }
                        }

                        if (result != null) {
                            if (!isCruiseOnPilot) {
                                NameSign.tryApplyName(result, signWrapper);
                            }
                            return new Pair<>(Result.succeed(), result);
                        }
                        throw new IllegalStateException("No craft created during detection!");
                    },
                    world, interactor, interactor,
                    craft -> () -> {
                        Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(craft, CraftPilotEvent.Reason.PLAYER));
                        if (craft instanceof SubCraft) { // Subtract craft from the parent
                            Craft parent = ((SubCraft) craft).getParent();
                            var newHitbox = parent.getHitBox().difference(craft.getHitBox());;
                            parent.setHitBox(newHitbox);
                            parent.setOrigBlockCount(parent.getOrigBlockCount() - craft.getHitBox().size());
                        }

                        if (isCruiseOnPilot) {
                            // Setup cruise direction
                            BlockFace facing = signWrapper.facing();
                            craft.setCruiseDirection(CruiseDirection.fromBlockFace(facing));

                            // Start craft cruising
                            craft.setLastCruiseUpdate(System.currentTimeMillis());
                            craft.setCruising(true);

                            // Stop craft cruising and sink it in 15 seconds
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    craft.setCruising(false, CraftStopCruiseEvent.Reason.CRAFT_SUNK);
                                    CraftManager.getInstance().sink(craft, CraftSinkEvent.SIMPLE_SINK_REASONS.CRUISE_LIFETIME);
                                }
                            }.runTaskLater(Movecraft.getInstance(), (craftType.get(PropertyKeys.CRUISE_ON_PILOT_LIFETIME)));
                        }
                        else {
                            // Release old craft if it exists
                            Craft oldCraft = CraftManager.getInstance().getCraftByEntity(interactor);
                            if (oldCraft != null)
                                CraftManager.getInstance().release(oldCraft, CraftReleaseEvent.Reason.PLAYER, false);
                        }
                    },
                    craft -> () -> {
                        PILOTING.remove(startPoint);
                    }
            );

            // Just to be sure...
            new BukkitRunnable() {
                @Override
                public void run() {
                    PILOTING.remove(startPoint);
                }
            }.runTaskLater(Movecraft.getInstance(), 4);
        }
    }

    @Override
    public boolean processSignChange(SignChangeEvent event, SignListener.SignWrapper sign) {
        String header = sign.getRaw(0).trim();
        TypeSafeCraftType craftType = CraftManager.getInstance().getCraftTypeByName(header);
        if (craftType != this.craftType) {
            return false;
        }
        if (Settings.RequireCreatePerm) {
            Player player = event.getPlayer();
            if (!player.hasPermission("movecraft." + header + ".create")) {
                player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
                return false;
            } else {
                return true;
            }
        } else {
            return true;
        }
    }
}
