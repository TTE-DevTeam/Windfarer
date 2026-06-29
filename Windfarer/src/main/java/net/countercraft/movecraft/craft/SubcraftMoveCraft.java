package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class SubcraftMoveCraft extends PilotedCraftImpl {

    public SubcraftMoveCraft(@NotNull TypeSafeCraftType type, @NotNull World world, @NotNull Entity pilot) {
        super(type, world, pilot);
    }

}
