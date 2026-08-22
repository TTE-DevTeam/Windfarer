package net.countercraft.movecraft.features.contacts;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.commands.IBrigadierCommandHelper;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.PlayerCraft;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import net.countercraft.movecraft.util.ComponentPaginator;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ContactsCommand {

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("contacts")
                        .requires(source -> {
                            if (source.getExecutor() == null || !(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.contacts");
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
                        )
                        .build(),
                "Prints out your craft's contacts",
                List.of()
        );
    }



    // TODO: Janky, find a better solution!
    static int process(final CommandContext<CommandSourceStack> context) {
        Optional<Map<String, ParsedArgument>> optArguments = IBrigadierCommandHelper.arguments(context);
        if (optArguments.isEmpty()) {
            return -1;
        }
        final Map<String, ParsedArgument> arguments = optArguments.get();

        int page = 1;

        if (arguments.containsKey("page")) {
            page = context.getArgument("page", Integer.class);
            page = Math.abs(page);
            if (page < 0) {
                page = 1;
            }
        }

        final Entity executor = context.getSource().getExecutor();

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
        if (optCraft.isEmpty()) {
            context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("You must be piloting a craft")));
            return 0;
        } else {
            final Craft base = optCraft.get();
            ComponentPaginator paginator = new ComponentPaginator(
                    I18nSupport.getInternationalisedComponent("Contacts"),
                    (pageNumber) -> "/contacts " + pageNumber);
            for (UUID target : base.getDataTag(Craft.CONTACTS)) {
                Craft targetCraft = Craft.getCraftByUUID(target);
                if (targetCraft == null) {
                    continue;
                }
                if (targetCraft.getHitBox().isEmpty())
                    continue;

                Component notification = ContactsManager.contactMessage(false, base, targetCraft);
                paginator.addLine(notification);
            }
            if (paginator.isEmpty()) {
                context.getSource().getSender().sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Contacts - None Found")));
                return 0;
            }
            if (!paginator.isInBounds(page)){
                context.getSource().getSender().sendMessage(Component.empty()
                        .append(ChatUtils.commandPrefix())
                        .append(I18nSupport.getInternationalisedComponent("Paginator - Invalid page"))
                        .append(Component.text(" \""))
                        .append(Component.text(page))
                        .append(Component.text("\"")));
                return 0;
            }
            for (Component line : paginator.getPage(page)) {
                context.getSource().getSender().sendMessage(line);
            }
            return 0;
        }
    }

}
