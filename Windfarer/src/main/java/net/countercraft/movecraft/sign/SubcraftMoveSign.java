package net.countercraft.movecraft.sign;

import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SubCraft;
import net.countercraft.movecraft.craft.SubCraftImpl;
import net.countercraft.movecraft.craft.SubcraftMoveCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.events.CraftPilotEvent;
import net.countercraft.movecraft.listener.InteractListener;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.Pair;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.Action;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

public class SubcraftMoveSign extends AbstractSubcraftSign {

    public static final String OPERATION = "MOVE";

    public static final Component DEFAULT_LINE_3 = Component.text("-->");
    public static final Component DEFAULT_LINE_4 = Component.text("<--");

    public SubcraftMoveSign(Function<String, TypeSafeCraftType> craftTypeRetrievalFunction, Supplier<Plugin> plugin) {
        super(craftTypeRetrievalFunction, plugin);
    }

    protected SignListener.SignWrapper signWrapperCur = null;

    // TODO: Pass the signWrapper to the detect task later!
    @Override
    protected boolean internalProcessSignWithCraft(Action clickType, SignListener.SignWrapper sign, @Nullable Craft craft, Entity player) {
        this.signWrapperCur = sign;
        boolean result = super.internalProcessSignWithCraft(clickType, sign, craft, player);
        this.signWrapperCur = null;
        return result;
    }

    private static final double ROT_90_DEGREE = Math.toRadians(90.0D);
    private static final double ROT_270_DEGREE = Math.toRadians(270.0D);

    protected Vector getMovementVector(SignListener.SignWrapper wrapper, TypeSafeCraftType craftType) {
        final int MAX_MOVEMENT = craftType.get(PropertyKeys.MAX_STATIC_MOVE);

        int offsetFrontBack = 0;
        int offsetVertical = 0;
        int offsetLeftRight = 0;

        String shiftData = wrapper.getRaw(2);
        if (!shiftData.isBlank()) {
            String[] strArr = shiftData.split(",");
            if (strArr.length == 3) {
                try {
                    offsetFrontBack = Integer.parseInt(strArr[0]);
                    offsetVertical = Integer.parseInt(strArr[1]);
                    offsetLeftRight = Integer.parseInt(strArr[2]);
                } catch (NumberFormatException nfe) {
                    offsetFrontBack = 0;
                    offsetVertical = 0;
                    offsetLeftRight = 0;
                }
            }
        }

        final Vector signDirection = wrapper.facing().getDirection().normalize();
        Vector movement = signDirection.clone();
        if (offsetLeftRight != 0 || offsetFrontBack != 0 || offsetVertical != 0) {
            movement = new Vector(0, 0, 0);
            // Limit the values
            offsetFrontBack = Math.clamp(offsetFrontBack, -MAX_MOVEMENT, MAX_MOVEMENT);
            offsetVertical = Math.clamp(offsetVertical, -MAX_MOVEMENT, MAX_MOVEMENT);
            offsetLeftRight = Math.clamp(offsetLeftRight, -MAX_MOVEMENT, MAX_MOVEMENT);

            movement.setY(movement.getBlockY() + offsetVertical);

            if (offsetFrontBack != 0) {
                Vector shiftFrontBack = signDirection.clone().multiply(offsetFrontBack);
                movement.add(shiftFrontBack);
            }
            if (offsetLeftRight != 0) {
                // Right click => right ways
                // Left click => left ways
                // rotateAroundY => rotates mathematically positive! => positive => leftwise, otherwise rightwise
                // Rotate so it faces "rightways"
                Vector shiftLeftRight = signDirection.clone().rotateAroundY(ROT_270_DEGREE).multiply(offsetLeftRight);
                movement.add(shiftLeftRight);
            }
        }

        return movement;
    }

    @Override
    protected void runDetectTask(Action action, TypeSafeCraftType subcraftType, Craft craft, World world, Entity player, MovecraftLocation startPoint) {
        if (this.signWrapperCur == null) {
            return;
        }
        Vector movement = this.getMovementVector(this.signWrapperCur, subcraftType);
        if (action.isRightClick()) {
            movement.multiply(-1);
        }

        if (movement.length() != 0) {
            final Vector finalMovement = movement;
            CraftManager.getInstance().detect(startPoint, subcraftType, (type, w, p, parents) -> {
                if (parents.size() > 1) {
                    return new Pair(Result.failWithMessage(I18nSupport.getInternationalisedString("Detection - Failed - Already commanding a craft")), (Object) null);
                } else if (parents.size() < 1) {
                    return new Pair(Result.succeed(), new SubcraftMoveCraft(type, w, p));
                } else {
                    Craft parent = (Craft) parents.iterator().next();
                    return new Pair(Result.succeed(), new SubCraftImpl(type, w, parent));
                }
            }, world, player, player, (subcraft) -> {
                return () -> {
                    Bukkit.getServer().getPluginManager().callEvent(new CraftPilotEvent(subcraft, CraftPilotEvent.Reason.SUB_CRAFT));
                    boolean movementAllowed = true;
                    if (subcraft instanceof SubCraft && ((SubCraft) subcraft).getParent() != null) {
                        Craft parent = ((SubCraft) subcraft).getParent();
                        HitBox newHitbox = parent.getHitBox().difference(subcraft.getHitBox());
                        parent.setHitBox(newHitbox);

                        // Validate if this would move out of the parentcraft

                        HitBox subcraftHitbox = subcraft.getHitBox();
                        final int minX = subcraftHitbox.getMinX() + finalMovement.getBlockX();
                        final int minY = subcraftHitbox.getMinY() + finalMovement.getBlockY();
                        final int minZ = subcraftHitbox.getMinZ() + finalMovement.getBlockZ();

                        final int maxX = subcraftHitbox.getMaxX() + finalMovement.getBlockX();
                        final int maxY = subcraftHitbox.getMaxY() + finalMovement.getBlockY();
                        final int maxZ = subcraftHitbox.getMaxZ() + finalMovement.getBlockZ();

                        if (!(parent.getHitBox().inBounds(minX, minY, minZ) && parent.getHitBox().inBounds(maxX, maxY, maxZ))) {
                            movementAllowed = false;
                        }
                    }

                    if (movementAllowed || !subcraftType.get(PropertyKeys.RESTRICT_TO_MOVEBOX)) {
                        subcraft.translate(world, finalMovement.getBlockX(), finalMovement.getBlockY(), finalMovement.getBlockZ());
                    } else {
                        player.sendMessage(I18nSupport.getInternationalisedComponent("Subcraft Move - can't move out of the parentcraft!"));
                    }

                    (new BukkitRunnable() {
                        public void run() {
                            if (subcraft instanceof SubCraft) {
                                Craft parent = ((SubCraft) subcraft).getParent();
                                HitBox newHitbox = parent.getHitBox().union(subcraft.getHitBox());
                                parent.setHitBox(newHitbox);
                            }

                            CraftManager.getInstance().release(subcraft, net.countercraft.movecraft.events.CraftReleaseEvent.Reason.SUB_CRAFT, false);
                        }
                    }).runTaskLater(Movecraft.getInstance(), 3L);
                };
            });
        }

    }

    @Override
    protected boolean isActionAllowed(String s) {
        return s.equalsIgnoreCase(OPERATION);
    }

    @Override
    protected void onActionAlreadyInProgress(Entity player) {
        player.sendMessage(I18nSupport.getInternationalisedString("Movement - Already Moving"));
    }

    @Override
    protected boolean isSignValid(Action clickType, SignListener.SignWrapper sign, Entity player) {
        if (super.isSignValid(clickType, sign, player)) {
            TypeSafeCraftType craftType = this.getCraftType(sign);

            final Vector movement = this.getMovementVector(sign, craftType);
            if (clickType.isRightClick()) {
                movement.multiply(-1);
            }

            if (!craftType.get(PropertyKeys.ALLOW_HORIZONTAL_MOVEMENT) && (movement.getX() != 0 || movement.getZ() != 0)) {
                player.sendMessage(I18nSupport.getInternationalisedString("Crafttype does not support horizontal movement!"));
                return false;
            }

            if (!craftType.get(PropertyKeys.ALLOW_VERTICAL_MOVEMENT) && (movement.getY() != 0)) {
                player.sendMessage(I18nSupport.getInternationalisedString("Crafttype does not support vertical movement!"));
                return false;
            }

            return true;
        }
        return false;
    }

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
    protected boolean canPlayerUseSignForCraftType(Action action, SignListener.SignWrapper signWrapper, Entity player, TypeSafeCraftType craftType) {
        String craftTypeStr = craftType.getName().toLowerCase();
        if (craftType.get(PropertyKeys.REQUIRE_PERM_FOR_ASSEMBLY, player.getWorld()) && !player.hasPermission("movecraft." + craftTypeStr + ".move")) {
            player.sendMessage(I18nSupport.getInternationalisedString("Insufficient Permissions"));
            return false;
        } else if (!craftType.get(PropertyKeys.CAN_STATIC_MOVE)) {
            player.sendMessage(I18nSupport.getInternationalisedString("Specified craft type can not static move!"));
            return false;
        } else {
            final Vector movement = this.getMovementVector(signWrapper, craftType);
            if (action.isRightClick()) {
                movement.multiply(-1);
            }

            if (!craftType.get(PropertyKeys.ALLOW_HORIZONTAL_MOVEMENT) && (movement.getX() != 0 || movement.getZ() != 0)) {
                player.sendMessage(I18nSupport.getInternationalisedString("Crafttype does not support horizontal movement!"));
                return false;
            }

            if (!craftType.get(PropertyKeys.ALLOW_VERTICAL_MOVEMENT) && (movement.getY() != 0)) {
                player.sendMessage(I18nSupport.getInternationalisedString("Crafttype does not support vertical movement!"));
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean canPlayerUseSignOn(Entity player, @Nullable Craft craft) {
        if (!super.canPlayerUseSignOn(player, craft)) {
            return false;
        }
        int tickCooldown = craft.getCraftProperties().get(PropertyKeys.TICK_COOLDOWN, craft.getWorld());
        final TypeSafeCraftType craftType = craft.getCraftProperties();
        if (craftType.get(PropertyKeys.GEAR_SHIFT_AFFECT_DIRECT_MOVEMENT) && craftType.get(PropertyKeys.GEAR_SHIFT_AFFECT_TICK_COOLDOWN)) {
            tickCooldown *= craft.getCurrentGear();
        }
        //Long lastTime = Math.min(InteractListener.INTERACTION_TIME_MAP.get(craft.getUUID()), InteractListener.PLAYER_INTERACTION_TIME_MAP.get(player.getUniqueId()));
        Long lastTimePlayer = InteractListener.PLAYER_INTERACTION_TIME_MAP.get(player.getUniqueId());
        Long lastTimeCraft = InteractListener.INTERACTION_TIME_MAP.get(craft.getUUID());
        if (lastTimePlayer != null || lastTimeCraft != null) {
            Long lastTime = null;
            if (lastTimePlayer == null) {
                lastTime = lastTimeCraft;
            } else if (lastTimeCraft == null) {
                lastTime = lastTimePlayer;
            } else {
                lastTime = Math.min(lastTimeCraft, lastTimePlayer);
            }
            if (lastTime != null) {
                long ticksElapsed = (System.currentTimeMillis() - lastTime) / 50L;
                if (craft.getCraftProperties().get(PropertyKeys.HALF_SPEED_UNDERWATER) && craft.getHitBox().getMinY() < craft.getWorld().getSeaLevel()) {
                    ticksElapsed /= 2L;
                }

                if (ticksElapsed < (long) tickCooldown) {
                    return false;
                }
            }
        }

        return true;
    }

    @Override
    protected void onCraftIsBusy(Entity player, Craft craft) {
        player.sendMessage(I18nSupport.getInternationalisedString("Detection - Parent Craft is busy"));
    }

    @Override
    protected void onCraftNotFound(Entity player, SignListener.SignWrapper signWrapper) {

    }

}
