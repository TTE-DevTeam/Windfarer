package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class NPCCraft extends NullCraft implements PilotedCraft {

    private WeakReference<Entity> pilot;
    private final boolean pilotIsPlayer;
    private final UUID pilotUUID;

    public NPCCraft(@NotNull TypeSafeCraftType type, @NotNull World world, final boolean doAutoRelease, final long lifetime, final Component name, final Entity pilot) {
        this(type, world, doAutoRelease, lifetime, pilot);
        if (name != null) {
            this.setName(name);
        }
    }
    public NPCCraft(@NotNull TypeSafeCraftType type, @NotNull World world, final boolean doAutoRelease, final long lifetime, final Entity pilot) {
        super(type, world, doAutoRelease, lifetime);
        this.pilotUUID = UUID.fromString(pilot.getUniqueId().toString());
        this.pilotIsPlayer = pilot instanceof Player;
        this.pilot = new WeakReference<>(pilot);
    }

    @Override
    public @Nullable Entity getPilotEntity() {
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
    public @NotNull UUID getPilotUUID() {
        return this.pilotUUID;
    }

    @Override
    public Component getDetectedMessage(boolean isNew, Craft detectingCraft) {
        // If null is returned, the standard detect message is generated
        return null;
    }

    @Override
    public boolean contactPickedUpBy(Craft other) {
        return true;
    }

    @Override
    public void setName(@NotNull Component name) {
        if (this.getName().equals(Component.empty())) {
            super.setName(name);
        }
    }

}
