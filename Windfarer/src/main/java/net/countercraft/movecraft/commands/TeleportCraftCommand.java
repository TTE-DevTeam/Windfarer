package net.countercraft.movecraft.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.async.translation.TranslationTask;
import net.countercraft.movecraft.craft.Craft;
import org.bukkit.World;

import java.util.*;

public class TeleportCraftCommand extends AbstractCraftCommand {

    public TeleportCraftCommand() {
        super("teleportcraft", "movecraft.commands.teleportcraft", "Command to rotate your craft", List.of("tpc", "tpcraft"));
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] arguments() {
        return new ArgumentBuilder[]{
                Commands.argument("destination-world", ArgumentTypes.world()),
                Commands.argument("destination-position", ArgumentTypes.blockPosition())
        };
    }

    @Override
    protected int processCommand(CommandContext<CommandSourceStack> context, Set<Craft> crafts) {
        final World world = context.getArgument("destination-world", World.class);
        // DONE: FixMe: Use BlockPositionResolver, not the BlockPosition interface

        try {
            final BlockPositionResolver blockPositionResolver = context.getArgument("destination-position", BlockPositionResolver.class);
            final BlockPosition blockPosition = blockPositionResolver.resolve((CommandSourceStack) context.getSource());
            final MovecraftLocation pos = new MovecraftLocation(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());

            for (Craft craft : crafts) {
                final MovecraftLocation delta = pos.subtract(craft.getCraftOrigin());
                TranslationTask translationTask = new TranslationTask(craft, world, delta.getX(), delta.getY(), delta.getZ());
                Movecraft.getInstance().getAsyncManager().submitTask(translationTask, craft);
            }
        } catch(CommandSyntaxException cse) {
            context.getSource().getSender().sendMessage(cse.getMessage());
            return -1;
        }

        return Command.SINGLE_SUCCESS;
    }
}
