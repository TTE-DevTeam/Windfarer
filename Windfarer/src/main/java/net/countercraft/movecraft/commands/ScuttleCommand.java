package net.countercraft.movecraft.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.events.CraftScuttleEvent;
import net.countercraft.movecraft.events.CraftSinkEvent;
import net.countercraft.movecraft.events.CraftStopCruiseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.Set;

public class ScuttleCommand extends AbstractCraftCommand {

    public ScuttleCommand() {
        super("scuttle", "movecraft.scuttle", "Sinks piloted craft", List.of());
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
        int scuttled = 0;
        for (Craft craft : crafts) {
            if (!context.getSource().getSender().hasPermission("movecraft." + craft.getCraftProperties().getName().toLowerCase() + ".scuttle")) {
                context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("Insufficient Permissions")));
            } else {
                if (craft instanceof SinkingCraft) {
                    context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("Scuttle - Craft Already Sinking")));
                } else {
                    CraftScuttleEvent e = new CraftScuttleEvent(craft, context.getSource().getExecutor());
                    Bukkit.getServer().getPluginManager().callEvent(e);
                    if (e.isCancelled())
                        continue;

                    craft.setCruising(false, CraftStopCruiseEvent.Reason.CRAFT_SUNK);
                    CraftManager.getInstance().sink(craft, CraftSinkEvent.SIMPLE_SINK_REASONS.SCUTTLE);
                    context.getSource().getSender().sendMessage(ChatUtils.errorPrefix().append(I18nSupport.getInternationalisedComponent("Scuttle - Scuttle Activated")));
                    scuttled++;
                }
            }
        }
        return scuttled > 0 ? Command.SINGLE_SUCCESS : -1;
    }

}

