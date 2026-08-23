package net.countercraft.movecraft.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.localisation.I18nSupport;
import net.countercraft.movecraft.util.ComponentPaginator;
import net.countercraft.movecraft.util.hitboxes.HitBox;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

public class CraftInfoCommand extends AbstractCraftCommand {
    private static final List<Function<Craft, ? extends Iterable<Component>>> componentProviders = new ArrayList<>();
    static {
        final Function<String, Component> createTitle = (heading) -> {
            return Component.text(heading).style(Style.style(TextDecoration.UNDERLINED).color(NamedTextColor.GRAY)).appendSpace().style(Style.empty());
        };
        final Component notApplicable = Component.text("n/a").style(Style.style().color(NamedTextColor.RED).decorate(TextDecoration.BOLD));

        registerComponentProvider(craft -> createTitle.apply("Craft Name:").append(craft.getName()));
        registerComponentProvider(craft -> {
            final HitBox hitBox = craft.getHitBox();
            return createTitle.apply("Craft midpoint:").append(hitBox.isEmpty() ? notApplicable : Component.text(hitBox.getMidPoint().toString()).clickEvent(ClickEvent.runCommand("/tp " + hitBox.getMidPoint().getX() + " " + hitBox.getMidPoint().getY() + " " + hitBox.getMidPoint().getZ())));
        });
        registerComponentProvider(craft -> {
            final HitBox hitBox = craft.getHitBox();
            final MovecraftLocation point = hitBox.isEmpty() ? MovecraftLocation.zero() : new MovecraftLocation(hitBox.getMinX(), hitBox.getMinY(), hitBox.getMinZ());
            return createTitle.apply("Craft min bound:").append(hitBox.isEmpty() ? notApplicable : Component.text(point.toString()).clickEvent(ClickEvent.runCommand("/tp " + point.getX() + " " + point.getY() + " " + point.getZ())));
        });
        registerComponentProvider(craft -> {
            final HitBox hitBox = craft.getHitBox();
            final MovecraftLocation point = hitBox.isEmpty() ? MovecraftLocation.zero() : new MovecraftLocation(hitBox.getMaxX(), hitBox.getMaxY(), hitBox.getMaxZ());
            return createTitle.apply("Craft max bound:").append(hitBox.isEmpty() ? notApplicable : Component.text(point.toString()).clickEvent(ClickEvent.runCommand("/tp " + point.getX() + " " + point.getY() + " " + point.getZ())));
        });
        registerComponentProvider(craft -> createTitle.apply("Craft world:").append(Component.text(craft.getWorld().getName())));
        registerComponentProvider(craft -> createTitle.apply("Craft type:").append(Component.text(craft.getCraftProperties().getName()).clickEvent(ClickEvent.runCommand("/crafttype " + craft.getCraftProperties().getName()))));
        registerComponentProvider(craft -> createTitle.apply("Craft size:").append(craft.getHitBox().isEmpty() ? notApplicable : Component.text(craft.getHitBox().size())));
        registerComponentProvider(craft -> createTitle.apply("Is cruising:").append(Component.text(craft.getCruising())));
        registerComponentProvider(craft -> createTitle.apply("Cruise direction:").append(craft.getCruising() ? Component.text(craft.getCruiseDirection().toString()) : notApplicable));
        registerComponentProvider(craft -> createTitle.apply("Cruise speed:").append(Component.text(craft.getSpeed())));
        registerComponentProvider(craft -> createTitle.apply("Mean cruise time:").append(Component.text(craft.getMeanCruiseTime())));
        registerComponentProvider(craft -> createTitle.apply("Is disabled:").append(Component.text(craft.getDisabled())));
        registerComponentProvider(craft -> createTitle.apply("Current gear:").append(Component.text(craft.getCurrentGear())));
    }

    public CraftInfoCommand() {
        super("craftinfo", "movecraft.commands.craftinfo", "Get information on a piloted craft", List.of());
    }

    @Deprecated(forRemoval = true)
    public static void registerMultiProvider(@NotNull Function<Craft, ? extends Iterable<String>> provider){
        return;
    }

    @Deprecated(forRemoval = true)
    public static void registerProvider(@NotNull Function<Craft, String> provider){
        registerComponentProvider(craft -> Component.text(provider.apply(craft)));
    }

    public static void registerComponentProvider(@NotNull Function<Craft, Component> provider){
        componentProviders.add(provider.andThen(List::of));
    }

    @Override
    protected ArgumentBuilder<CommandSourceStack, ? extends ArgumentBuilder<CommandSourceStack, ?>> arguments() {
        return Commands.argument("page", IntegerArgumentType.integer(1));
    }

    @Override
    protected int processCommand(CommandContext<CommandSourceStack> context, Set<Craft> crafts) {
        Optional<Map<String, ParsedArgument>> optArguments = IBrigadierCommandHelper.arguments(context);
        if (optArguments.isEmpty()) {
            return -1;
        }
        final Map<String, ParsedArgument> arguments = optArguments.get();

        int page = 1;

        if (arguments.containsKey("page")) {
            page = context.getArgument("page", Integer.class);
            page = Math.abs(page);
            if (page < 0) {
                page = 1;
            }
        }

        // TODO: Properly list all the other arguments as well!
        ComponentPaginator paginator = new ComponentPaginator(I18nSupport.getInternationalisedComponent("Craft Info"), (pageNumber) -> "/craftinfo " + pageNumber);
        for (Craft craft : crafts) {
            for(var provider : componentProviders){
                for(var line : provider.apply(craft)){
                    if (line == null)
                        continue;
                    paginator.addLine(line);
                }
            }
            if (crafts.size() > 1) {
                // Add pages so we have a clear delimiter between each craft
                int pageCountNow = paginator.getPageCount();
                while (paginator.getPageCount() == pageCountNow) {
                    paginator.addLine(Component.empty());
                }
            }
        }
        if(!paginator.isInBounds(page)){
            page = paginator.getPageCount();
        }
        if (!paginator.isEmpty()) {
            for(Component line : paginator.getPage(page)) {
                context.getSource().getSender().sendMessage(line);
            }
        } else {
            // TODO: Logging
        }

        return 0;
    }

}
