package net.countercraft.movecraft.craft;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public interface PilotedCraft extends Craft {

    @Nullable
    // TODO: Change to entity
    Player getPilot();

    @NotNull
    UUID getPilotUUID();
}
