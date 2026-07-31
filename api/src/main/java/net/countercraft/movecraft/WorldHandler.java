package net.countercraft.movecraft;

import net.countercraft.movecraft.craft.Craft;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.NotNull;

public abstract class WorldHandler {
    public abstract void rotateCraft(@NotNull Craft craft, @NotNull MovecraftLocation originLocation, @NotNull MovecraftRotation rotation);
    public abstract void translateCraft(@NotNull Craft craft, @NotNull MovecraftLocation newLocation, @NotNull World world);
    public abstract void setBlockFast(@NotNull Location location, @NotNull BlockData data);
    public abstract void setBlockFast(@NotNull Location location, @NotNull MovecraftRotation rotation, @NotNull BlockData data);

    public static @NotNull String[] getPackageNames(@NotNull String minecraftVersion) {
        String[] parts = minecraftVersion.split("\\.");
        String[] result = new String[parts.length];
        String workingStr = "v";
        for (int i = 0; i < parts.length; i++) {
            workingStr += parts[i];
            result[parts.length - 1 - i] = workingStr;
            workingStr += "_";
        }
        return result;
    }
}
