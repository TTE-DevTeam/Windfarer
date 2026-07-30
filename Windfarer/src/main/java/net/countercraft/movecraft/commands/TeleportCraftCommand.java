package net.countercraft.movecraft.commands;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.countercraft.movecraft.craft.Craft;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TeleportCraftCommand extends AbstractCraftCommand {

    protected TeleportCraftCommand() {
        super("teleportcraft", "movecraft.commands.teleportcraft", "Command to rotate your craft", List.of("tpc", "tpcraft"));
    }

    @Nullable
    @Override
    protected RequiredArgumentBuilder<CommandSourceStack, ?> arguments() {
        return Commands.argument("destination-world", ArgumentTypes.world())
                .then(
                        Commands.argument("destination-position", ArgumentTypes.blockPosition())
                );
    }

    @Override
    protected int processCommand(CommandContext context, Craft craft) {
        throw new NotImplementedException();
    }
}
