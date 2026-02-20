package net.countercraft.movecraft.commands;

import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.commands.argument.type.CraftTypeArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.PlayerCraftImpl;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.events.CraftReleaseEvent;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.processing.functions.Result;
import net.countercraft.movecraft.util.MathUtils;
import net.countercraft.movecraft.util.Pair;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

import static net.countercraft.movecraft.util.ChatUtils.MOVECRAFT_COMMAND_PREFIX;

public class PilotCommand {

    public static void register(final Commands commands) {
        commands.register(
                Commands.literal("pilot")
                        .requires(source -> {
                            if (!(source.getExecutor() instanceof Entity)) {
                                source.getSender().sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Pilot - Must Be Entity"));
                                return false;
                            }
                            if (!source.getSender().hasPermission("movecraft.commands")) {
                                return false;
                            }
                            return source.getSender().hasPermission("movecraft.commands.pilot");
                        })
                        .executes(context -> {
                            context.getSource().getSender().sendMessage(MOVECRAFT_COMMAND_PREFIX + I18nSupport.getInternationalisedString("Pilot - No Craft Type"));
                            return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("type", new CraftTypeArgumentType())
                                .executes(context -> {
                                    TypeSafeCraftType type = context.getArgument("type", TypeSafeCraftType.class);
                                    process(context.getSource().getExecutor(), type);
                                    return com.mojang.brigadier.Command.SINGLE_SUCCESS;
                                })
                        )
                        .build(),
                "Command to change the cruise state of your craft",
                List.of()
        );
    }

    static void process(Entity executor, final TypeSafeCraftType craftType) {
        final World world = executor.getWorld();
        MovecraftLocation startPoint = MathUtils.bukkit2MovecraftLoc(executor.getLocation());

        CraftManager.getInstance().detect(
                startPoint,
                craftType, (type, w, p, parents) -> {
                    assert p != null; // Note: This only passes in a non-null player.
                    if (parents.size() > 0)
                        return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedString(
                                "Detection - Failed - Already commanding a craft")), null);

                    if (p instanceof Player player) {
                        return new Pair<>(Result.succeed(),
                                new PlayerCraftImpl(type, w, player));
                    } else {
                        //return new Pair<>(Result.succeed(), new PilotedCraftImpl(type, w, p));
                        return new Pair<>(Result.failWithMessage(I18nSupport.getInternationalisedString(
                                "Detection - Failed - Pilot must be player")), null);
                    }
                },
                world,
                // Pilot
                executor,
                // Audience
                executor,
                craft -> () -> {
                    if (executor instanceof Player player) {
                        // Release old craft if it exists
                        Craft oldCraft = CraftManager.getInstance().getCraftByPlayer(player);
                        if(oldCraft != null)
                            CraftManager.getInstance().release(oldCraft, CraftReleaseEvent.Reason.PLAYER, false);
                    }
                }
        );
    }

}
