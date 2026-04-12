package net.countercraft.movecraft.support.v1_21_11;

import net.countercraft.movecraft.SmoothTeleport;
import net.minecraft.server.commands.TeleportCommand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class ISmoothTeleport extends SmoothTeleport {
    static final Set<Relative> relatives = Set.of(
            Relative.DELTA_X,
            Relative.DELTA_Y,
            Relative.DELTA_Z,
            Relative.ROTATE_DELTA,
            Relative.X_ROT,
            Relative.Y_ROT
    );

    public void teleport(@NotNull Player player, final World world, final double x, final double y, final double z, final float deltaYaw, final float deltaPitch) {
        ServerPlayer target = ((CraftPlayer)player).getHandle();

        float xRot = target.getXRot() + deltaPitch;
        float yRot = target.getYRot() + deltaYaw;

        double deltaX = relatives.contains(Relative.X) ? x - target.getX() : x;
        double deltaY = relatives.contains(Relative.Y) ? y - target.getY() : y;
        double deltaZ = relatives.contains(Relative.Z) ? z - target.getZ() : z;
        float deltaRotY = relatives.contains(Relative.Y_ROT) ? yRot - target.getYRot() : yRot;
        float deltaRotX = relatives.contains(Relative.X_ROT) ? xRot - target.getXRot() : xRot;
        float yawWrapped = Mth.wrapDegrees(deltaRotY);
        float pitchWrapped = Mth.wrapDegrees(deltaRotX);
        boolean result = false;

        if (target != null) {
            result = target.teleportTo(((CraftWorld)world).getHandle(), deltaX, deltaY, deltaZ, relatives, yawWrapped, pitchWrapped, true, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }

        if (result) {
            // TODO: Add option to provide a Optional lookAt thing
//            if (lookAt != null) {
//                lookAt.perform(source, target);
//            }

            label50: {
                if (target instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity)target;
                    if (livingEntity.isFallFlying()) {
                        break label50;
                    }
                }

                target.setDeltaMovement(target.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                target.setOnGround(true);
            }

//            if (target instanceof PathfinderMob) {
//                PathfinderMob pathfinderMob = (PathfinderMob)target;
//                pathfinderMob.getNavigation().stop();
//            }
        }
    }
}
