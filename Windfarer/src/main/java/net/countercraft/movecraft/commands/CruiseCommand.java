package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.commands.argument.type.EnumArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.sign.AbstractToggleSign;
import net.countercraft.movecraft.sign.CraftSignManager;
import net.countercraft.movecraft.sign.CruiseSign;
import net.countercraft.movecraft.util.ChatUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class CruiseCommand {

    enum CRUISE_DIRECTION {
        ON,
        OFF,
        UP,
        DOWN,
        NORTH,
        EAST,
        SOUTH,
        WEST
    }

    // TODO: Fail messages
    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("cruise")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.cruise");
                        })
                        .executes(
                                context -> {
                                    process(context.getSource().getExecutor(), context.getSource().getSender());
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                }
                        )
                        .then(Commands.argument("direction", EnumArgumentType.ofEnum(CRUISE_DIRECTION.class))
                                .executes(context -> {
                                    CRUISE_DIRECTION direction = context.getArgument("direction", CRUISE_DIRECTION.class);
                                    if (direction == null) {
                                        process(context.getSource().getExecutor(), context.getSource().getSender());
                                    } else {
                                        process(context.getSource().getExecutor(), context.getSource().getSender(), direction);
                                    }
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                        .build(),
                "Command to change the cruise state of your craft",
                List.of()
        );
    }

    static void process(Entity executor, CommandSender commandSender, CRUISE_DIRECTION direction) {
        if (executor == null) {
            return;
        }
        final Optional<Craft> optCraft = CraftManager.getInstance().getCraftsInWorld(executor.getWorld())
                .stream()
                .filter(craftTmp -> {
                    if (executor instanceof Player) {
                        return craftTmp instanceof PlayerCraft pc && pc.getPilotUUID() != null && pc.getPilotUUID().equals(executor.getUniqueId());
                    } else {
                        return craftTmp instanceof PilotedCraft pc && pc.getPilotUUID() != null && pc.getPilotUUID().equals(executor.getUniqueId());
                    }
                })
                .findFirst();
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            if (!craft.getCraftProperties().get(PropertyKeys.CAN_CRUISE)) {
                executor.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Cruise - Craft Cannot Cruise"));
            }
            else if (!commandSender.hasPermission("movecraft." + craft.getCraftProperties().getName().toLowerCase() + ".move")) {
                executor.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            }
            else if (direction == null) {
                if (craft.getCruising()) {
                    craft.setCruising(false, CraftStopCruiseEvent.Reason.COMMAND);
                } else {
                    float yaw = (executor.getLocation().getYaw() + 360.0f);
                    if (yaw >= 360.0f) {
                        yaw %= 360.0f;
                    }
                    if (yaw >= 45 && yaw < 135) { // west
                        craft.setCruiseDirection(CruiseDirection.WEST);
                    } else if (yaw >= 135 && yaw < 225) { // north
                        craft.setCruiseDirection(CruiseDirection.NORTH);
                    } else if (yaw >= 225 && yaw <= 315){ // east
                        craft.setCruiseDirection(CruiseDirection.EAST);
                    } else { // default south
                        craft.setCruiseDirection(CruiseDirection.SOUTH);
                    }
                    craft.setCruising(true);
                }
            } else {
                switch(direction) {
                    case OFF:
                        break;
                    case DOWN:
                        craft.setCruiseDirection(CruiseDirection.DOWN);
                        break;
                    case UP:
                        craft.setCruiseDirection(CruiseDirection.UP);
                        break;
                    case NORTH:
                        craft.setCruiseDirection(CruiseDirection.NORTH);
                        break;
                    case EAST:
                        craft.setCruiseDirection(CruiseDirection.EAST);
                        break;
                    case SOUTH:craft.setCruiseDirection(CruiseDirection.SOUTH);
                        break;
                    case WEST:
                        craft.setCruiseDirection(CruiseDirection.WEST);
                        break;
                    default:
                        float yaw = (executor.getLocation().getYaw() + 360.0f);
                        if (yaw >= 360.0f) {
                            yaw %= 360.0f;
                        }
                        if (yaw >= 45 && yaw < 135) { // west
                            craft.setCruiseDirection(CruiseDirection.WEST);
                        } else if (yaw >= 135 && yaw < 225) { // north
                            craft.setCruiseDirection(CruiseDirection.NORTH);
                        } else if (yaw >= 225 && yaw <= 315){ // east
                            craft.setCruiseDirection(CruiseDirection.EAST);
                        } else { // default south
                            craft.setCruiseDirection(CruiseDirection.SOUTH);
                        }
                        break;
                }
                craft.setCruising(direction != CRUISE_DIRECTION.OFF, CraftStopCruiseEvent.Reason.COMMAND);
            }
            // TODO: Toggle sign updates via SignManager
        } else {
            executor.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft"));
        }
    }

    static void process(Entity sender, CommandSender commandSender) {
        process(sender, commandSender, null);
    }

}
