package net.countercraft.movecraft.util;

import net.countercraft.movecraft.SmoothTeleport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BukkitTeleport extends SmoothTeleport {
    @Override
    public void teleport(@NotNull Player player, final World world, final double x, final double y, final double z, final float deltaYaw, final float deltaPitch) {
        player.teleport(new Location(world, x, y, z, player.getYaw() + deltaYaw, player.getPitch() + deltaPitch));
    }
}
