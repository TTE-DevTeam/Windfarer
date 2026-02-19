package net.countercraft.movecraft.commands.argument.type;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.CraftManager;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.SubCraftImpl;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CraftUUIDArgumentType implements CustomArgumentType.Converted<UUID, UUID> {

    @Override
    public ArgumentType<UUID> getNativeType() {
        return ArgumentTypes.uuid();
    }

    @Override
    public UUID convert(UUID uuid) throws CommandSyntaxException {
        return uuid;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        for (Craft craft : CraftManager.getInstance().getCrafts()) {
            if (craft instanceof SinkingCraft)
                continue;
            // Squadrons uses Subcraft implementations
            if (craft instanceof SubCraftImpl)
                continue;
            builder.suggest(craft.getUUID().toString());
        }
        return builder.buildFuture();
    }
}
