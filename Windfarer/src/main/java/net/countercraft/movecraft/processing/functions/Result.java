package net.countercraft.movecraft.processing.functions;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;

public final class Result {
    private static final Result SUCCESS = new Result(true);
    private static final Result FAILURE = new Result(false);

    @NotNull
    public static Result of(boolean success) {
        return success ? SUCCESS : FAILURE;
    }

    @NotNull
    public static Result succeed() {
        return SUCCESS;
    }

    @NotNull
    @Deprecated(forRemoval = true)
    public static Result succeedWithMessage(@NotNull String message) {
        return new Result(true, Component.text(message));
    }

    @NotNull
    @Deprecated(forRemoval = true)
    public static Result succeedWithMessage(@NotNull Component message) {
        return new Result(true, message);
    }

    @NotNull
    public static Result fail() {
        return FAILURE;
    }

    @NotNull
    @Deprecated(forRemoval = true)
    public static Result failWithMessage(@NotNull String message) {
        return new Result(false, Component.text(message));
    }

    @NotNull
    public static Result failWithMessage(@NotNull Component message) {
        return new Result(false, message);
    }

    private final boolean success;
    @NotNull
    private final Component message;

    private Result(boolean success) {
        this.success = success;
        message = Component.text("No result message provided! This is a bug and should be reported.");
    }

    private Result(boolean success, @NotNull Component message) {
        this.success = success;
        this.message = message;
    }

    public Component getMessageComponent() {
        return this.message;
    }
    public String getMessage() {
        return PlainTextComponentSerializer.plainText().serialize(this.message);
    }

    public boolean isSucess() {
        return success;
    }
}
