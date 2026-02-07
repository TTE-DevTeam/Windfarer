package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.MovecraftRotation;
import net.countercraft.movecraft.commands.argument.type.EnumArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

import java.util.List;
import java.util.Optional;

public class RotateCommand {

    enum ROTATE_OPTIONS {
        LEFT(Action.LEFT_CLICK_BLOCK),
        RIGHT(Action.RIGHT_CLICK_BLOCK),
        ;

        private Action clickType;

        ROTATE_OPTIONS(Action clickType) {
            this.clickType = clickType;
        }

        public Action clickType() {
            return this.clickType;
        }
    }

    // TODO: Fail messages
    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("rotate")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.rotate");
                        })
                        .executes(
                                context -> {
                                    process((Player) (context.getSource().getSender()));
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                }
                        )
                        .then(Commands.argument("rotation", EnumArgumentType.ofEnum(ROTATE_OPTIONS.class))
                                .executes(context -> {
                                    ROTATE_OPTIONS rotation = context.getArgument("rotation", ROTATE_OPTIONS.class);
                                    if (rotation == null) {
                                        process((Player) (context.getSource().getExecutor()));
                                    } else {
                                        process((Player) (context.getSource().getExecutor()), rotation);
                                    }
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                        .build(),
                "Command to rotate your craft",
                List.of()
        );
    }

    static void process (Player sender, ROTATE_OPTIONS rotation) {
        final Optional<Craft> optCraft = CraftManager.getInstance().getCraftsInWorld(sender.getWorld())
                .stream()
                .filter(craftTmp -> {
                    return craftTmp instanceof PlayerCraft pc && pc.getPilotUUID() != null && pc.getPilotUUID().equals(sender.getUniqueId());
                })
                .findFirst();
        if (optCraft.isPresent()) {
            final Craft craft = optCraft.get();
            /*if (!craft.getCraftProperties().get(PropertyKeys.CAN_CRUISE)) {
                sender.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Cruise - Craft Cannot Rotate"));
            }
            else*/ if (!sender.hasPermission("movecraft." + craft.getCraftProperties().getName().toLowerCase() + ".rotate")) {
                sender.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Insufficient Permissions"));
            }
            else if (rotation == null) {
                rotation = ROTATE_OPTIONS.RIGHT;
            }
            craft.rotate(MovecraftRotation.CLOCKWISE, craft.getHitBox().getMidPoint());
        } else {
            sender.sendMessage(ChatUtils.MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("You must be piloting a craft"));
        }
    }

    static void process(Player sender) {
        process(sender, ROTATE_OPTIONS.RIGHT);
    }

}
