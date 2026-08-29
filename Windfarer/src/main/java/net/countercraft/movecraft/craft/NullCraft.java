package net.countercraft.movecraft.craft;

import net.countercraft.movecraft.MovecraftLocation;
import net.countercraft.movecraft.craft.type.TypeSafeCraftType;
import net.countercraft.movecraft.features.contacts.ContactProvider;
import net.countercraft.movecraft.processing.MovecraftWorld;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

public class NullCraft extends BaseCraft implements ContactProvider {

    protected long pointOfAutoRelease;
    protected boolean autoRelease;

    public NullCraft(@NotNull TypeSafeCraftType type, @NotNull World world, final boolean doAutoRelease, final long lifetime) {
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
    public Component getDetectedMessage(boolean isNew, Craft detectingCraft) {
        return Component.empty();
    }

    @Override
    public boolean contactPickedUpBy(Craft other) {
        return false;
    }

    @Override
    public MovecraftLocation getContactLocation() {
        return this.getCraftOrigin();
    }

    @Override
    public double getDetectionMultiplier(boolean overWaterLine, MovecraftWorld world) {
        return 1;
    }
}
