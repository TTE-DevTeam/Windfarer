package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class NPCCraft extends BaseCraft {

    protected long pointOfAutoRelease;
    protected boolean autoRelease;

    public NPCCraft(@NotNull TypeSafeCraftType type, @NotNull World world, final boolean doAutoRelease, final long lifetime, final Component name) {
        this(type, world, doAutoRelease, lifetime);
        if (name != null) {
            this.setName(name);
        }
    }
    public NPCCraft(@NotNull TypeSafeCraftType type, @NotNull World world, final boolean doAutoRelease, final long lifetime) {
        super(type, world);

        this.pointOfAutoRelease = System.currentTimeMillis() + lifetime;
        this.autoRelease = doAutoRelease;
    }

    @Override
    public boolean shouldAutoRelease(long autoReleaseTimeout, long maxTimeBetweenCruiseUpdates) {
        if (this.autoRelease) {
            return this.pointOfAutoRelease < System.currentTimeMillis();
        } else {
            return false;
        }
    }

    @Override
    public void setName(@NotNull Component name) {
        if (this.getName().equals(Component.empty())) {
            super.setName(name);
        }
    }

}
