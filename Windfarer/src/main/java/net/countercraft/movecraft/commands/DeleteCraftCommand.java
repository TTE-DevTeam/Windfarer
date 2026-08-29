package net.countercraft.movecraft.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.processing.WorldManager;
import net.countercraft.movecraft.processing.tasks.DeleteCraftTask;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public class DeleteCraftCommand extends AbstractCraftCommand {
    public DeleteCraftCommand() {
        super("deletecraft", "movecraft.commands.deletecraft", "Command to release a craft and to remove all of it's blocks", List.of("dlc"));
    }

    @Override
    protected @Nullable ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] arguments() {
        return new ArgumentBuilder[0];
    }

    @Override
    protected int processCommand(CommandContext<CommandSourceStack> context, Set<Craft> crafts) {
        for (Craft craft : crafts) {
            final DeleteCraftTask deleteCraftTask = new DeleteCraftTask(craft.getHitBox(), craft.getWorld());
            if (CraftManager.getInstance().tryRelease(craft, CraftReleaseEvent.Reason.SUNK, true)) {
                WorldManager.INSTANCE.submit(deleteCraftTask);
            }
        }
        return Command.SINGLE_SUCCESS;
    }
}
