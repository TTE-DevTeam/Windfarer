package net.countercraft.movecraft.features.contacts;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.commands.IBrigadierCommandHelper;
import net.countercraft.movecraft.commands.argument.type.CraftUUIDArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import org.bukkit.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class IgnoreContactCommand implements IBrigadierCommandHelper {

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("ignorecontact")
                        .requires(source -> {
                            if (source.getExecutor() == null || !(source.getExecutor() instanceof Entity)) {
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.ignore_contact");
                        })
                        .executes(
                                context -> {
                                    return process(context);
                                }
                        )
                        .then(Commands.argument("own-craft", new CraftUUIDArgumentType())
                                .executes(context -> {
                                    return process(context);
                                })
                                .then(Commands.argument("other-craft", new CraftUUIDArgumentType())
                                        .executes(context -> {
                                            return process(context);
                                        })
                                )
                        )
                        .build(),
                "Ignore a specific craft in contacts",
                List.of()
        );
    }

    static int process(final CommandContext<CommandSourceStack> context) {
        final Optional<Map<String, ParsedArgument>> optArgumentMap = IBrigadierCommandHelper.arguments(context);
        if (optArgumentMap.isEmpty()) {
            context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("At least a base craft and a ignore craft must be given!")));
        } else {
            final Map<String, ParsedArgument> argumentMap = optArgumentMap.get();
            final UUID ownCraftUUID = argumentMap.containsKey("own-craft") ? context.getArgument("own-craft", UUID.class) : null;
            final UUID otherCraftUUID = argumentMap.containsKey("other-craft") ? context.getArgument("other-craft", UUID.class) : null;
            if (ownCraftUUID == null || otherCraftUUID == null) {
                context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("At least a base craft and a ignore craft must be given!")));
            } else {
                final Craft ownCraft = Craft.getCraftByUUID(ownCraftUUID);
                final Craft otherCraft = Craft.getCraftByUUID(otherCraftUUID);

                if (ownCraft == null || otherCraft == null) {
                    context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("Argument 1 and 2 must be valid crafts!")));
                } else {
                    Craft executorCraft = CraftManager.getInstance().getCraftByEntity(context.getSource().getExecutor());
                    if (executorCraft == null || !executorCraft.getUUID().equals(ownCraft.getUUID())) {
                        context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("You cant modify the ignore list of a craft that is not yours!")));
                    } else {
                        ownCraft.getDataTag(ContactsManager.IGNORED_CRAFTS).add(otherCraft.getUUID());
                    }
                }
            }
        }
        return 0;
    }

}
