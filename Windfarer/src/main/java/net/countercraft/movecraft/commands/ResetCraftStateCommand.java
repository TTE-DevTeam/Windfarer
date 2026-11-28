package net.countercraft.movecraft.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.countercraft.movecraft.Movecraft;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.state.CraftStateTracker;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Set;

/**
 * Emergency command: releases all craft states (movement, shape changes, fuel burn, subcraft handling, ...) of the
 * selected craft(s), see {@link CraftStateTracker#forceReset()}. Meant for crafts which are stuck, for example
 * because a task did not release its state. Tasks which are still running do not notice the reset, their later release
 * of an already reset state is harmless. Resetting a craft in the middle of a movement may leave that movement unfinished.
 */
public class ResetCraftStateCommand extends AbstractCraftCommand {

    public ResetCraftStateCommand() {
        super("resetcraftstate", "movecraft.resetcraftstate", "Resets the states of craft(s) which are stuck", List.of());
    }

    @Override
    protected boolean specialArgsPredicate(CommandSourceStack sourceStack) {
        return sourceStack.getSender().hasPermission(this.permissionNode + ".others");
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] arguments() {
        return new ArgumentBuilder[]{};
    }

    @Override
    protected boolean requiresCheck(CommandSourceStack sourceStack) {
        return sourceStack.getSender().hasPermission(this.permissionNode);
    }

    @Override
    protected int processCommand(CommandContext<CommandSourceStack> context, Set<Craft> crafts) {
        if (crafts.isEmpty()) {
            context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("ResetCraftState - No Craft")));
            return -1;
        }

        for (Craft craft : crafts) {
            final CraftStateTracker tracker = craft.getStateTracker();
            // Read before, the reset clears it
            final String stateBefore = tracker.toString();
            final int released = tracker.forceReset();

            Movecraft.getInstance().getLogger().info(String.format(
                    "%s reset the states of craft %s (%d released): %s",
                    context.getSource().getSender().getName(), craft.getUUID(), released, stateBefore
            ));

            final String messageKey = released > 0 ? "ResetCraftState - Reset" : "ResetCraftState - Nothing To Reset";
            context.getSource().getSender().sendMessage(ChatUtils.commandPrefix().append(Component.text(String.format(
                    I18nSupport.getInternationalisedString(messageKey), craft.getUUID(), released
            ))));
        }
        return Command.SINGLE_SUCCESS;
    }

}
