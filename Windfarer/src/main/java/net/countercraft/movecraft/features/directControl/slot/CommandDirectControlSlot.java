package net.countercraft.movecraft.features.directControl.slot;

import net.countercraft.movecraft.CruiseDirection;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.PilotedCraft;
import net.countercraft.movecraft.craft.controller.directControl.AbstractDirectControlSlot;
import net.countercraft.movecraft.util.SerializationUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class CommandDirectControlSlot extends AbstractDirectControlSlot {

    private Optional<SlotCommand> cmdLeftClick;
    private Optional<SlotCommand> cmdRightClick;
    private Optional<SlotCommand> cmdDropItem;
    private Optional<SlotCommand> cmdSwapHand;

    public CommandDirectControlSlot(final Map<String, Object> yamlData) {
        this.cmdLeftClick = Optional.ofNullable(SlotCommand.parse(yamlData.getOrDefault("on_left_click", null)));
        this.cmdRightClick = Optional.ofNullable(SlotCommand.parse(yamlData.getOrDefault("on_right_click", null)));
        this.cmdDropItem = Optional.ofNullable(SlotCommand.parse(yamlData.getOrDefault("on_drop_item", null)));
        this.cmdSwapHand = Optional.ofNullable(SlotCommand.parse(yamlData.getOrDefault("on_swap_hand", null)));
    }

    public CommandDirectControlSlot(CommandDirectControlSlot toCopy) {
        super();

        this.cmdLeftClick = Optional.ofNullable(SlotCommand.copy(toCopy.cmdLeftClick.get()));
        this.cmdRightClick = Optional.ofNullable(SlotCommand.copy(toCopy.cmdRightClick.get()));
        this.cmdDropItem = Optional.ofNullable(SlotCommand.copy(toCopy.cmdDropItem.get()));
        this.cmdSwapHand = Optional.ofNullable(SlotCommand.copy(toCopy.cmdSwapHand.get()));
    }

    protected static boolean runCommand(final Optional<SlotCommand> commandOptional, Player interactor, Craft craft) {
        if (commandOptional.isPresent() && interactor != null && craft != null) {
            final SlotCommand cmd = commandOptional.get();
            return cmd.execute(interactor, craft);
        }
        return false;
    }

    @Override
    public boolean onLeftClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        return runCommand(this.cmdLeftClick, interactor, craft);
    }

    @Override
    public boolean onRightClick(ItemStack itemStack, Player interactor, Craft craft, Action action) {
        return runCommand(this.cmdRightClick, interactor, craft);
    }

    @Override
    public boolean onItemDrop(ItemStack itemStack, Player interactor, Craft craft) {
        return runCommand(this.cmdDropItem, interactor, craft);
    }

    @Override
    public boolean onSwapHand(ItemStack itemStackMainHand, ItemStack itemStackOffHand, Player interactor, Craft craft) {
        return runCommand(this.cmdSwapHand, interactor, craft);
    }

    // Not supported
    @Override
    public boolean onPreCruise(Player activePilot, Craft craft, int tickCooldown, Consumer<Integer> modifyTickCooldown, CruiseDirection cruiseDirection) {
        return false;
    }

    @Override
    public AbstractDirectControlSlot clone() {
        return new CommandDirectControlSlot(this);
    }

    @Override
    public @NotNull Map<String, Object> serialize() {
        Map<String, Object> result = new HashMap<>();

        this.cmdLeftClick.ifPresent(slot -> result.put("on_left_click", slot));
        this.cmdRightClick.ifPresent(slot -> result.put("on_right_click", slot));
        this.cmdDropItem.ifPresent(slot -> result.put("on_drop_item", slot));
        this.cmdSwapHand.ifPresent(slot -> result.put("on_swap_hand", slot));

        return result;
    }

    protected static record SlotCommand(
            String commandString,
            boolean runAsConsole
    ) {

        public static SlotCommand copy(SlotCommand toCopy) {
            if (toCopy == null) {
                return null;
            }
            return new SlotCommand(toCopy.commandString(), toCopy.runAsConsole());
        }

        static @Nullable SlotCommand parse(final Object object) {
            if (object == null) {
                return null;
            } else
            // Only command
            if (object instanceof String) {
                return new SlotCommand((String) object, false);
            } else
            if (object instanceof Map yamlMappingRaw) {
                String command = SerializationUtil.deserializeString("command", yamlMappingRaw, null);
                if (command == null || command.isBlank() || command.isEmpty()) {
                    return null;
                }
                boolean runAsConsole = SerializationUtil.deserializeBoolean("run_as_console", yamlMappingRaw, false);
                return new SlotCommand(command, runAsConsole);
            } else {
                return null;
            }
        }

        static Map<String, String> generateVariables(Entity executor, Craft craft) {
            return Map.of(
                    "%EXECUTOR_UUID%", executor.getUniqueId().toString(),
                    "%CRAFT_UUID%", craft.getUUID().toString(),
                    "%CRAFT_NAME%", craft.getNameRaw(),
                    "%CRAFT_PILOT_UUID%", craft instanceof PilotedCraft pc ? pc.getPilotUUID().toString() : "",
                    "%CRAFT_WORLD%", craft.getWorld().getName(),
                    "%CRAFT_CENTER_X%", craft.getHitBox().getMidPoint().getX() + "",
                    "%CRAFT_CENTER_Y%", craft.getHitBox().getMidPoint().getY() + "",
                    "%CRAFT_CENTER_Z%", craft.getHitBox().getMidPoint().getZ() + ""
            );
        }

        boolean execute(final Entity executor, final Craft craft) {
            // Step 1) Replace the placeholders
            // Step 2) Acquire sender
            // Step 3) execute command
            String preparedCommand = new String(this.commandString());
            for (Map.Entry<String, String> entry : generateVariables(executor, craft).entrySet()) {
                preparedCommand = preparedCommand.replaceAll(entry.getKey(), entry.getValue());
            }

            CommandSender sender;
            if (this.runAsConsole()) {
                sender = Bukkit.getConsoleSender();
            } else {
                sender = executor;
                if (executor instanceof Player player) {
                    return player.performCommand(preparedCommand);
                }
            }

            return Bukkit.dispatchCommand(sender, preparedCommand);
        }

    }
}
