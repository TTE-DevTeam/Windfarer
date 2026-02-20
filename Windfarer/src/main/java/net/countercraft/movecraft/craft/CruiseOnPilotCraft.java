package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.kyori.adventure.audience.Audience;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class CruiseOnPilotCraft extends BaseCraft implements PilotedCraft {
    private WeakReference<Entity> pilot;
    private final UUID pilotUUID;
    private final boolean pilotIsPlayer;

    public CruiseOnPilotCraft(@NotNull TypeSafeCraftType type, @NotNull World world, @NotNull Entity pilot) {
        super(type, world);
        this.pilot = new WeakReference<>(pilot);
        // Copy UUID just to be safe
        this.pilotUUID = UUID.fromString(pilot.getUniqueId().toString());
        this.pilotIsPlayer = pilot instanceof Player;
        this.setAudience(Audience.empty());
    }

    @Nullable
    @Override
    public Entity getPilotEntity() {
        // Do not re-set the audience here! Crafts like this are like torpedoes, we dont need to receive messages from it
        if (this.pilot.get() == null) {
            if (this.pilotIsPlayer) {
                this.pilot = new WeakReference<> (Bukkit.getPlayer(this.getPilotUUID()));
            } else {
                this.pilot = new WeakReference<>(Bukkit.getEntity(this.getPilotUUID()));
            }
        } else {
            if (this.pilot.get() instanceof Player pilotPlayer) {
                Player bukkitPilot = Bukkit.getPlayer(this.getPilotUUID());
                if (!pilotPlayer.isOnline() || (this.pilot.get() != bukkitPilot)) {
                    this.pilot = new WeakReference<> (bukkitPilot);
                }
            }
        }
        return this.pilot.get();
    }

    @Override
    public @NotNull Audience getAudience() {
        return Audience.empty();
    }

    @Override
    public @NotNull UUID getPilotUUID() {
        return this.pilotUUID;
    }
}
