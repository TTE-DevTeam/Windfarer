package net.countercraft.movecraft.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ChatUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// TODO: Find a possibility to add different messages again for what type of crafts was released
public class ReleaseCommand extends AbstractCraftCommand {

    public ReleaseCommand() {
        super("release", "movecraft.commands.release", "Utility command to release craft(s)", List.of());
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ?> attachCraftSelectorTree(ArgumentBuilder<CommandSourceStack, ?> literal) {
        return super.attachCraftSelectorTree(literal)
                // All players
                .then(
                        this.processRest(
                                Commands.literal("--all-players")
                                        .requires(this::specialArgsPredicate),
                                this::getAllPlayerCrafts
                        )
                )
                // All crafts
                .then(
                        this.processRest(
                                Commands.literal("--all")
                                        .requires(this::specialArgsPredicate),
                                this::getAllCrafts
                        )
                )
                // Null piloted
                .then(
                        this.processRest(
                                Commands.literal("--null-piloted")
                                        .requires(this::specialArgsPredicate),
                                this::getAllNullCrafts
                        )
                );
    }

    @Override
    protected boolean specialArgsPredicate(CommandSourceStack sourceStack) {
        return sourceStack.getSender().hasPermission("movecraft.commands.release.others");
    }

    private Set<Craft> getAllNullCrafts(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        Set<Craft> nullCrafts = new HashSet<>();
        final List<Craft> craftsToRelease = new ArrayList<>(CraftManager.getInstance().getCrafts());
        for (Craft craft : craftsToRelease) {
            if (!(craft instanceof PilotedCraft))
                nullCrafts.add(craft);
        }
        return nullCrafts;
    }

    private Set<Craft> getAllCrafts(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        return new HashSet<>(CraftManager.getInstance().getCrafts());
    }

    private Set<Craft> getAllPlayerCrafts(CommandContext<CommandSourceStack> commandSourceStackCommandContext) {
        return new HashSet<>(CraftManager.getInstance().getPlayerCrafts());
    }

    @Override
    protected @Nullable RequiredArgumentBuilder<CommandSourceStack, ?> arguments() {
        // No longer necessary
        return null;
    }

    @Override
    protected int processCommand(CommandContext<CommandSourceStack> context, Set<Craft> crafts) {
        if (crafts == null || crafts.isEmpty()) {
            for (Craft craft : crafts) {
                CraftManager.getInstance().release(craft, CraftReleaseEvent.Reason.FORCE, false);
            }
            context.getSource().getSender().sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Release - Released Crafts")));
        } else {
            context.getSource().getSender().sendMessage(ChatUtils.commandPrefix().append(I18nSupport.getInternationalisedComponent("Release - No Crafts To Release")));
        }
        return 0;
    }
}

