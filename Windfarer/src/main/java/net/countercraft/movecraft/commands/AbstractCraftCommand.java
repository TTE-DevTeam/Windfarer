package net.countercraft.movecraft.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.EntitySelectorArgumentResolver;
import io.papermc.paper.math.BlockPosition;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.commands.argument.type.CraftUUIDArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.SubCraftImpl;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.World;
import org.bukkit.entity.Entity;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public abstract class AbstractCraftCommand {

    protected final String permissionNode;
    protected final String description;
    protected final Collection<String> aliasList;

    protected AbstractCraftCommand(String permissionNode, String description, Collection<String> aliasList) {
        this.permissionNode = permissionNode;
        this.description = description;
        this.aliasList = aliasList;
    }

    protected boolean requiresCheck(final CommandSourceStack sourceStack) {
        if (!sourceStack.getSender().hasPermission("movecraft.commands")) {
            return false;
        }
        return sourceStack.getSender().hasPermission(this.permissionNode);
    }

    public void register(final Commands commands) {
        commands.register(
                Commands.literal("teleportcraft")
                        .requires(this::requiresCheck)
                        // By Pilot entity
                        .then(
                                this.processRest(
                                    Commands.literal("--pilot")
                                        .then(Commands.argument("pilot", ArgumentTypes.entity())),
                                    this::getCraftByPilot
                                )
                        )
                        // By Craft UUID
                        .then(
                                this.processRest(
                                    Commands.literal("--uuid")
                                        .then(Commands.argument("uuid", new CraftUUIDArgumentType())),
                                    this::getByCraftUUID
                                )
                        )
                        // By Craft name
                        .then(
                                this.processRest(
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
                                        ),
                                    this::getCraftByName
                                )
                        )
                        // By position
                        .then(
                                this.processRest(
                                    Commands.literal("--position")
                                        .then(Commands.argument("positionWorld", ArgumentTypes.world()))
                                        .then(Commands.argument("position", ArgumentTypes.blockPosition())),
                                    this::getCraftByPosition
                                )
                        )
                        // TODO: Add logic for craft-by-executor (aka no arg given) logic!
                        .build(),
                this.description,
                this.aliasList
        );
    }

    protected abstract @Nullable RequiredArgumentBuilder<CommandSourceStack, ?> additionalArguments();
    protected abstract int processCommand(final CommandContext context, final Craft craft);

    protected ArgumentBuilder<CommandSourceStack, ?> processRest(final LiteralArgumentBuilder<CommandSourceStack> literal, final Function<CommandContext, Craft> craftSupplier) {
        RequiredArgumentBuilder<CommandSourceStack, ?> addArgument = this.additionalArguments();
        final Function<Command<CommandSourceStack>, ArgumentBuilder<CommandSourceStack, ?>> actualCommand;
        if (addArgument == null) {
            actualCommand = literal::executes;
        } else {
            actualCommand = addArgument::executes;
        }
        return actualCommand.apply(context -> {
            final Craft craft = craftSupplier.apply(context);
            return processCommand(context, craft);
        });
    }

    protected Craft getCraftByExecutor(CommandContext context) {
        return null;
    }

    protected Craft getCraftByPilot(CommandContext context) {
        final EntitySelectorArgumentResolver entitySelectorArgumentResolver = (EntitySelectorArgumentResolver) context.getArgument("pilot", EntitySelectorArgumentResolver.class);
        try {
            final List<Entity> entities = entitySelectorArgumentResolver.resolve((CommandSourceStack) context.getSource());

            if (entities.isEmpty()) {
                return null;
            }

            Entity entityToUse = entities.getFirst();
            return CraftManager.getInstance().getCraftByEntity(entityToUse);
        } catch(CommandSyntaxException cse) {
            ((CommandSourceStack) context.getSource()).getSender().sendMessage(cse.getMessage());
            return null;
        }
    }
    protected Craft getByCraftUUID(CommandContext context) {
        final UUID uuid = (UUID) context.getArgument("uuid", UUID.class);
        if (uuid == null) {
            return null;
        }
        return Craft.getCraftByUUID(uuid);
    }
    protected Craft getCraftByName(CommandContext context) {
        final String name = (String) context.getArgument("name", String.class);
        if (name == null) {
            return null;
        }
        for (Craft craft : CraftManager.getInstance().getCrafts()) {
            String craftName = craft.getNameRaw();
            if (craftName == null) {
                continue;
            }
            if (name.equalsIgnoreCase(craftName)) {
                return craft;
            }
        }
        return null;
    }
    protected Craft getCraftByPosition(CommandContext context) {
        final World world = (World) context.getArgument("positionWorld", World.class);
        final BlockPositionResolver blockPositionResolver = (BlockPositionResolver) context.getArgument("position", BlockPositionResolver.class);

        if (world == null || blockPositionResolver == null) {
            return null;
        }

        try {
            final BlockPosition blockPosition = blockPositionResolver.resolve((CommandSourceStack) context.getSource());
            final MovecraftLocation pos = new MovecraftLocation(blockPosition.blockX(), blockPosition.blockY(), blockPosition.blockZ());

            final List<Craft> craftsWithPos = new ArrayList<>();
            for (Craft craft : CraftManager.getInstance().getCraftsInWorld(world)) {
                final HitBox hitBox = craft.getHitBox();
                if (hitBox.inBounds(pos)) {
                    if (hitBox.contains(pos)) {
                        craftsWithPos.add(craft);
                    }
                }
            }
            if (!craftsWithPos.isEmpty()) {
                if (craftsWithPos.size() > 1) {
                    // Sort list by distance to position => ASCENDING!
                    craftsWithPos.sort(new Comparator<Craft>() {
                        @Override
                        public int compare(Craft o1, Craft o2) {
                            final int distCraft1 = o1.getHitBox().getMidPoint().distanceSquared(pos);
                            final int distCraft2 = o2.getHitBox().getMidPoint().distanceSquared(pos);
                            return Integer.compare(distCraft1, distCraft2);
                        }
                    });
                }
                return craftsWithPos.getFirst();
            }
        } catch(CommandSyntaxException cse) {
            ((CommandSourceStack) context.getSource()).getSender().sendMessage(cse.getMessage());
        }
        return null;
    }

}
