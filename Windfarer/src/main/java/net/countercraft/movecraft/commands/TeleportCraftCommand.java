package net.countercraft.movecraft.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.countercraft.movecraft.commands.argument.type.CraftUUIDArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.SubCraftImpl;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.NotImplementedException;
import org.bukkit.World;

import java.util.List;
import java.util.function.Function;

public class TeleportCraftCommand {

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("teleportcraft")
                        .requires(source -> {
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.teleportcraft");
                        })
                        // By Pilot entity
                        .then(
                                Commands.literal("--pilot")
                                        .then(Commands.argument("pilot", ArgumentTypes.entity()))
                                        .then(destination(TeleportCraftCommand::processByPilot))
                        )
                        // By Craft UUID
                        .then(
                                Commands.literal("--uuid")
                                        .then(Commands.argument("uuid", new CraftUUIDArgumentType()))
                                        .then(destination(TeleportCraftCommand::processByCraftUUID))
                        )
                        // By Craft name
                        .then(
                                Commands.literal("--name")
                                        .then(Commands.argument("name", StringArgumentType.string())
                                                .suggests(
                                                        (provider, builder) -> {
                                                            for (Craft craft : CraftManager.getInstance().getCrafts()) {
                                                                if (craft instanceof SinkingCraft)
                                                                    continue;
                                                                if (craft instanceof SubCraftImpl)
                                                                    continue;
                                                                if (craft.getName() == null)
                                                                    continue;
                                                                String nameStr = PlainTextComponentSerializer.plainText().serialize(craft.getName());
                                                                if (nameStr.isEmpty() || nameStr.isBlank())
                                                                    continue;
                                                                if (nameStr.indexOf(' ') >= 0) {
                                                                    nameStr = '"' + nameStr + '"';
                                                                }
                                                                builder.suggest(nameStr);
                                                            }
                                                            return builder.buildFuture();
                                                        }
                                                )
                                        )
                                        .then(destination(TeleportCraftCommand::processByCraftName))
                        )
                        // By position
                        .then(
                                Commands.literal("--position")
                                        .then(Commands.argument("positionWorld", ArgumentTypes.world()))
                                        .then(Commands.argument("position", ArgumentTypes.blockPosition()))
                                        .then(destination(TeleportCraftCommand::processByCraftPosition))
                        )
                        .build(),
                "Command to rotate your craft",
                List.of("tpc", "tpcraft")
        );
    }

    protected static RequiredArgumentBuilder<CommandSourceStack, World> destination(final Function<CommandContext, Integer> execute) {
        return Commands.argument("destination-world", ArgumentTypes.world())
                .then(
                        Commands.argument("destination-position", ArgumentTypes.blockPosition())
                )
                .executes(
                        context -> {
                            return execute.apply(context);
                        }
                );
    }

    static int processByPilot(CommandContext context) {
        throw new NotImplementedException();
    }

    static int processByCraftUUID(CommandContext context) {
        throw new NotImplementedException();
    }

    static int processByCraftName(CommandContext context) {
        throw new NotImplementedException();
    }

    static int processByCraftPosition(CommandContext context) {
        throw new NotImplementedException();
    }

}
