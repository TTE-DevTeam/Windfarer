package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.kyori.adventure.audience.Audience;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class CruiseOnPilotCraft extends PilotedCraftImpl {

    public CruiseOnPilotCraft(@NotNull TypeSafeCraftType type, @NotNull World world, @NotNull Entity pilot) {
        super(type, world, pilot);
        this.setAudience(Audience.empty());
    }

    @Override
    public @NotNull Audience getAudience() {
        return Audience.empty();
    }

}
