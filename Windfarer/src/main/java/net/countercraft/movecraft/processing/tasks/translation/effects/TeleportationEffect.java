package net.countercraft.movecraft.processing.tasks.translation.effects;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.config.Settings;
import net.countercraft.movecraft.craft.Craft;
import net.countercraft.movecraft.craft.SinkingCraft;
import net.countercraft.movecraft.craft.type.PropertyKeys;
import net.countercraft.movecraft.events.CraftTeleportEntityEvent;
import net.countercraft.movecraft.mapUpdater.update.EntityUpdateCommand;
import net.countercraft.movecraft.processing.effects.Effect;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class TeleportationEffect implements Effect {
    private final @NotNull Craft craft;
    private final @NotNull MovecraftLocation translation;
    private final @NotNull World world;

    public TeleportationEffect(@NotNull Craft craft, @NotNull MovecraftLocation translation, @NotNull World world) {
        this.craft = craft;
        this.translation = translation;
        this.world = world;
    }

    @Override
    public void run() {
        if (!craft.getCraftProperties().get(PropertyKeys.CAN_MOVE_ENTITIES) || craft instanceof SinkingCraft
                && craft.getCraftProperties().get(PropertyKeys.ONLY_MOVE_PLAYERS))
            return;

        Location midpoint = craft.getHitBox().getMidPoint().toBukkit(craft.getWorld());
        Set<Entity> entitiesToTeleport = new HashSet<>();
        for (Entity entity : craft.getWorld().getNearbyEntities(midpoint,
                craft.getHitBox().getXLength() / 2.0 + 1,
                craft.getHitBox().getYLength() / 2.0 + 2,
                craft.getHitBox().getZLength() / 2.0 + 1)) {
            if ((entity.getType() == EntityType.PLAYER && !(craft instanceof SinkingCraft))) {
                CraftTeleportEntityEvent e = new CraftTeleportEntityEvent(craft, entity);
                Bukkit.getServer().getPluginManager().callEvent(e);
                if (e.isCancelled())
                    continue;

                entitiesToTeleport.add(entity);
            }
            else if (!craft.getCraftProperties().get(PropertyKeys.ONLY_MOVE_PLAYERS)
                    || Settings.alwaysMovedEntities.contains(entity.getType().getKey())) {
                CraftTeleportEntityEvent e = new CraftTeleportEntityEvent(craft, entity);
                Bukkit.getServer().getPluginManager().callEvent(e);
                if (e.isCancelled())
                    continue;

                entitiesToTeleport.add(entity);
            }
        }

        if (entitiesToTeleport.isEmpty())
            return;

        // Remove all passengers from the list
        Set<Entity> cleanedList = new HashSet<>(entitiesToTeleport);
        for (Entity entity : entitiesToTeleport) {
            if (entity.getPassengers() != null && !entity.getPassengers().isEmpty()) {
                cleanedList.removeAll(entity.getPassengers());
            }
        }

        if (cleanedList.isEmpty())
            return;

        for (Entity entity : cleanedList) {
            EntityUpdateCommand entityUpdateCommand = new EntityUpdateCommand(entity, translation.getX(), translation.getY(), translation.getZ(), 0, 0, world);
            entityUpdateCommand.doUpdate();
        }
    }
}
