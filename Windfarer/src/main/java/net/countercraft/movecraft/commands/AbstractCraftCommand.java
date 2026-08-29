package net.countercraft.movecraft.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
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

    protected final String commandLiteral;
    protected final String permissionNode;
    protected final String description;
    protected final Collection<String> aliasList;

    protected AbstractCraftCommand(String commandLiteral, String permissionNode, String description, Collection<String> aliasList) {
        this.commandLiteral = commandLiteral;
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

    protected boolean specialArgsPredicate(CommandSourceStack sourceStack) {
        return sourceStack.getSender().hasPermission(this.permissionNode + ".selector-arguments");
    }

    public void register(final Commands commands) {
        ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> literal = processCommandPath(
                Commands.literal(this.commandLiteral)
                        .requires(this::requiresCheck),
                this::getCraftByExecutor
        );
        literal = this.attachCraftSelectorTree(literal);

        // Append our additional logic => fallback logic, uses the executor's craft
        //literal = literal.then(processRest(literal, this::getCraftByExecutor));

        // Dirty, but works \o/
        CommandNode<CommandSourceStack> node = literal.build();
        commands.register(
                (LiteralCommandNode<CommandSourceStack>) node,
                this.description,
                this.aliasList
        );
    }

    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> attachCraftSelectorTree(final ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> literal) {
        return literal
                // By Pilot entity
                .then(
                        this.processCommandPath(
                                Commands.literal("--pilot")
                                        .requires(this::specialArgsPredicate),
                                this::getCraftByPilot,
                                Commands.argument("pilot", ArgumentTypes.entity())
                        )
                )
                // By Craft UUID
                .then(
                        this.processCommandPath(
                                Commands.literal("--uuid")
                                        .requires(this::specialArgsPredicate),
                                this::getByCraftUUID,
                                Commands.argument("uuid", new CraftUUIDArgumentType())
                        )
                )
                // By Craft name
                .then(
                        this.processCommandPath(
                                Commands.literal("--name")
                                        .requires(this::specialArgsPredicate),
                                this::getCraftByName,
                                Commands.argument("name", StringArgumentType.string())
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

                                                        if (nameStr.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
                                                            builder.suggest(nameStr);
                                                        }
                                                    }
                                                    return builder.buildFuture();
                                                }
                                        )
                        )
                )
                // By position
                .then(
                        this.processCommandPath(
                                Commands.literal("--position")
                                        .requires(this::specialArgsPredicate),
                                this::getCraftByPosition,
                                Commands.argument("position", ArgumentTypes.blockPosition())
                        )
                );
    }

    // Attention: NEVER implement any EXECUTES functions in there! Whatever is returned will later have a "executes" attached to them
    protected abstract @Nullable ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] arguments();
    protected abstract int processCommand(final CommandContext<CommandSourceStack> context, final Set<Craft> craft);

    // Go up the chain of arguments and adjust the command to run to the innermost node
    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> constructArgumentChain(final ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] arguments, final Command<CommandSourceStack> command) {
        ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> chain = null;

        for(int i = arguments.length - 1; i >= 0; i--) {
            if (chain == null) {
                chain = arguments[i];
                chain = chain.executes(command);
            } else {
                chain = arguments[i].then(chain);
            }
        }

        return chain;
    }

    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> processCommandPath(final ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> literal, final Function<CommandContext<CommandSourceStack>, Set<Craft>> craftSupplier, final ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>... pathArguments) {
        ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] addArguments = this.arguments();

        int chainLength = 0;
        if (pathArguments != null) {
            chainLength += pathArguments.length;
        }
        if (addArguments != null) {
            chainLength += addArguments.length;
        }
        ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>>[] argumentChain = new ArgumentBuilder[chainLength];
        if (chainLength > 0) {
            for (int i = 0; i < chainLength; i++) {
                if (pathArguments != null && i < pathArguments.length) {
                    argumentChain[i] = pathArguments[i];
                } else {
                    argumentChain[i] = addArguments[i - (pathArguments == null ? 0 : pathArguments.length)];
                }
            }
        }

        ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> result;
        if (chainLength == 0) {
            result = literal.executes(context -> {
                Set<Craft> crafts = craftSupplier.apply(context);
                if (crafts == null) {
                    crafts = Set.of();
                }
                return processCommand(context, crafts);
            });
        } else {
            result = literal.then(
                    this.constructArgumentChain(argumentChain, context -> {
                                Set<Craft> crafts = craftSupplier.apply(context);
                                if (crafts == null) {
                                    crafts = Set.of();
                                }
                                return processCommand(context, crafts);
                            }
                    )
            );
        }

        return result;
    }

    protected Set<Craft> getCraftByExecutor(CommandContext<CommandSourceStack> context) {
        final CommandSourceStack css = context.getSource();
        if (css.getExecutor() == null) {
            return null;
        }
        final Craft obj = CraftManager.getInstance().getCraftByEntity(css.getExecutor());
        if (obj == null) {
            return null;
        } else {
            return Set.of(obj);
        }
    }

    protected Set<Craft> getCraftByPilot(CommandContext<CommandSourceStack> context) {
        try {
            final EntitySelectorArgumentResolver entitySelectorArgumentResolver = context.getArgument("pilot", EntitySelectorArgumentResolver.class);
            try {
                final List<Entity> entities = entitySelectorArgumentResolver.resolve(context.getSource());

                if (entities.isEmpty()) {
                    return null;
                }

                Entity entityToUse = entities.getFirst();
                Craft obj = CraftManager.getInstance().getCraftByEntity(entityToUse);
                if (obj != null) {
                    return Set.of(obj);
                } else {
                    return null;
                }
            } catch(CommandSyntaxException cse) {
                context.getSource().getSender().sendMessage(cse.getMessage());
                return null;
            }
        } catch(IllegalArgumentException illegalArgumentException) {
            // Ignore, this happens when the argument is not present!
            return null;
        }
    }
    protected Set<Craft> getByCraftUUID(CommandContext<CommandSourceStack> context) {
        UUID uuid = null;
        try {
            uuid = context.getArgument("uuid", UUID.class);
        } catch(IllegalArgumentException illegalArgumentException) {
            // Ignore, this happens when the argument is not present!
        }
        if (uuid == null) {
            return null;
        }
        final Craft obj = Craft.getCraftByUUID(uuid);
        if (obj == null) {
            return null;
        } else {
            return Set.of(obj);
        }
    }
    protected Set<Craft> getCraftByName(CommandContext<CommandSourceStack> context) {
        String name = null;
        try {
            name = context.getArgument("name", String.class);
        } catch(IllegalArgumentException illegalArgumentException) {
            // Ignore, this happens when the argument is not present!
        }
        if (name == null) {
            return null;
        }
        Set<Craft> result = new HashSet<>();
        for (Craft craft : CraftManager.getInstance().getCrafts()) {
            String craftName = craft.getNameRaw();
            if (craftName == null) {
                continue;
            }
            if (name.equalsIgnoreCase(craftName)) {
                result.add(craft);
            }
        }
        return result.isEmpty() ? null : result;
    }
    protected Set<Craft> getCraftByPosition(CommandContext<CommandSourceStack> context) {
        try {
            final World world = context.getSource().getLocation().getWorld();
            final BlockPositionResolver blockPositionResolver = context.getArgument("position", BlockPositionResolver.class);

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
                    return new HashSet<>(craftsWithPos);
                }
            } catch(CommandSyntaxException cse) {
                context.getSource().getSender().sendMessage(cse.getMessage());
            }
        } catch(IllegalArgumentException illegalArgumentException) {
            // Ignore, this happens when the argument is not present!
        }
        return null;
    }

}
