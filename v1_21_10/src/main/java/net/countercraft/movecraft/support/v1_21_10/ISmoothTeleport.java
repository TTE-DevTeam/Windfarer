package net.countercraft.movecraft.support.v1_21_10;

import io.papermc.paper.entity.TeleportFlag;
import net.countercraft.movecraft.SmoothTeleport;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ISmoothTeleport extends SmoothTeleport {
    public void teleport(@NotNull Player player, final World world, final double x, final double y, final double z, final float deltaYaw, final float deltaPitch) {
        final Location location = new Location(world, x, y, z);
        location.setYaw(player.getYaw() + deltaYaw);
        location.setPitch(player.getPitch() + deltaPitch);

        player.teleport(
                location,
                TeleportFlag.Relative.VELOCITY_X,//x
                TeleportFlag.Relative.VELOCITY_Y,//y
                TeleportFlag.Relative.VELOCITY_Z,//z
                //TeleportFlag.Relative.VELOCITY_ROTATION, // Adds snapping apparently
                TeleportFlag.EntityState.RETAIN_OPEN_INVENTORY,
                TeleportFlag.EntityState.RETAIN_VEHICLE,
                TeleportFlag.EntityState.RETAIN_PASSENGERS
        );
    }
}
