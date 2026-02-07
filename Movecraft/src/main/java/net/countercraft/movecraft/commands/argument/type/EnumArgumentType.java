package de.dertoaster.movecrafttteadditions.util.command.argument.type;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class EnumArgumentType<E extends Enum<E>> implements CustomArgumentType.Converted<E, String> {

    private final Class<E> enumInner;

    private EnumArgumentType(Class<E> enumInner) {
        this.enumInner = enumInner;
    }

    public static <T extends Enum<T>> EnumArgumentType<T> ofEnum(final Class<T> enumClass) {
        return new EnumArgumentType<T>(enumClass);
    }

    @Override
    public ArgumentType<String> getNativeType() {
        return StringArgumentType.word();
    }

    @Override
    public E convert(String s) throws CommandSyntaxException {
        return E.valueOf(this.enumInner, s.toUpperCase());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        EnumSet.allOf(enumInner).forEach(entry -> builder.suggest(entry.name()));
        return builder.buildFuture();
    }

    @Override
    public Collection<String> getExamples() {
        List<String> examples = new ArrayList<>();
        EnumSet.allOf(enumInner).forEach(entry -> examples.add(entry.name()));
        return examples;
    }
}
