package net.countercraft.movecraft.commands;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.commands.argument.type.CraftTypeArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import net.countercraft.movecraft.util.ComponentPaginator;
import net.countercraft.movecraft.util.ICraftReportInfoProvider;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import java.lang.reflect.Field;
import java.util.*;

public class CraftReportCommand {

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("craftreport")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.craftreport");
                        })
                        .executes(
                                context -> {
                                    return process(context);
                                }
                        )
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    return process(context);
                                })
                                .then(Commands.argument("reportSunk", BoolArgumentType.bool())
                                        .executes(context -> {
                                            return process(context);
                                        })
                                        .then(Commands.argument("reportDisabled", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    return process(context);
                                                })
                                                .then(Commands.argument("reportNormal", BoolArgumentType.bool())
                                                        .executes(context -> {
                                                            return process(context);
                                                        })
                                                        .then(Commands.argument("typeFilter", new CraftTypeArgumentType())
                                                                .executes(context -> {
                                                                    return process(context);
                                                                })
                                                                .then(Commands.argument("world", ArgumentTypes.world())
                                                                        .executes(context -> {
                                                                            return process(context);
                                                                        })
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                        .build(),
                "Prints out info about currently existing crafts",
                List.of()
        );
    }

    static final Field ARGUMENT_FIELD;

    static {
        try {
            ARGUMENT_FIELD = CommandContext.class.getDeclaredField("arguments");
            ARGUMENT_FIELD.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO: Janky, find a better solution!
    static int process(final CommandContext<CommandSourceStack> context) {
        Map<String, ParsedArgument> arguments;
        try {
            arguments = (Map<String, ParsedArgument>) ARGUMENT_FIELD.get(context);
        } catch(Exception ex) {
            ex.printStackTrace();
            return -1;
        }

        int page = 1;
        boolean reportSunk = true;
        boolean reportDisabled = true;
        boolean reportNormal = true;
        World world = null;
        TypeSafeCraftType type = null;

        if (arguments.containsKey("reportSunk")) {
            reportSunk = context.getArgument("reportSunk", Boolean.class);
        }
        if (arguments.containsKey("reportDisabled")) {
            reportDisabled = context.getArgument("reportDisabled", Boolean.class);
        }
        if (arguments.containsKey("reportNormal")) {
            reportNormal = context.getArgument("reportNormal", Boolean.class);
        }
        if (arguments.containsKey("world")) {
            world = context.getArgument("world", World.class);
        }
        if (arguments.containsKey("typeFilter")) {
            type = context.getArgument("typeFilter", TypeSafeCraftType.class);
        }
        if (arguments.containsKey("page")) {
            page = context.getArgument("page", Integer.class);
        }

        final int fPage = page;
        final boolean fReportSunk = reportSunk;
        final boolean fReportDisabled = reportDisabled;
        final boolean fReportNormal = reportNormal;
        final World fWorld = world;
        final TypeSafeCraftType fType = type;

        ComponentPaginator paginator = new ComponentPaginator(
                I18nSupport.getInternationalisedComponent("Craft Report"),
                // TODO: Figure out a way to not use the objects here but still use their auto complete functionality!
                (pageNumber) -> {
                    String result = "/craftreport " + pageNumber + " " + fReportSunk + " " + fReportDisabled + " " + fReportNormal;
                    if (fType != null) {
                        result += " " + fType.getName();
                        if (fWorld != null) {
                            result += " " + fWorld.getName();
                        }
                    }
                    return result;
                }
        );

        // TODO: Add event to add more crafts to this set
        Set<Craft> craftSet = new HashSet<>(CraftManager.getInstance().getCrafts());
        // Remove the ones we dont want to report!
        craftSet.removeIf(craftTmp -> {
            if (!fReportSunk && craftTmp instanceof SinkingCraft) {
                return true;
            }
            if (!fReportDisabled && craftTmp.getDisabled()) {
                return true;
            }
            if (!fReportNormal && !((craftTmp instanceof SinkingCraft) || craftTmp.getDisabled())) {
                return true;
            }
            if (fWorld != null) {
                return !craftTmp.getWorld().getUID().equals(fWorld.getUID());
            }
            if (fType != null) {
                return !craftTmp.getCraftProperties().getName().equals(fType.getName());
            }
            return false;
        });

        for (Craft craft : craftSet) {
            if (craft instanceof ICraftReportInfoProvider icrip) {
                // Allow customization
                icrip.getReportInfo().forEach(paginator::addLine);
            } else {
                // TODO: pack some more information into the message by using the "show text" hover event, one could include the flyblocks, moveblocks and so on here!
                // Plain old logic
                HitBox hitBox = craft.getHitBox();
                Component line = Component.empty();
                Component name = craft.getName();
                if (craft instanceof SinkingCraft)
                    name = name.color(NamedTextColor.RED);
                else if (craft.getDisabled())
                    name = name.color(NamedTextColor.BLUE);
                line = line.append(name).append(Component.text(" "));
                if (craft instanceof PilotedCraft pilotedCraft)
                    // DONE: Use player object type next to name (https://minecraft.wiki/w/Text_component_format#Player_Object_Type)
                    line = line.append(Movecraft.getInstance().getNMSHelper().getEntityReferencingComponent(pilotedCraft.getPilotEntity(), pilotedCraft.getPilotUUID()));
                else
                    line = line.append(I18nSupport.getInternationalisedComponent("None"));
                line = line.append(Component.text(" "));
                line = line
                        .append(Component.text(hitBox.size()))
                        .append(Component.text(" @ "))
                        .append(Component.text(hitBox.getMinX()))
                        .append(Component.text(","))
                        .append(Component.text(hitBox.getMinY()))
                        .append(Component.text(","))
                        .append(Component.text(hitBox.getMinZ()))
                        .append(Component.text(" in "))
                        .append(Component.text(craft.getWorld().getName()))
                        .append(Component.text(" - "))
                        .append(Component.text(String.format("%.2f", 1000 * craft.getMeanCruiseTime())))
                        .append(Component.text("ms"));
                paginator.addLine(line);
            }
        }

        if (paginator.getPageCount() == 0) {
            context.getSource().getSender().sendMessage(Component.empty()
                    .append(ChatUtils.commandPrefix())
                    .append(I18nSupport.getInternationalisedComponent("Craftreport - no active crafts"))
                    .append(Component.text(" \""))
                    .append(Component.text("" + page))
                    .append(Component.text("\"")));
            return 1;
        }
        else if (!paginator.isInBounds(page)) {
            context.getSource().getSender().sendMessage(Component.empty()
                    .append(ChatUtils.commandPrefix())
                    .append(I18nSupport.getInternationalisedComponent("Paginator - Invalid page"))
                    .append(Component.text(" \""))
                    .append(Component.text("" + page))
                    .append(Component.text("\"")));
            return 1;
        }
        for (Component line : paginator.getPage(page))
            context.getSource().getSender().sendMessage(line);

        return 1;
    }

}
