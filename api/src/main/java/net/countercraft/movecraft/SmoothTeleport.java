package net.countercraft.movecraft;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public abstract class SmoothTeleport {
    public abstract void teleport(@NotNull Player player, final World world, final double x, final double y, final double z, final float deltaYaw, final float deltaPitch);
}
