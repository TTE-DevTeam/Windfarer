package net.countercraft.movecraft.commands;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedArgument;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

public interface IBrigadierCommandHelper {

    static final Field ARGUMENT_FIELD = loadField();

    public static Field loadField() {
        try {
            Field result = CommandContext.class.getDeclaredField("arguments");
            result.setAccessible(true);
            return result;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    static Optional<Map<String, ParsedArgument>> arguments(final CommandContext<CommandSourceStack> context) {
        Map<String, ParsedArgument> arguments;
        try {
            arguments = (Map<String, ParsedArgument>) ARGUMENT_FIELD.get(context);
            return Optional.ofNullable(arguments);
        } catch(Exception ex) {
            ex.printStackTrace();
            return Optional.empty();
        }
    }

    static <T> T tryGetArgument(final String argumentName, final Class<T> typeClazz, final CommandContext<CommandSourceStack> commandContext, T fallback) {
        Optional<Map<String, ParsedArgument>> optArgumentMap = arguments(commandContext);
        if (optArgumentMap.isPresent()) {
            Map<String, ParsedArgument> argumentMap = optArgumentMap.get();
            final ParsedArgument argument = argumentMap.getOrDefault(argumentName, null);
            if (argument != null) {
                return commandContext.getArgument(argumentName, typeClazz);
            } else {
                return fallback;
            }
        } else {
            return fallback;
        }
    }

}
