package net.countercraft.movecraft.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import net.countercraft.movecraft.craft.Craft;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Set;

public class TeleportCraftCommand extends AbstractCraftCommand {

    protected TeleportCraftCommand() {
        super("teleportcraft", "movecraft.commands.teleportcraft", "Command to rotate your craft", List.of("tpc", "tpcraft"));
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> arguments() {
        return Commands.argument("destination-world", ArgumentTypes.world())
                .then(
                        Commands.argument("destination-position", ArgumentTypes.blockPosition())
                );
    }

    @Override
    protected int processCommand(CommandContext<CommandSourceStack> context, Set<Craft> craft) {
        throw new NotImplementedException();
    }
}
